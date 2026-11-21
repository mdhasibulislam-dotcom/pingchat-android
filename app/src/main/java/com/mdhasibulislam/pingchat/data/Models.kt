package com.mdhasibulislam.pingchat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A user profile (Supabase: pingchat_profiles). v3.1.0 adds cover photo,
 *  city, education, status line, social links, theme colour and the
 *  verified flag (migration-v310-profile.sql). All new fields are
 *  nullable/defaulted, so rows written before the migration still decode. */
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
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("education") val education: String? = null,
    @SerialName("status_line") val statusLine: String? = null,
    @SerialName("social_links") val socialLinks: SocialLinks? = null,
    @SerialName("theme_color") val themeColor: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    /** v3.2.2 (migration-v322-dob.sql): date of birth, ISO yyyy-MM-dd. */
    @SerialName("date_of_birth") val dateOfBirth: String? = null
)

/**
 * Social links, stored as one jsonb object (pingchat_profiles.social_links):
 * {"facebook": "...", "instagram": "...", "github": "..."}. Any key may be
 * absent or blank — only non-blank URLs are shown, and tapping one opens
 * it in the browser. Never a free-text field: the edit form asks for one
 * URL per network.
 */
@Serializable
data class SocialLinks(
    @SerialName("facebook") val facebook: String? = null,
    @SerialName("instagram") val instagram: String? = null,
    @SerialName("github") val github: String? = null
) {
    /** Only the networks that actually have a URL, in display order. */
    fun nonBlank(): List<Pair<String, String>> = listOf(
        "Facebook" to (facebook ?: ""),
        "Instagram" to (instagram ?: ""),
        "GitHub" to (github ?: "")
    ).filter { it.second.isNotBlank() }
}

/** One profile-view row (Supabase: pingchat_profile_views). */
@Serializable
data class ProfileView(
    @SerialName("id") val id: String,
    @SerialName("profile_user_id") val profileUserId: String,
    @SerialName("viewer_user_id") val viewerUserId: String,
    @SerialName("viewed_at") val viewedAt: String = ""
)

/** One recent viewer with their profile (avatar + name for the list). */
data class ProfileViewerItem(
    val view: ProfileView,
    val viewer: Profile?
)

/** A 1-to-1 or group conversation (Supabase: pingchat_conversations).
 *  v3.1.0: is_group / group_name / group_avatar_url / created_by are the
 *  group columns from migration-v310-groups.sql; 1-to-1 rows keep
 *  is_group = false. */
@Serializable
data class Conversation(
    @SerialName("id") val id: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_kind") val lastMessageKind: String? = null,
    @SerialName("last_message_id") val lastMessageId: String? = null,
    @SerialName("last_sender_id") val lastSenderId: String? = null,
    @SerialName("is_group") val isGroup: Boolean = false,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("group_avatar_url") val groupAvatarUrl: String? = null,
    @SerialName("created_by") val createdBy: String? = null
)

/** Membership + read position (Supabase: pingchat_conversation_participants).
 *  v3.1.0: role is 'admin' | 'member' (groups; 1-to-1 rows stay 'member').
 *  is_pinned / is_muted / is_archived are per-user, per-chat flags from
 *  migration-v310-chat-features.sql — the v3 update-self RLS policy lets
 *  a user change only their own row, so these are safe to update directly. */
@Serializable
data class Participant(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_at") val lastReadAt: String? = null,
    @SerialName("role") val role: String = "member",
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("pinned_at") val pinnedAt: String? = null,
    @SerialName("is_muted") val isMuted: Boolean = false,
    @SerialName("muted_at") val mutedAt: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("archived_at") val archivedAt: String? = null
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
    // v3.1.0: no view-once — photo/voice stay in the chat like normal
    // messages; the server deletes them 7 days after they were sent
    // (database/migration-v310-media-expiry.sql). The view_once /
    // consumed_at columns still exist in the DB (legacy, unused) so
    // the already-installed v3.0.2 APK keeps working until it updates.
    @SerialName("deleted_by") val deletedBy: List<String> = emptyList(),
    /** v3.1.0 (migration-v310-chat-features.sql): set when the sender edits
     *  a text message — the bubble shows an "edited" label. */
    @SerialName("edited_at") val editedAt: String? = null,
    /** v3.1.0: the ORIGINAL sender of a forwarded message — the bubble
     *  shows a "Forwarded" label. Null for messages sent normally. */
    @SerialName("forwarded_from") val forwardedFrom: String? = null,
    /** v3.1.0: at most one pinned message per conversation; the chat
     *  shows it in a banner above the messages. */
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("pinned_at") val pinnedAt: String? = null,
    @SerialName("pinned_by") val pinnedBy: String? = null,
    /** v3.2.2 (migration-v322-stories.sql): set when this message is a
     *  REPLY to a story — the id of the story replied to, plus a
     *  snapshot of its media URL so the chat bubble can show the story
     *  attached/quoted above the reply text. Null for normal messages.
     *  story_id is ON DELETE SET NULL: the reply stays in the inbox
     *  even after the 12-hour story itself is deleted. */
    @SerialName("story_id") val storyId: String? = null,
    @SerialName("story_media_url") val storyMediaUrl: String? = null,
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

/** One row of the Chats list: conversation + peer + last message + read state.
 *  v3.1.0: peer is null for group conversations — use [displayName] and
 *  [memberProfiles] instead. isPinned / isMuted / isArchived are the
 *  caller's own flags from their participant row. */
data class ConversationItem(
    val conversation: Conversation,
    val peer: Profile? = null,
    val lastMessage: ChatMessage?,
    val myLastReadAt: String?,
    val peerLastReadAt: String?,
    val unreadCount: Int,
    val memberProfiles: List<Profile> = emptyList(),
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false
) {
    val isGroup: Boolean get() = conversation.isGroup

    /** Name shown in the chats list: group name, else the peer's name. */
    val displayName: String
        get() = if (isGroup) {
            conversation.groupName?.takeIf { it.isNotBlank() }
                ?: memberProfiles.joinToString(", ") {
                    it.fullName.ifBlank { it.username }
                }.ifBlank { "Group" }
        } else {
            peer?.fullName?.ifBlank { peer?.username } ?: "Chat"
        }
}

/** One group member: their participant row (role) + profile. */
data class GroupMember(
    val participant: Participant,
    val profile: Profile?
) {
    val isAdmin: Boolean get() = participant.role == "admin"
}

/** Everything the group settings screen needs for one group. */
data class GroupInfo(
    val conversation: Conversation,
    val members: List<GroupMember>,
    val myRole: String,
    val myUserId: String
) {
    val isAdmin: Boolean get() = myRole == "admin"
    val memberCount: Int get() = members.size
}

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
