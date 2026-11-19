package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Chats list, messages, read ticks, typing, reactions, deletes — on
 * Supabase (v3.0.0).
 *
 * Layout (all tables prefixed pingchat_ — the project is shared with the
 * MCQ exam app):
 * - pingchat_conversations: last_message, last_message_at, last_message_kind,
 *   last_message_id, last_sender_id (denormalized preview, refreshed on send)
 * - pingchat_conversation_participants: conversation_id, user_id, last_read_at
 * - pingchat_messages: sender_id, body, kind (text/voice/photo), reply_to_id,
 *   media_url, media_path, view_once, kept, consumed_at, deleted_by [uuid]
 * - pingchat_message_reactions: message_id, user_id, emoji (one row per user)
 * - Typing + presence are Realtime Broadcast / Presence — no tables.
 *
 * Timestamps are ISO strings in Postgres; the models keep ISO strings, so
 * TimeFormat / tick logic works unchanged. Conversations and messages are
 * sorted client-side where the query cannot order (all sorting runs after
 * the fetch, same as v2.0.3).
 */
class ChatRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    /** Load all 1-to-1 conversations for the signed-in user, newest first. */
    suspend fun loadConversations(): List<ConversationItem> {
        val me = myId() ?: return emptyList()

        val myParts = client.from("pingchat_conversation_participants").select {
            filter { eq("user_id", me) }
        }.decodeList<Participant>()
        if (myParts.isEmpty()) return emptyList()

        val convIds = myParts.map { it.conversationId }
        val myRead = myParts.associate { it.conversationId to it.lastReadAt }

        val convs = client.from("pingchat_conversations").select {
            filter { isIn("id", convIds) }
            order("last_message_at", Order.DESCENDING)
        }.decodeList<Conversation>()

        val others = client.from("pingchat_conversation_participants").select {
            filter {
                isIn("conversation_id", convIds)
                neq("user_id", me)
            }
        }.decodeList<Participant>()
        val peerIds = others.map { it.userId }.distinct()
        if (peerIds.isEmpty()) return emptyList()

        val peerProfiles = client.from("pingchat_profiles").select {
            filter { isIn("id", peerIds) }
        }.decodeList<Profile>().associateBy { it.id }
        val peerRead = others.associate { it.conversationId to it.lastReadAt }

        return convs.mapNotNull { conv ->
            val peerPart = others.firstOrNull { it.conversationId == conv.id }
                ?: return@mapNotNull null
            val peer = peerProfiles[peerPart.userId] ?: return@mapNotNull null
            // The last-message preview comes from the denormalized fields
            // on the conversation row itself — no messages query.
            val last = if (conv.lastMessageId.isNullOrBlank()) {
                null
            } else {
                ChatMessage(
                    id = conv.lastMessageId,
                    conversationId = conv.id,
                    senderId = conv.lastSenderId ?: "",
                    body = conv.lastMessage ?: "",
                    kind = conv.lastMessageKind ?: "text",
                    createdAt = conv.lastMessageAt ?: ""
                )
            }
            val unread = unreadCount(conv.id, myRead[conv.id])
            ConversationItem(conv, peer, last, myRead[conv.id], peerRead[conv.id], unread)
        }
    }

    private suspend fun unreadCount(conversationId: String, myLastReadAt: String?): Int {
        val me = myId() ?: return 0
        return try {
            // RLS already hides messages I deleted for myself; the time
            // filter runs in the query (ISO strings compare correctly).
            val rows = client.from("pingchat_messages").select(Columns.list("id")) {
                filter {
                    eq("conversation_id", conversationId)
                    neq("sender_id", me)
                    if (myLastReadAt != null) gt("created_at", myLastReadAt)
                }
            }.decodeList<Map<String, String>>()
            rows.size
        } catch (e: Exception) {
            0
        }
    }

    /** Latest messages of a conversation, oldest first. */
    suspend fun loadMessages(conversationId: String, limit: Int = 200): List<ChatMessage> {
        return try {
            client.from("pingchat_messages").select {
                filter { eq("conversation_id", conversationId) }
                order("created_at", Order.ASCENDING)
                limit(limit.toLong())
            }.decodeList<ChatMessage>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** The other participant's profile + their last_read_at for one conversation. */
    suspend fun loadPeer(conversationId: String): Pair<Profile?, String?> {
        val me = myId() ?: return null to null
        return try {
            val other = client.from("pingchat_conversation_participants").select {
                filter {
                    eq("conversation_id", conversationId)
                    neq("user_id", me)
                }
            }.decodeSingleOrNull<Participant>() ?: return null to null
            val profile = client.from("pingchat_profiles").select {
                filter { eq("id", other.userId) }
            }.decodeSingleOrNull<Profile>()
            profile to other.lastReadAt
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
        return try {
            client.from("pingchat_message_reactions").select {
                filter { isIn("message_id", messageIds) }
            }.decodeList<Reaction>().groupBy { it.messageId }
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
        return try {
            val sent = client.from("pingchat_messages").insert(
                buildJsonObject {
                    put("conversation_id", conversationId)
                    put("sender_id", me)
                    put("body", body)
                    put("kind", "text")
                    if (replyToId != null) put("reply_to_id", replyToId)
                }
            ) { select() }.decodeSingleOrNull<ChatMessage>()
            if (sent != null) {
                updateConversationPreview(conversationId, body, "text", sent.id, me)
            }
            sent
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Mark a conversation read (only when the user is actually looking at
     * it). [readUpTo] is the watermark: every message created at or before
     * it counts as read. It MUST be a server timestamp (the newest loaded
     * message's created_at) — unreadCount compares created_at (server
     * time) against last_read_at, so the old device-clock Instant.now()
     * left the newest message unread whenever the device clock trailed
     * the server, and re-opening the chat re-wrote the same lagging time
     * so the badge never cleared. Falls back to device time only when
     * the caller has no server timestamp (no messages loaded yet).
     * Returns true when the update landed, so the caller can tell the
     * chats list to re-read (a participants UPDATE fires no feed tick).
     */
    suspend fun markRead(conversationId: String, readUpTo: Instant? = null): Boolean {
        val me = myId() ?: return false
        return try {
            client.from("pingchat_conversation_participants").update(
                { set("last_read_at", (readUpTo ?: Instant.now()).toString()) }
            ) {
                filter {
                    eq("conversation_id", conversationId)
                    eq("user_id", me)
                }
            }
            true
        } catch (e: Exception) {
            // read markers are best-effort
            false
        }
    }

    /**
     * Send a view-once photo: upload to the public 'chat-photos' bucket at
     * <userId>/<uuid>.<ext>, then insert a kind='photo' message.
     * Supabase Storage is free — no Blaze needed.
     */
    suspend fun sendPhoto(conversationId: String, bytes: ByteArray, mime: String): ChatMessage? {
        val me = myId() ?: return null
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        return try {
            val path = "$me/${UUID.randomUUID()}.$ext"
            client.storage.from("chat-photos").upload(path, bytes) {
                this.contentType = ContentType.parse(mime)
            }
            val url = client.storage.from("chat-photos").publicUrl(path)
            insertMediaMessage(conversationId, me, "photo", url, path)
        } catch (e: Exception) {
            null
        }
    }

    /** Send a view-once voice message: upload the .m4a bytes, then insert. */
    suspend fun sendVoice(conversationId: String, bytes: ByteArray): ChatMessage? {
        val me = myId() ?: return null
        return try {
            val path = "$me/${UUID.randomUUID()}.m4a"
            client.storage.from("voice-messages").upload(path, bytes) {
                this.contentType = ContentType.parse("audio/mp4")
            }
            val url = client.storage.from("voice-messages").publicUrl(path)
            insertMediaMessage(conversationId, me, "voice", url, path)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun insertMediaMessage(
        conversationId: String,
        me: String,
        kind: String,
        url: String,
        path: String
    ): ChatMessage? {
        return try {
            val sent = client.from("pingchat_messages").insert(
                buildJsonObject {
                    put("conversation_id", conversationId)
                    put("sender_id", me)
                    put("body", "")
                    put("kind", kind)
                    put("media_url", url)
                    put("media_path", path)
                    put("view_once", true)
                }
            ) { select() }.decodeSingleOrNull<ChatMessage>()
            if (sent != null) {
                updateConversationPreview(
                    conversationId,
                    if (kind == "photo") "Photo" else "Voice message",
                    kind,
                    sent.id,
                    me
                )
            }
            sent
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateConversationPreview(
        conversationId: String,
        preview: String,
        kind: String,
        messageId: String,
        senderId: String
    ) {
        try {
            client.from("pingchat_conversations").update({
                set("last_message", preview)
                set("last_message_at", Instant.now().toString())
                set("last_message_kind", kind)
                set("last_message_id", messageId)
                set("last_sender_id", senderId)
            }) {
                filter { eq("id", conversationId) }
            }
        } catch (e: Exception) {
            // preview is best-effort; the message itself is already saved
        }
    }

    /**
     * View-once consume: deletes a view-once voice/photo message, but only
     * when the caller is the recipient (the RPC refuses the sender or an
     * already-consumed message). Returns true when the row was deleted.
     */
    suspend fun consumeMessage(conversationId: String, messageId: String): Boolean {
        return try {
            client.postgrest.rpc(
                "pingchat_consume_voice_message",
                buildJsonObject { put("p_message_id", messageId) }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Keep a view-once message in the chat: sets kept = true on the row
     * (PostgREST update). The migration-message-kept.sql RLS policy only
     * lets the RECIPIENT of a view-once message do this, and the consume
     * RPC refuses kept rows, so a kept message is never deleted.
     * Returns true only when the row came back with kept = true.
     * "Keep" means keep in the chat — it does NOT save a copy to the device.
     */
    suspend fun keepMessage(messageId: String): Boolean {
        return try {
            val updated = client.from("pingchat_messages").update(
                { set("kept", true) }
            ) {
                filter { eq("id", messageId) }
                select()
            }.decodeSingleOrNull<ChatMessage>()
            updated?.kept == true
        } catch (e: Exception) {
            false
        }
    }

    /** Best-effort removal of a consumed/deleted media file from Storage.
     * [bucket] is the top folder ("chat-photos" / "voice-messages"). */
    suspend fun deleteMedia(bucket: String, path: String) {
        try {
            client.storage.from(bucket).delete(path)
        } catch (e: Exception) {
            // cleanup is best-effort; the row is already gone
        }
    }

    /** Find the 1-to-1 conversation with another user, or create it (friend-gated). */
    suspend fun getOrCreateConversation(otherUserId: String): String {
        return client.postgrest.rpc(
            "pingchat_get_or_create_conversation",
            buildJsonObject { put("p_user_id", otherUserId) }
        ).decodeAs<String>()
    }

    suspend fun deleteForMe(conversationId: String, messageId: String): Boolean {
        return try {
            client.postgrest.rpc(
                "pingchat_delete_message_for_me",
                buildJsonObject { put("p_message_id", messageId) }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteForEveryone(conversationId: String, messageId: String) {
        // The RPC deletes the row and returns its media_path (or null) so
        // the file can be removed from Storage as well.
        val mediaPath = try {
            client.postgrest.rpc(
                "pingchat_delete_message_for_everyone",
                buildJsonObject { put("p_message_id", messageId) }
            ).decodeAs<String?>()
        } catch (e: Exception) {
            throw e
        }
        if (!mediaPath.isNullOrBlank()) {
            val bucket = if (mediaPath.endsWith(".m4a", ignoreCase = true) ||
                mediaPath.endsWith(".mp3", ignoreCase = true) ||
                mediaPath.endsWith(".webm", ignoreCase = true) ||
                mediaPath.endsWith(".ogg", ignoreCase = true)
            ) {
                "voice-messages"
            } else {
                "chat-photos"
            }
            deleteMedia(bucket, mediaPath)
        }
    }

    suspend fun clearChat(conversationId: String) {
        client.postgrest.rpc(
            "pingchat_clear_chat_for_me",
            buildJsonObject { put("p_conversation_id", conversationId) }
        )
    }

    /** Toggle my reaction on a message (same emoji again removes it). */
    suspend fun toggleReaction(conversationId: String, messageId: String, emoji: String) {
        val me = myId() ?: return
        val mine = client.from("pingchat_message_reactions").select {
            filter {
                eq("message_id", messageId)
                eq("user_id", me)
            }
        }.decodeSingleOrNull<Reaction>()
        if (mine != null && mine.emoji == emoji) {
            client.from("pingchat_message_reactions").delete {
                filter {
                    eq("message_id", messageId)
                    eq("user_id", me)
                }
            }
        } else {
            client.from("pingchat_message_reactions").upsert(
                Reaction(messageId = messageId, userId = me, emoji = emoji)
            ) { onConflict = "message_id,user_id" }
        }
    }

    // ---------- realtime ----------

    /** Room channel for one open chat (cached, subscribed once). */
    private suspend fun roomChannel(conversationId: String): RealtimeChannel {
        val name = "room:$conversationId"
        val channel = RealtimeChannels.get(name) {
            client.realtime.channel(name)
        }
        RealtimeChannels.ensureSubscribed(name)
        return channel
    }

    /** Typing channel for one open chat (cached, subscribed once). */
    private suspend fun typingChannel(conversationId: String): RealtimeChannel {
        val name = "typing:$conversationId"
        val channel = RealtimeChannels.get(name) {
            client.realtime.channel(name)
        }
        RealtimeChannels.ensureSubscribed(name)
        return channel
    }

    /** Live messages of one conversation, oldest first. */
    fun messagesFlow(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val scope = this
        val channel = try {
            roomChannel(conversationId)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        val job = scope.launch {
            merge(
                channel.postgresChangeFlow<PostgresAction.Insert>("public") {
                    table = "pingchat_messages"
                    filter = "conversation_id=eq.$conversationId"
                },
                channel.postgresChangeFlow<PostgresAction.Update>("public") {
                    table = "pingchat_messages"
                    filter = "conversation_id=eq.$conversationId"
                },
                channel.postgresChangeFlow<PostgresAction.Delete>("public") {
                    table = "pingchat_messages"
                    filter = "conversation_id=eq.$conversationId"
                }
            ).collect {
                trySend(loadMessages(conversationId))
            }
        }
        trySend(loadMessages(conversationId))
        awaitClose { job.cancel() }
    }

    /** The other participant's last_read_at (ISO string), live. */
    fun peerReadFlow(conversationId: String): Flow<String?> = callbackFlow {
        val scope = this
        val channel = try {
            roomChannel(conversationId)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        val job = scope.launch {
            channel.postgresChangeFlow<PostgresAction.Update>("public") {
                table = "pingchat_conversation_participants"
                filter = "conversation_id=eq.$conversationId"
            }.collect { action ->
                val userId = action.record["user_id"]?.toString()?.trim('"')
                if (userId != null && userId != myId()) {
                    trySend(
                        action.record["last_read_at"]?.toString()?.trim('"')
                            ?.takeIf { it != "null" }
                    )
                }
            }
        }
        awaitClose { job.cancel() }
    }

    /** Typing events from the peer (broadcast on the typing channel). */
    @OptIn(SupabaseInternal::class)
    fun typingFlow(conversationId: String): Flow<TypingPayload> = callbackFlow {
        val scope = this
        val channel = try {
            typingChannel(conversationId)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        val job = scope.launch {
            channel.broadcastFlow<TypingPayload>("typing").collect { trySend(it) }
        }
        awaitClose { job.cancel() }
    }

    /** Typing-stop events from the peer. */
    @OptIn(SupabaseInternal::class)
    fun typingStopFlow(conversationId: String): Flow<TypingPayload> = callbackFlow {
        val scope = this
        val channel = try {
            typingChannel(conversationId)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        val job = scope.launch {
            channel.broadcastFlow<TypingPayload>("typing-stop").collect { trySend(it) }
        }
        awaitClose { job.cancel() }
    }

    /** Tell the peer I am typing (broadcast, best-effort). */
    suspend fun sendTyping(conversationId: String, payload: TypingPayload) {
        try {
            typingChannel(conversationId).broadcast("typing", payload)
        } catch (e: Exception) {
            // typing is best-effort
        }
    }

    /** Tell the peer I stopped typing (broadcast, best-effort). */
    suspend fun sendTypingStop(conversationId: String, payload: TypingPayload) {
        try {
            typingChannel(conversationId).broadcast("typing-stop", payload)
        } catch (e: Exception) {
            // typing is best-effort
        }
    }

    // ---------- presence (who is online) + global feed (SessionManager) ----------

    /**
     * The "online" presence channel. Presence key = user id (same as the web app).
     *
     * Creation only — this does NOT subscribe. The presence listener must be
     * attached BEFORE subscribing (see SessionManager.startPresence): the
     * server sends the full presence_state — everyone already online — as
     * soon as we subscribe, and presenceChangeFlow only emits changes that
     * arrive after the listener is attached. Subscribe with
     * [subscribePresence], after the collector is running.
     */
    suspend fun presenceChannel(myId: String): RealtimeChannel {
        return RealtimeChannels.get("online") {
            client.realtime.channel("online") {
                presence { key = myId }
            }
        }
    }

    /**
     * Subscribe the "online" channel (blocks until SUBSCRIBED, 10s cap in
     * RealtimeChannels). Call only AFTER the presenceFlow collector is
     * attached — never before.
     */
    suspend fun subscribePresence() {
        RealtimeChannels.ensureSubscribed("online", block = true)
    }

    /** Emits (joinedUserIds, leftUserIds) on every presence change. */
    fun presenceFlow(channel: RealtimeChannel): Flow<Pair<Set<String>, Set<String>>> =
        channel.presenceChangeFlow().map { action ->
            action.joins.keys to action.leaves.keys
        }

    suspend fun trackPresence(channel: RealtimeChannel, state: PresenceState) {
        try {
            channel.track(state)
        } catch (e: Exception) {
            // best-effort
        }
    }

    /** Global feed: INSERT / UPDATE / DELETE on pingchat_messages. Caller must subscribe. */
    suspend fun messagesFeedChannel(): RealtimeChannel {
        val channel = RealtimeChannels.get("messages-feed") {
            client.realtime.channel("messages-feed")
        }
        RealtimeChannels.ensureSubscribed("messages-feed")
        return channel
    }

    fun messagesInsertFlow(channel: RealtimeChannel): Flow<ChatMessage> =
        channel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "pingchat_messages"
        }.map { it.decodeRecord<ChatMessage>() }

    fun messagesUpdateFlow(channel: RealtimeChannel): Flow<ChatMessage> =
        channel.postgresChangeFlow<PostgresAction.Update>("public") {
            table = "pingchat_messages"
        }.map { it.decodeRecord<ChatMessage>() }

    fun messagesDeleteFlow(channel: RealtimeChannel): Flow<String> =
        channel.postgresChangeFlow<PostgresAction.Delete>("public") {
            table = "pingchat_messages"
        }.map { it.oldRecord["id"]?.toString()?.trim('"') ?: "" }

    /** Drop a cached channel (sign-out). */
    suspend fun dropChannel(name: String) = RealtimeChannels.drop(name)

    /** Drop every chat channel (sign-out). */
    suspend fun dropAllChannels() = RealtimeChannels.dropAll()
}
