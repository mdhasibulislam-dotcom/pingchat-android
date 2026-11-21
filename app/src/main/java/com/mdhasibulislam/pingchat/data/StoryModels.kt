package com.mdhasibulislam.pingchat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Story views / reactions / replies (v3.2.2).
 *
 * The "seen" grey ring stays LOCAL-ONLY (SharedPreferences, see
 * StoryRepository) — but WHO viewed is now server-side, so the story
 * owner can see names + times in the Viewers sheet:
 * - pingchat_story_views: one row per (story, viewer), upserted so a
 *   re-view only refreshes viewed_at.
 * - pingchat_story_reactions: one row per (story, user) — PRIMARY KEY
 *   (story_id, user_id) — tap again to change, tap the same emoji to
 *   remove. Reacting also counts as a view.
 * - Story replies are NOT a new table: they are normal chat messages
 *   in pingchat_messages with story_id + story_media_url set (see
 *   database/migration-v322-stories.sql for why), so a reply lands in
 *   the owner's inbox as a message from the replier, and the Viewers
 *   sheet's REPLIES section reads the same rows back.
 */

/** The six reaction emoji — the ONLY place emoji are used as UI
 *  (reactions are emoji by design; all other chrome uses real
 *  vector icons). Order matches the mockup (story-react-reply). */
val STORY_REACTION_EMOJIS: List<String> =
    listOf("❤️", "😂", "😮", "😢", "🔥", "👍")

/** One server-side view row (Supabase: pingchat_story_views). */
@Serializable
data class StoryViewRow(
    @SerialName("id") val id: String = "",
    @SerialName("story_id") val storyId: String,
    @SerialName("viewer_id") val viewerId: String,
    @SerialName("viewed_at") val viewedAt: String = ""
)

/** One story reaction row (Supabase: pingchat_story_reactions). */
@Serializable
data class StoryReactionRow(
    @SerialName("story_id") val storyId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("emoji") val emoji: String,
    @SerialName("reacted_at") val reactedAt: String = ""
)

/** One row of the Viewers sheet: a view + who viewed + their
 *  reaction on this story, if they left one. */
data class StoryViewerItem(
    val view: StoryViewRow,
    val viewer: Profile?,
    /** Null when this viewer did not react. */
    val reactionEmoji: String? = null
)

/** One row of the Viewers sheet's REPLIES section: the reply message
 *  (a pingchat_messages row with story_id set) + who sent it. */
data class StoryReplyItem(
    val message: ChatMessage,
    val sender: Profile?
)

/** Everything the Viewers bottom-sheet needs for one story. */
data class StoryViewersData(
    val story: Story,
    val viewers: List<StoryViewerItem>,
    val replies: List<StoryReplyItem>
) {
    val viewCount: Int get() = viewers.size
}

/**
 * Compact viewer count, as in the own-story screenshot ("Nijer Story"):
 * plain number under 1000, "1.2K" for thousands, "1.2M" for millions.
 * Trailing ".0" is dropped ("1K", not "1.0K").
 */
fun formatCompactCount(count: Int): String {
    if (count < 1000) return count.toString()
    val (value, suffix) = if (count < 1_000_000) {
        count / 1000.0 to "K"
    } else {
        count / 1_000_000.0 to "M"
    }
    val oneDecimal = (kotlin.math.round(value * 10) / 10.0).toString()
    val trimmed = oneDecimal.removeSuffix(".0")
    return "$trimmed$suffix"
}
