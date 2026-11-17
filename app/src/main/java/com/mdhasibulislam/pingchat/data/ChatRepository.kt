package com.mdhasibulislam.pingchat.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.StorageMetadata
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Chats list, messages, read ticks, typing, reactions, deletes — on
 * Firestore (v2.0.0).
 *
 * Layout:
 * - conversations/{pairId}: participants [uid, uid], lastMessage,
 *   lastMessageAt (millis), lastMessageKind, lastReadAt {uid: millis}
 * - conversations/{pairId}/messages/{msgId}: senderId, body, kind
 *   (text/voice/photo), mediaUrl, mediaPath, viewOnce, consumedAt,
 *   replyToId, deletedBy [uid], reactions {uid: emoji}, createdAtMillis,
 *   participants [uid, uid] (denormalized: the Firestore rules cannot
 *   use get() to check the parent conversation during a list query —
 *   "rules are not filters" — so every message carries its participants
 *   and every messages query filters whereArrayContains("participants", me))
 * - conversations/{pairId}/typing/{uid}: userId, name, updatedAt,
 *   participants [uid, uid] (same reason as messages)
 *
 * Timestamps are epoch millis in Firestore; the models keep ISO strings,
 * so TimeFormat / tick logic works unchanged. Conversations are sorted
 * client-side, and so are messages: every query uses a single field
 * (whereArrayContains) with NO orderBy, so NO composite index is needed
 * at all — sorting runs client-side after the fetch. The chat-list
 * preview comes from the denormalized lastMessage* fields on the
 * conversation doc itself (see updateConversationPreview).
 */
class ChatRepository {
    private val db get() = FirebaseDb.db
    private val storage get() = FirebaseDb.storage

    fun myId(): String? = FirebaseDb.auth.currentUser?.uid

    private fun conversationsRef() = db.collection("conversations")
    private fun messagesRef(conversationId: String) =
        conversationsRef().document(conversationId).collection("messages")

    /**
     * The conversation's participants, cached per conversation. Messages
     * and typing docs carry a denormalized `participants` array (the
     * Firestore rules cannot use get() on the parent conversation during
     * a list query, so membership must live on each document itself).
     */
    private val participantsCache = mutableMapOf<String, List<String>>()

    private suspend fun participantsFor(conversationId: String): List<String> {
        participantsCache[conversationId]?.let { return it }
        return try {
            val snap = conversationsRef().document(conversationId).get().await()
            @Suppress("UNCHECKED_CAST")
            val list = (snap.data?.get("participants") as? List<*>)
                ?.mapNotNull { it as? String } ?: emptyList()
            if (list.isNotEmpty()) participantsCache[conversationId] = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Load all 1-to-1 conversations for the signed-in user, newest first. */
    suspend fun loadConversations(): List<ConversationItem> {
        val me = myId() ?: return emptyList()
        val snap = conversationsRef()
            .whereArrayContains("participants", me)
            .get()
            .await()
        val convs = snap.documents
            .map { conversationFromDoc(it.id, it.data ?: emptyMap()) to (it.data ?: emptyMap()) }
            .sortedByDescending { (it.second["lastMessageAt"] as? Long) ?: 0L }
        if (convs.isEmpty()) return emptyList()

        return convs.mapNotNull { (conv, data) ->
            @Suppress("UNCHECKED_CAST")
            val participants = (data["participants"] as? List<*>)?.mapNotNull { it as? String }
                ?: return@mapNotNull null
            val peerId = participants.firstOrNull { it != me } ?: return@mapNotNull null
            val peer = getProfile(peerId) ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val lastRead = data["lastReadAt"] as? Map<String, Any?>
            val myReadMillis = (lastRead?.get(me) as? Long)
            val peerReadMillis = (lastRead?.get(peerId) as? Long)
            // The last-message preview comes from the denormalized fields
            // on the conversation doc itself — no messages query, so no
            // composite index is needed. (Conversations written before
            // v2.0.3 have no lastMessageId yet; their preview appears
            // after the next message is sent.)
            val lastMessageId = data["lastMessageId"] as? String
            val last = if (lastMessageId.isNullOrBlank()) {
                null
            } else {
                ChatMessage(
                    id = lastMessageId,
                    conversationId = conv.id,
                    senderId = data["lastSenderId"] as? String ?: "",
                    body = data["lastMessage"] as? String ?: "",
                    kind = data["lastMessageKind"] as? String ?: "text",
                    createdAt = millisToIsoOrEmpty(data["lastMessageAt"] as? Long)
                )
            }
            val unread = unreadCount(conv.id, myReadMillis)
            ConversationItem(
                conv, peer, last,
                millisToIso(myReadMillis), millisToIso(peerReadMillis), unread
            )
        }
    }

    private suspend fun getProfile(userId: String): Profile? {
        return try {
            val snap = db.collection("users").document(userId).get().await()
            if (!snap.exists()) null else profileFromDoc(userId, snap.data ?: emptyMap())
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun unreadCount(conversationId: String, myLastReadAtMillis: Long?): Int {
        val me = myId() ?: return 0
        return try {
            // Single-field filter only (no orderBy/range) so no composite
            // index is needed; the time filter runs client-side.
            val snap = messagesRef(conversationId)
                .whereArrayContains("participants", me)
                .get()
                .await()
            snap.documents.count { doc ->
                val senderId = doc.getString("senderId")
                val createdAt = doc.getLong("createdAtMillis") ?: 0L
                val deletedBy = (doc.get("deletedBy") as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList()
                senderId != null && senderId != me && !deletedBy.contains(me) &&
                    (myLastReadAtMillis == null || createdAt > myLastReadAtMillis)
            }
        } catch (e: Exception) {
            0
        }
    }

    /** Latest messages of a conversation, oldest first. */
    suspend fun loadMessages(conversationId: String, limit: Int = 200): List<ChatMessage> {
        val me = myId() ?: return emptyList()
        return try {
            // Single-field filter only (no orderBy) so no composite index
            // is needed; sorting runs client-side.
            messagesRef(conversationId)
                .whereArrayContains("participants", me)
                .get()
                .await()
                .documents
                .map { doc ->
                    (doc.getLong("createdAtMillis") ?: 0L) to
                        chatMessageFromDoc(conversationId, doc.id, doc.data ?: emptyMap())
                }
                .sortedBy { it.first }
                .takeLast(limit)
                .map { it.second }
                .filter { !it.deletedBy.contains(me) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** The other participant's profile + their last_read_at for one conversation. */
    suspend fun loadPeer(conversationId: String): Pair<Profile?, String?> {
        val me = myId() ?: return null to null
        return try {
            val snap = conversationsRef().document(conversationId).get().await()
            if (!snap.exists()) return null to null
            val data = snap.data ?: return null to null
            @Suppress("UNCHECKED_CAST")
            val participants = (data["participants"] as? List<*>)
                ?.mapNotNull { it as? String } ?: return null to null
            val otherId = participants.firstOrNull { it != me } ?: return null to null
            val profile = getProfile(otherId)
            @Suppress("UNCHECKED_CAST")
            val lastRead = data["lastReadAt"] as? Map<String, Any?>
            val peerReadMillis = lastRead?.get(otherId) as? Long
            profile to millisToIso(peerReadMillis)
        } catch (e: Exception) {
            null to null
        }
    }

    /** Reactions for the given messages, grouped by message id. */
    suspend fun loadReactions(
        conversationId: String,
        messageIds: List<String>
    ): Map<String, List<Reaction>> {
        if (messageIds.isEmpty()) return emptyMap()
        val me = myId() ?: return emptyMap()
        return try {
            // Reactions live on the message docs themselves; read the
            // conversation's messages once and pick out the requested ids.
            val messages = messagesRef(conversationId)
                .whereArrayContains("participants", me)
                .get()
                .await()
                .documents
                .map { chatMessageFromDoc(conversationId, it.id, it.data ?: emptyMap()) }
            messages.filter { messageIds.contains(it.id) }
                .associate { msg ->
                    msg.id to msg.reactions.map { (uid, emoji) ->
                        Reaction(messageId = msg.id, userId = uid, emoji = emoji)
                    }
                }
                .filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Send a text message. Returns the inserted row (or null if it failed). */
    suspend fun sendMessage(
        conversationId: String,
        body: String,
        replyToId: String? = null
    ): ChatMessage? {
        val me = myId() ?: return null
        val now = nowMillis()
        return try {
            val ref = messagesRef(conversationId).document()
            val data = mutableMapOf<String, Any?>(
                "senderId" to me,
                "body" to body,
                "kind" to "text",
                "replyToId" to replyToId,
                "mediaUrl" to null,
                "mediaPath" to null,
                "viewOnce" to false,
                "consumedAt" to null,
                "deletedBy" to emptyList<String>(),
                "reactions" to emptyMap<String, String>(),
                "participants" to participantsFor(conversationId),
                "createdAtMillis" to now
            )
            ref.set(data).await()
            updateConversationPreview(conversationId, body, "text", now, ref.id, me)
            chatMessageFromDoc(conversationId, ref.id, data)
        } catch (e: Exception) {
            null
        }
    }

    /** Mark a conversation read (only when the user is actually looking at it). */
    suspend fun markRead(conversationId: String) {
        val me = myId() ?: return
        try {
            conversationsRef().document(conversationId)
                .update("lastReadAt.$me", nowMillis())
                .await()
        } catch (e: Exception) {
            // read markers are best-effort
        }
    }

    /**
     * Send a view-once photo: upload to Firebase Storage at
     * chat-photos/<userId>/<uuid>.<ext>, then insert a kind='photo' message.
     */
    suspend fun sendPhoto(conversationId: String, bytes: ByteArray, mime: String): ChatMessage? {
        val me = myId() ?: return null
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val now = nowMillis()
        return try {
            val path = "$me/${UUID.randomUUID()}.$ext"
            val ref = storage.reference.child("chat-photos/$path")
            ref.putBytes(
                bytes,
                StorageMetadata.Builder().setContentType(mime).build()
            ).await()
            val url = ref.downloadUrl.await().toString()
            insertMediaMessage(conversationId, me, "photo", url, path, now)
        } catch (e: Exception) {
            null
        }
    }

    /** Send a view-once voice message: upload the .m4a bytes, then insert. */
    suspend fun sendVoice(conversationId: String, bytes: ByteArray): ChatMessage? {
        val me = myId() ?: return null
        val now = nowMillis()
        return try {
            val path = "$me/${UUID.randomUUID()}.m4a"
            val ref = storage.reference.child("voice-messages/$path")
            ref.putBytes(
                bytes,
                StorageMetadata.Builder().setContentType("audio/mp4").build()
            ).await()
            val url = ref.downloadUrl.await().toString()
            insertMediaMessage(conversationId, me, "voice", url, path, now)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun insertMediaMessage(
        conversationId: String,
        me: String,
        kind: String,
        url: String,
        path: String,
        now: Long
    ): ChatMessage? {
        return try {
            val ref = messagesRef(conversationId).document()
            val data = mutableMapOf<String, Any?>(
                "senderId" to me,
                "body" to "",
                "kind" to kind,
                "replyToId" to null,
                "mediaUrl" to url,
                "mediaPath" to path,
                "viewOnce" to true,
                "consumedAt" to null,
                "deletedBy" to emptyList<String>(),
                "reactions" to emptyMap<String, String>(),
                "participants" to participantsFor(conversationId),
                "createdAtMillis" to now
            )
            ref.set(data).await()
            updateConversationPreview(
                conversationId,
                if (kind == "photo") "Photo" else "Voice message",
                kind,
                now,
                ref.id,
                me
            )
            chatMessageFromDoc(conversationId, ref.id, data)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateConversationPreview(
        conversationId: String,
        preview: String,
        kind: String,
        now: Long,
        messageId: String,
        senderId: String
    ) {
        try {
            conversationsRef().document(conversationId).set(
                mapOf(
                    "lastMessage" to preview,
                    "lastMessageAt" to now,
                    "lastMessageKind" to kind,
                    "lastMessageId" to messageId,
                    "lastSenderId" to senderId
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            // preview is best-effort; the message itself is already saved
        }
    }

    /**
     * View-once consume: deletes a view-once voice/photo message, but only
     * when the caller is the recipient (the transaction refuses the sender
     * or an already-consumed message). Returns true when the row was deleted.
     */
    suspend fun consumeMessage(conversationId: String, messageId: String): Boolean {
        val me = myId() ?: return false
        val ref = messagesRef(conversationId).document(messageId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) return@runTransaction false
                val senderId = snap.getString("senderId")
                val consumedAt = snap.getLong("consumedAt")
                if (senderId == me || consumedAt != null) return@runTransaction false
                if (snap.getBoolean("viewOnce") != true) return@runTransaction false
                tx.delete(ref)
                true
            }.await()
        } catch (e: Exception) {
            false
        }
    }

    /** Best-effort removal of a consumed/deleted media file from Storage.
     * [bucket] is the top folder ("chat-photos" / "voice-messages"). */
    suspend fun deleteMedia(bucket: String, path: String) {
        try {
            storage.reference.child("$bucket/$path").delete().await()
        } catch (e: Exception) {
            // cleanup is best-effort; the row is already gone
        }
    }

    /** Find the 1-to-1 conversation with another user, or create it (friend-gated). */
    suspend fun getOrCreateConversation(otherUserId: String): String {
        val me = myId() ?: throw IllegalStateException("You are not signed in.")
        val convId = pairDocId(me, otherUserId)
        // No chat/call until the friend request is accepted (same as v1.0.5).
        val friendSnap = db.collection("friendships").document(convId).get().await()
        val status = friendSnap.getString("status")
        if (status != "accepted") {
            throw IllegalStateException("You need to be friends first.")
        }
        val ref = conversationsRef().document(convId)
        val existing = ref.get().await()
        if (!existing.exists()) {
            val now = nowMillis()
            ref.set(
                mapOf(
                    "participants" to listOf(me, otherUserId),
                    "lastMessage" to "",
                    "lastMessageAt" to 0L,
                    "lastMessageKind" to "text",
                    "lastReadAt" to emptyMap<String, Long>(),
                    "createdAt" to now
                )
            ).await()
        }
        return convId
    }

    suspend fun deleteForMe(conversationId: String, messageId: String): Boolean {
        val me = myId() ?: return false
        return try {
            messagesRef(conversationId).document(messageId)
                .update("deletedBy", FieldValue.arrayUnion(me))
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteForEveryone(conversationId: String, messageId: String) {
        try {
            messagesRef(conversationId).document(messageId).delete().await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun clearChat(conversationId: String) {
        val me = myId() ?: return
        val snap = messagesRef(conversationId)
            .whereArrayContains("participants", me)
            .get()
            .await()
        if (snap.isEmpty) return
        val batch = db.batch()
        snap.documents.forEach { doc ->
            batch.update(doc.reference, "deletedBy", FieldValue.arrayUnion(me))
        }
        batch.commit().await()
    }

    /** Toggle my reaction on a message (same emoji again removes it). */
    suspend fun toggleReaction(conversationId: String, messageId: String, emoji: String) {
        val me = myId() ?: return
        val ref = messagesRef(conversationId).document(messageId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) return@runTransaction null
                @Suppress("UNCHECKED_CAST")
                val reactions = (snap.get("reactions") as? Map<String, String>) ?: emptyMap()
                if (reactions[me] == emoji) {
                    tx.update(ref, "reactions.$me", FieldValue.delete())
                } else {
                    tx.update(ref, "reactions.$me", emoji)
                }
                null
            }.await()
        } catch (e: Exception) {
            throw e
        }
    }

    // ---------- realtime (Firestore listeners) ----------

    /** Live messages of one conversation, oldest first. */
    fun messagesFlow(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val me = myId() ?: run { close(); return@callbackFlow }
        val reg = messagesRef(conversationId)
            .whereArrayContains("participants", me)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    // No orderBy (it would need a composite index);
                    // sorting runs client-side.
                    val list = snap.documents
                        .map { doc ->
                            (doc.getLong("createdAtMillis") ?: 0L) to
                                chatMessageFromDoc(conversationId, doc.id, doc.data ?: emptyMap())
                        }
                        .sortedBy { it.first }
                        .map { it.second }
                        .filter { m -> !m.deletedBy.contains(me) }
                    trySend(list)
                }
            }
        awaitClose { reg.remove() }
    }

    /** The other participant's last_read_at (ISO string), live. */
    fun peerReadFlow(conversationId: String): Flow<String?> = callbackFlow {
        val me = myId()
        val reg = conversationsRef().document(conversationId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val participants = (snap.get("participants") as? List<*>)
                        ?.mapNotNull { it as? String } ?: emptyList()
                    val otherId = participants.firstOrNull { it != me }
                    @Suppress("UNCHECKED_CAST")
                    val lastRead = snap.get("lastReadAt") as? Map<String, Any?>
                    val millis = otherId?.let { lastRead?.get(it) as? Long }
                    trySend(millisToIso(millis))
                }
            }
        awaitClose { reg.remove() }
    }

    /** Typing events from the peer (a doc appears/updates in typing/{uid}). */
    fun typingFlow(conversationId: String): Flow<TypingPayload> = callbackFlow {
        val me = myId() ?: run { close(); return@callbackFlow }
        val reg = conversationsRef().document(conversationId)
            .collection("typing")
            .whereArrayContains("participants", me)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                        change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED
                    ) {
                        val userId = change.document.getString("userId") ?: return@forEach
                        if (userId == me) return@forEach
                        val name = change.document.getString("name") ?: ""
                        trySend(TypingPayload(userId, name))
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    /** Typing-stop events from the peer (their typing doc was removed). */
    fun typingStopFlow(conversationId: String): Flow<TypingPayload> = callbackFlow {
        val me = myId() ?: run { close(); return@callbackFlow }
        val reg = conversationsRef().document(conversationId)
            .collection("typing")
            .whereArrayContains("participants", me)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                        val userId = change.document.getString("userId") ?: return@forEach
                        if (userId == me) return@forEach
                        trySend(TypingPayload(userId))
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    /** Tell the peer I am typing (writes my typing doc). */
    suspend fun sendTyping(conversationId: String, payload: TypingPayload) {
        val me = myId() ?: return
        try {
            conversationsRef().document(conversationId)
                .collection("typing").document(me)
                .set(
                    mapOf(
                        "userId" to me,
                        "name" to payload.name,
                        "updatedAt" to nowMillis(),
                        "participants" to participantsFor(conversationId)
                    )
                ).await()
        } catch (e: Exception) {
            // typing is best-effort
        }
    }

    /** Tell the peer I stopped typing (removes my typing doc). */
    suspend fun sendTypingStop(conversationId: String, payload: TypingPayload) {
        val me = myId() ?: return
        try {
            conversationsRef().document(conversationId)
                .collection("typing").document(me)
                .delete().await()
        } catch (e: Exception) {
            // typing is best-effort
        }
    }
}
