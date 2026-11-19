package com.mdhasibulislam.pingchat.data

import android.content.Context
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Stories (v3.1.0) — 12-hour stories on Supabase (project tjsalnhtuqqigyhvgpnb).
 *
 * Layout (see database/migration-v310-stories.sql):
 * - pingchat_stories: user_id, media_url, media_path, media_type
 *   ('photo' | 'video'), created_at, expires_at (= created_at + 12 hours).
 * - Storage bucket 'stories' (public, 10 MB, <userId>/<uuid>.<ext>),
 *   same shape as the 'chat-photos' bucket.
 *
 * Lifetime: a story lives exactly 12 hours, then it is GONE — the row
 * and its Storage file are both deleted, with no permanent save anywhere
 * (no Story Highlight feature exists on purpose). Deletion has two layers:
 * 1. Server: pg_cron runs pingchat_cleanup_expired_stories() every 15 min
 *    (see the migration file — pg_cron must be enabled in the dashboard
 *    first: Database > Extensions > pg_cron).
 * 2. Client: every read filters expires_at > now, so an expired story is
 *    never shown even if the cron run has not happened yet.
 *
 * Visibility: RLS lets friends read a story (see the migration's
 * pingchat_is_friend_or_self helper) and only the owner insert/delete it.
 * Blocks are honoured by the same policies. "Seen" (grey ring) is local
 * only — a story has no server-side view tracking — kept in
 * SharedPreferences ("pingchat", key "seen_story_ids") and pruned to the
 * stories that still exist whenever the list loads.
 */
@Serializable
data class Story(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("media_url") val mediaUrl: String,
    @SerialName("media_path") val mediaPath: String,
    @SerialName("media_type") val mediaType: String = "photo",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("expires_at") val expiresAt: String = ""
) {
    val isVideo: Boolean get() = mediaType == "video"
    val isExpired: Boolean get() {
        val exp = TimeFormat.parse(expiresAt) ?: return true
        return Instant.now().isAfter(exp)
    }
}

/** One person's stories, newest first, for the ring strip. */
data class StoryGroup(
    val user: Profile,
    val stories: List<Story>,
    val isMine: Boolean,
    val allSeen: Boolean
)

class StoryRepository(private val context: Context? = null) {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    companion object {
        /** Stories live exactly 12 hours — never longer, never saved. */
        const val STORY_TTL_HOURS = 12L
        const val STORY_BUCKET = "stories"
        /** Same 10 MB cap as the 'chat-photos' bucket (see the migration). */
        const val STORY_MAX_BYTES = 10 * 1024 * 1024
        private const val SEEN_PREFS_KEY = "seen_story_ids"
    }

    private fun seenPrefs() =
        context?.getSharedPreferences("pingchat", Context.MODE_PRIVATE)

    /** Ids of stories this device has already viewed (local only). */
    fun seenIds(): Set<String> =
        seenPrefs()?.getStringSet(SEEN_PREFS_KEY, emptySet())?.toSet() ?: emptySet()

    fun markSeen(storyId: String) {
        val prefs = seenPrefs() ?: return
        val updated = seenIds().toMutableSet().apply { add(storyId) }
        prefs.edit().putStringSet(SEEN_PREFS_KEY, updated).apply()
    }

    private fun pruneSeen(keeping: Set<String>) {
        val prefs = seenPrefs() ?: return
        val pruned = seenIds().intersect(keeping)
        if (pruned.size != seenIds().size) {
            prefs.edit().putStringSet(SEEN_PREFS_KEY, pruned).apply()
        }
    }

    /**
     * Upload a story: file to the public 'stories' bucket at
     * <userId>/<uuid>.<ext>, then a pingchat_stories row with
     * expires_at = now + 12 hours. Photo or video (mime decides).
     * Returns the inserted row, or null when it failed.
     */
    suspend fun uploadStory(bytes: ByteArray, mime: String): Story? {
        val me = myId() ?: return null
        if (bytes.size > STORY_MAX_BYTES) return null
        val isVideo = mime.startsWith("video/")
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            "video/3gpp" -> "3gp"
            else -> if (isVideo) "mp4" else "jpg"
        }
        return try {
            val path = "$me/${UUID.randomUUID()}.$ext"
            client.storage.from(STORY_BUCKET).upload(path, bytes) {
                this.contentType = ContentType.parse(mime)
            }
            val url = client.storage.from(STORY_BUCKET).publicUrl(path)
            val expiresAt = Instant.now().plus(STORY_TTL_HOURS, ChronoUnit.HOURS).toString()
            client.from("pingchat_stories").insert(
                buildJsonObject {
                    put("user_id", me)
                    put("media_url", url)
                    put("media_path", path)
                    put("media_type", if (isVideo) "video" else "photo")
                    put("expires_at", expiresAt)
                }
            ) { select() }.decodeSingleOrNull<Story>()
        } catch (e: Exception) {
            null
        }
    }

    /** All unexpired stories (mine + friends'), newest first. */
    suspend fun loadStories(): List<Story> {
        return try {
            // Server time wins: expires_at > now() hides expired rows even
            // before the pg_cron cleanup has run.
            client.from("pingchat_stories").select {
                filter { gt("expires_at", Instant.now().toString()) }
                order("created_at", Order.DESCENDING)
            }.decodeList<Story>().filter { !it.isExpired }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Stories grouped by person for the ring strip: my group first,
     * then friends, newest story first inside each group. Only accepted
     * friends (either side of the friendship) plus me — RLS already
     * restricts reads to friends, this is the client-side grouping.
     */
    suspend fun loadStoryGroups(): List<StoryGroup> {
        val me = myId() ?: return emptyList()
        val stories = loadStories()
        if (stories.isEmpty()) return emptyList()
        pruneSeen(stories.map { it.id }.toSet())
        val seen = seenIds()

        val friendIds = try {
            FriendsRepository().loadFriends().map { it.id }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val userIds = stories.map { it.userId }.distinct()
        val profiles = try {
            client.from("pingchat_profiles").select {
                filter { isIn("id", userIds) }
            }.decodeList<Profile>().associateBy { it.id }
        } catch (e: Exception) {
            emptyMap()
        }

        return stories.groupBy { it.userId }
            .mapNotNull { (userId, rows) ->
                if (userId != me && !friendIds.contains(userId)) return@mapNotNull null
                val profile = profiles[userId] ?: return@mapNotNull null
                val sorted = rows.sortedByDescending { it.createdAt }
                StoryGroup(
                    user = profile,
                    stories = sorted,
                    isMine = userId == me,
                    allSeen = sorted.all { seen.contains(it.id) }
                )
            }
            .sortedWith(
                compareByDescending<StoryGroup> { it.isMine }
                    .thenByDescending { it.stories.firstOrNull()?.createdAt ?: "" }
            )
    }

    /** Delete one of MY stories: row + Storage file. Only the owner can. */
    suspend fun deleteStory(story: Story): Boolean {
        val me = myId() ?: return false
        if (story.userId != me) return false
        return try {
            client.from("pingchat_stories").delete {
                filter { eq("id", story.id) }
            }
            try {
                client.storage.from(STORY_BUCKET).delete(story.mediaPath)
            } catch (e: Exception) {
                // Row is gone; the file is swept by the expiry cleanup.
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Live story list: any INSERT / UPDATE / DELETE on pingchat_stories. */
    fun storiesFlow(): Flow<List<StoryGroup>> = callbackFlow {
        val scope = this
        val channel = try {
            RealtimeChannels.get("stories-feed") {
                client.realtime.channel("stories-feed")
            }.also {
                RealtimeChannels.ensureSubscribed("stories-feed")
            }
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        val job = scope.launch {
            merge(
                channel.postgresChangeFlow<PostgresAction.Insert>("public") {
                    table = "pingchat_stories"
                },
                channel.postgresChangeFlow<PostgresAction.Update>("public") {
                    table = "pingchat_stories"
                },
                channel.postgresChangeFlow<PostgresAction.Delete>("public") {
                    table = "pingchat_stories"
                }
            ).collect {
                trySend(loadStoryGroups())
            }
        }
        trySend(loadStoryGroups())
        awaitClose { job.cancel() }
    }

    /** Drop the cached stories channel (sign-out). */
    suspend fun dropChannel() = RealtimeChannels.drop("stories-feed")
}
