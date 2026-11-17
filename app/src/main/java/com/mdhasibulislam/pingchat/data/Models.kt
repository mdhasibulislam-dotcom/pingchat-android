package com.mdhasibulislam.pingchat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A user profile (public.profiles). */
@Serializable
data class Profile(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("bio") val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/** A 1-to-1 conversation (public.conversations). */
@Serializable
data class Conversation(
    @SerialName("id") val id: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_message_at") val lastMessageAt: String? = null
)

/** Membership + read position (public.conversation_participants). */
@Serializable
data class Participant(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_at") val lastReadAt: String? = null
)

/** A chat message (public.messages). kind is 'text', 'voice' or 'photo'. */
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
    @SerialName("consumed_at") val consumedAt: String? = null,
    @SerialName("deleted_by") val deletedBy: List<String> = emptyList()
)

/** One reaction per user per message (public.message_reactions). */
@Serializable
data class Reaction(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("emoji") val emoji: String,
    @SerialName("created_at") val createdAt: String = ""
)

/** A friend request / friendship (public.friendships). status: pending/accepted/declined. */
@Serializable
data class Friendship(
    @SerialName("id") val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/** A call row (public.calls). Phase 2 — model kept for the Calls tab history. */
@Serializable
data class CallRow(
    @SerialName("id") val id: String,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("kind") val kind: String = "audio",
    @SerialName("status") val status: String = "ringing",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("created_at") val createdAt: String = ""
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
