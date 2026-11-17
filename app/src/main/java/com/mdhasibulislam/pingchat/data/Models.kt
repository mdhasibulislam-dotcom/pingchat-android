package com.mdhasibulislam.pingchat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A user profile (Firestore: users/{uid}). */
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
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/** A 1-to-1 conversation (Firestore: conversations/{id}). */
@Serializable
data class Conversation(
    @SerialName("id") val id: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_message_at") val lastMessageAt: String? = null
)

/** Membership + read position — in Firestore this lives on the conversation
 * doc itself as lastReadAt: {userId: epochMillis}. */
@Serializable
data class Participant(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_at") val lastReadAt: String? = null
)

/** A chat message (Firestore: conversations/{id}/messages/{msgId}). */
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
    @SerialName("deleted_by") val deletedBy: List<String> = emptyList(),
    /** Reactions stored on the message doc itself: {userId: emoji}. */
    val reactions: Map<String, String> = emptyMap()
)

/** One reaction per user per message (stored on the message doc: reactions {userId: emoji}). */
@Serializable
data class Reaction(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("emoji") val emoji: String,
    @SerialName("created_at") val createdAt: String = ""
)

/** A friend request / friendship (Firestore: friendships/{pairId}). status: pending/accepted/declined. */
@Serializable
data class Friendship(
    @SerialName("id") val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/** A call row (Firestore: calls/{callId}). */
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

// ---------- Firestore document converters (v2.0.0) ----------
// Firestore stores timestamps as epoch millis; the models keep ISO strings
// so every UI / ViewModel comparison (TimeFormat.parse, tickState) works
// unchanged. Field names in Firestore are camelCase; the @SerialName
// values above are kept for readability only.

@Suppress("UNCHECKED_CAST")
fun profileFromDoc(id: String, data: Map<String, Any?>): Profile = Profile(
    id = id,
    username = data["username"] as? String ?: "",
    fullName = data["fullName"] as? String ?: "",
    bio = data["bio"] as? String,
    avatarUrl = data["avatarUrl"] as? String,
    phone = data["phone"] as? String,
    fcmToken = data["fcmToken"] as? String,
    isPublic = data["isPublic"] as? Boolean ?: true,
    createdAt = millisToIsoOrEmpty(data["createdAt"] as? Long),
    updatedAt = millisToIsoOrEmpty(data["updatedAt"] as? Long)
)

fun conversationFromDoc(id: String, data: Map<String, Any?>): Conversation = Conversation(
    id = id,
    createdAt = millisToIsoOrEmpty(data["createdAt"] as? Long),
    lastMessageAt = millisToIso(data["lastMessageAt"] as? Long)
)

@Suppress("UNCHECKED_CAST")
fun chatMessageFromDoc(conversationId: String, id: String, data: Map<String, Any?>): ChatMessage =
    ChatMessage(
        id = id,
        conversationId = conversationId,
        senderId = data["senderId"] as? String ?: "",
        body = data["body"] as? String ?: "",
        createdAt = millisToIsoOrEmpty(data["createdAtMillis"] as? Long),
        replyToId = data["replyToId"] as? String,
        kind = data["kind"] as? String ?: "text",
        mediaUrl = data["mediaUrl"] as? String,
        mediaPath = data["mediaPath"] as? String,
        viewOnce = data["viewOnce"] as? Boolean ?: false,
        consumedAt = millisToIso(data["consumedAt"] as? Long),
        deletedBy = (data["deletedBy"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        reactions = (data["reactions"] as? Map<*, *>)
            ?.mapNotNull { (k, v) -> (k as? String)?.let { it to (v as? String ?: "") } }
            ?.toMap() ?: emptyMap()
    )

fun friendshipFromDoc(id: String, data: Map<String, Any?>): Friendship = Friendship(
    id = id,
    requesterId = data["requesterId"] as? String ?: "",
    addresseeId = data["addresseeId"] as? String ?: "",
    status = data["status"] as? String ?: "pending",
    createdAt = millisToIsoOrEmpty(data["createdAt"] as? Long),
    updatedAt = millisToIsoOrEmpty(data["updatedAt"] as? Long)
)

fun callRowFromDoc(id: String, data: Map<String, Any?>): CallRow = CallRow(
    id = id,
    conversationId = data["conversationId"] as? String,
    callerId = data["callerId"] as? String ?: "",
    calleeId = data["calleeId"] as? String ?: "",
    kind = data["kind"] as? String ?: "audio",
    status = data["status"] as? String ?: "ringing",
    startedAt = millisToIso(data["startedAt"] as? Long),
    endedAt = millisToIso(data["endedAt"] as? Long),
    durationSeconds = (data["durationSeconds"] as? Long)?.toInt(),
    createdAt = millisToIsoOrEmpty(data["createdAt"] as? Long)
)

/** Deterministic conversation / friendship doc id for a pair of users. */
fun pairDocId(a: String, b: String): String =
    if (a < b) "${a}_${b}" else "${b}_${a}"
