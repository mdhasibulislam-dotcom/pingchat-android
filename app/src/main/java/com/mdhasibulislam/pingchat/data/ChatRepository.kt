package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** Chats list, messages, read ticks, typing, reactions, deletes. Mirrors js/chats.js. */
class ChatRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    /** Load all 1-to-1 conversations for the signed-in user, newest first. */
    suspend fun loadConversations(): List<ConversationItem> {
        val me = myId() ?: return emptyList()
        val myParts = client.from("conversation_participants").select {
            filter { eq("user_id", me) }
        }.decodeList<Participant>()
        if (myParts.isEmpty()) return emptyList()
        val convIds = myParts.map { it.conversationId }
        val myRead = myParts.associate { it.conversationId to it.lastReadAt }

        val convs = client.from("conversations").select {
            filter { isIn("id", convIds) }
            order("last_message_at", Order.DESCENDING)
        }.decodeList<Conversation>()

        val others = client.from("conversation_participants").select {
            filter {
                isIn("conversation_id", convIds)
                neq("user_id", me)
            }
        }.decodeList<Participant>()
        val peerIds = others.map { it.userId }.distinct()
        if (peerIds.isEmpty()) return emptyList()
        val peerProfiles = client.from("profiles").select {
            filter { isIn("id", peerIds) }
        }.decodeList<Profile>().associateBy { it.id }
        val peerRead = others.associate { it.conversationId to it.lastReadAt }

        return convs.mapNotNull { conv ->
            val peerPart = others.firstOrNull { it.conversationId == conv.id } ?: return@mapNotNull null
            val peer = peerProfiles[peerPart.userId] ?: return@mapNotNull null
            val last = client.from("messages").select {
                filter { eq("conversation_id", conv.id) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeSingleOrNull<ChatMessage>()
            val unread = unreadCount(conv.id, myRead[conv.id])
            ConversationItem(conv, peer, last, myRead[conv.id], peerRead[conv.id], unread)
        }
    }

    private suspend fun unreadCount(conversationId: String, myLastReadAt: String?): Int {
        val me = myId() ?: return 0
        return try {
            val rows = client.from("messages").select(Columns.list("id")) {
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
        return client.from("messages").select {
            filter { eq("conversation_id", conversationId) }
            order("created_at", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<ChatMessage>()
    }

    /** The other participant's profile + their last_read_at for one conversation. */
    suspend fun loadPeer(conversationId: String): Pair<Profile?, String?> {
        val me = myId() ?: return null to null
        val other = client.from("conversation_participants").select {
            filter {
                eq("conversation_id", conversationId)
                neq("user_id", me)
            }
        }.decodeSingleOrNull<Participant>() ?: return null to null
        val profile = client.from("profiles").select {
            filter { eq("id", other.userId) }
        }.decodeSingleOrNull<Profile>()
        return profile to other.lastReadAt
    }

    /** Reactions for the given messages, grouped by message id. */
    suspend fun loadReactions(messageIds: List<String>): Map<String, List<Reaction>> {
        if (messageIds.isEmpty()) return emptyMap()
        return try {
            client.from("message_reactions").select {
                filter { isIn("message_id", messageIds) }
            }.decodeList<Reaction>().groupBy { it.messageId }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Send a text message. Returns the inserted row (or null if the insert failed). */
    suspend fun sendMessage(conversationId: String, body: String, replyToId: String? = null): ChatMessage? {
        val me = myId() ?: return null
        return try {
            client.from("messages").insert(
                buildJsonObject {
                    put("conversation_id", conversationId)
                    put("sender_id", me)
                    put("body", body)
                    put("kind", "text")
                    if (replyToId != null) put("reply_to_id", replyToId)
                }
            ) { select() }.decodeSingleOrNull<ChatMessage>()
        } catch (e: Exception) {
            null
        }
    }

    /** Mark a conversation read (only when the user is actually looking at it). */
    suspend fun markRead(conversationId: String) {
        val me = myId() ?: return
        try {
            client.from("conversation_participants").update(
                { set("last_read_at", Instant.now().toString()) }
            ) {
                filter {
                    eq("conversation_id", conversationId)
                    eq("user_id", me)
                }
            }
        } catch (e: Exception) {
            // read markers are best-effort
        }
    }

    /** Find the 1-to-1 conversation with another user, or create it (friend-gated). */
    suspend fun getOrCreateConversation(otherUserId: String): String {
        return client.postgrest.rpc(
            "get_or_create_conversation",
            buildJsonObject { put("p_user_id", otherUserId) }
        ).decodeAs<String>()
    }

    suspend fun deleteForMe(messageId: String): Boolean {
        return try {
            client.postgrest.rpc(
                "delete_message_for_me",
                buildJsonObject { put("p_message_id", messageId) }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteForEveryone(messageId: String) {
        client.postgrest.rpc(
            "delete_message_for_everyone",
            buildJsonObject { put("p_message_id", messageId) }
        )
    }

    suspend fun clearChat(conversationId: String) {
        client.postgrest.rpc(
            "clear_chat_for_me",
            buildJsonObject { put("p_conversation_id", conversationId) }
        )
    }

    /** Toggle my reaction on a message (same emoji again removes it). */
    suspend fun toggleReaction(messageId: String, emoji: String) {
        val me = myId() ?: return
        val mine = client.from("message_reactions").select {
            filter {
                eq("message_id", messageId)
                eq("user_id", me)
            }
        }.decodeSingleOrNull<Reaction>()
        if (mine != null && mine.emoji == emoji) {
            client.from("message_reactions").delete {
                filter {
                    eq("message_id", messageId)
                    eq("user_id", me)
                }
            }
        } else {
            client.from("message_reactions").upsert(
                Reaction(messageId = messageId, userId = me, emoji = emoji)
            ) { onConflict = "message_id,user_id" }
        }
    }

    // ---------- realtime ----------

    /** Global feed: INSERT / UPDATE / DELETE on messages. Caller must subscribe. */
    fun messagesFeedChannel() = client.realtime.channel("messages-feed")

    fun messagesInsertFlow(channel: io.github.jan.supabase.realtime.RealtimeChannel): Flow<ChatMessage> =
        channel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "messages"
        }.map { it.decodeRecord<ChatMessage>() }

    fun messagesUpdateFlow(channel: io.github.jan.supabase.realtime.RealtimeChannel): Flow<ChatMessage> =
        channel.postgresChangeFlow<PostgresAction.Update>("public") {
            table = "messages"
        }.map { it.decodeRecord<ChatMessage>() }

    fun messagesDeleteFlow(channel: io.github.jan.supabase.realtime.RealtimeChannel): Flow<String> =
        channel.postgresChangeFlow<PostgresAction.Delete>("public") {
            table = "messages"
        }.map { it.oldRecord["id"]?.toString()?.trim('"') ?: "" }

    /** Room channel for one open chat: peer read-position updates. */
    fun roomChannel(conversationId: String) =
        client.realtime.channel("room:$conversationId")

    fun peerReadFlow(channel: io.github.jan.supabase.realtime.RealtimeChannel, conversationId: String): Flow<String?> =
        channel.postgresChangeFlow<PostgresAction.Update>("public") {
            table = "conversation_participants"
            filter = "conversation_id=eq.$conversationId"
        }.map { action ->
            action.record["last_read_at"]?.toString()?.trim('"')?.takeIf { it != "null" }
        }

    /** Typing channel for one open chat. */
    fun typingChannel(conversationId: String) =
        client.realtime.channel("typing:$conversationId")

    @OptIn(SupabaseInternal::class)
    fun typingFlow(channel: io.github.jan.supabase.realtime.RealtimeChannel): Flow<TypingPayload> =
        channel.broadcastFlow<TypingPayload>("typing")

    @OptIn(SupabaseInternal::class)
    fun typingStopFlow(channel: io.github.jan.supabase.realtime.RealtimeChannel): Flow<TypingPayload> =
        channel.broadcastFlow<TypingPayload>("typing-stop")

    suspend fun sendTyping(channel: io.github.jan.supabase.realtime.RealtimeChannel, payload: TypingPayload) {
        try {
            channel.broadcast("typing", payload)
        } catch (e: Exception) {
            // best-effort
        }
    }

    suspend fun sendTypingStop(channel: io.github.jan.supabase.realtime.RealtimeChannel, payload: TypingPayload) {
        try {
            channel.broadcast("typing-stop", payload)
        } catch (e: Exception) {
            // best-effort
        }
    }

    // ---------- presence (who is online) ----------

    /** The "online" presence channel. Presence key = user id (same as the web app). */
    fun presenceChannel(myId: String) = client.realtime.channel("online") {
        presence { key = myId }
    }

    /** Emits (joinedUserIds, leftUserIds) on every presence change. */
    fun presenceFlow(channel: io.github.jan.supabase.realtime.RealtimeChannel): Flow<Pair<Set<String>, Set<String>>> =
        channel.presenceChangeFlow().map { action ->
            action.joins.keys to action.leaves.keys
        }

    suspend fun trackPresence(channel: io.github.jan.supabase.realtime.RealtimeChannel, state: PresenceState) {
        try {
            channel.track(state)
        } catch (e: Exception) {
            // best-effort
        }
    }
}
