package com.mdhasibulislam.pingchat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A user profile (Supabase: pingchat_profiles). */
@Serializable
data class Profile(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("bio") val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("online") val online: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/** A 1-to-1 conversation (Supabase: pingchat_conversations). */
@Serializable
data class Conversation(
    @SerialName("id") val id: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_kind") val lastMessageKind: String? = null,
    @SerialName("last_message_id") val lastMessageId: String? = null,
    @SerialName("last_sender_id") val lastSenderId: String? = null
)

/** Membership + read position (Supabase: pingchat_conversation_participants). */
@Serializable
data class Participant(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_at") val lastReadAt: String? = null
)

/** A chat message (Supabase: pingchat_messages). */
@Serializable
data class ChatMessage(
    @SerialName("id") val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("body") val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("kind") val kind: String = "text",
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("view_once") val viewOnce: Boolean = false,
    /** True when the recipient tapped Keep on a view-once message: the
     *  message then stays in the chat instead of being consumed. */
    @SerialName("kept") val kept: Boolean = false,
    @SerialName("consumed_at") val consumedAt: String? = null,
    @SerialName("deleted_by") val deletedBy: List<String> = emptyList(),
    /** Reactions live in pingchat_message_reactions, not on the message row. */
    val reactions: Map<String, String> = emptyMap()
)

/** One reaction per user per message (Supabase: pingchat_message_reactions). */
@Serializable
data class Reaction(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("emoji") val emoji: String,
    @SerialName("created_at") val createdAt: String = ""
)

/** A friend request / friendship (Supabase: pingchat_friendships). status: pending/accepted/declined. */
@Serializable
data class Friendship(
    @SerialName("id") val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/** A call row (Supabase: pingchat_calls). */
@Serializable
data class CallRow(
    @SerialName("id") val id: String,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("caller_name") val callerName: String? = null,
    @SerialName("kind") val kind: String = "audio",
    @SerialName("status") val status: String = "ringing",
    @SerialName("offer_sdp") val offerSdp: SdpJson? = null,
    @SerialName("answer_sdp") val answerSdp: SdpJson? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("created_at") val createdAt: String = ""
)

/**
 * One side's own call-history entry (Supabase: pingchat_call_history,
 * migration-call-history.sql). One row per user per finished call, so
 * deleting it removes the history for that user only.
 * status: ended (answered, has duration) / missed (incoming, unanswered)
 * / declined (incoming, declined by me) / unanswered (outgoing, no answer).
 */
@Serializable
data class CallHistoryRow(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("call_id") val callId: String? = null,
    @SerialName("peer_id") val peerId: String,
    @SerialName("peer_name") val peerName: String? = null,
    @SerialName("kind") val kind: String = "audio",
    @SerialName("direction") val direction: String = "outgoing",
    @SerialName("status") val status: String = "ended",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("created_at") val createdAt: String = ""
)

/** One history row with the peer's profile (for avatar + current name). */
data class CallHistoryItem(
    val row: CallHistoryRow,
    val peer: Profile?
)

/** Typing broadcast payload on channel "typing:<conversationId>". */
@Serializable
data class TypingPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String = ""
)

/** Presence state tracked on the "online" channel. */
@Serializable
data class PresenceState(
    @SerialName("user_id") val userId: String = "",
    @SerialName("name") val name: String = ""
)

/** One row of the Chats list: conversation + peer + last message + read state. */
data class ConversationItem(
    val conversation: Conversation,
    val peer: Profile,
    val lastMessage: ChatMessage?,
    val myLastReadAt: String?,
    val peerLastReadAt: String?,
    val unreadCount: Int
)

/** Friendship state from the caller's point of view. */
enum class FriendState {
    NONE, PENDING_OUT, PENDING_IN, FRIENDS, DECLINED;

    companion object {
        fun fromRpc(value: String?): FriendState = when (value) {
            "pending_out" -> PENDING_OUT
            "pending_in" -> PENDING_IN
            "friends" -> FRIENDS
            "declined" -> DECLINED
            else -> NONE
        }
    }
}

/** Read-tick state for one of my messages — same 3 states as the web app. */
enum class TickState { SENT, DELIVERED, SEEN }

/**
 * Text a bubble or chat-list preview should show for a message — never
 * blank. A text-kind row with an empty body (e.g. a voice message migrated
 * from the old v1 app, whose kind stayed 'text') used to render as a tiny
 * empty green pill showing only the tick. Such rows fall back to
 * "Voice message" / "Photo" from their media, or "Message" when there is
 * nothing else to show.
 */
fun messageDisplayText(message: ChatMessage): String {
    if (message.body.isNotBlank()) return message.body
    return when (message.kind) {
        "photo" -> "Photo"
        "voice" -> "Voice message"
        else -> {
            val path = message.mediaPath ?: message.mediaUrl ?: ""
            when {
                path.endsWith(".m4a", ignoreCase = true) ||
                    path.endsWith(".mp3", ignoreCase = true) ||
                    path.endsWith(".webm", ignoreCase = true) ||
                    path.endsWith(".ogg", ignoreCase = true) ||
                    path.endsWith(".mp4", ignoreCase = true) -> "Voice message"
                path.isNotBlank() -> "Photo"
                else -> "Message"
            }
        }
    }
}
