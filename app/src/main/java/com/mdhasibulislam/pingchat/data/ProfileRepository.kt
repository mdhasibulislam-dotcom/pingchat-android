package com.mdhasibulislam.pingchat.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** My profile: view, edit, photo upload — on Supabase PostgREST + Storage. */
class ProfileRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun getProfile(userId: String): Profile? {
        return try {
            client.from("pingchat_profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateProfile(userId: String, fullName: String, bio: String?, phone: String?, isPublic: Boolean): Profile? {
        return try {
            client.from("pingchat_profiles").update(
                {
                    set("full_name", fullName)
                    set("bio", bio)
                    set("phone", phone)
                    set("is_public", isPublic)
                }
            ) {
                filter { eq("id", userId) }
                select()
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * v3.1.0 extended profile fields (migration-v310-profile.sql): city,
     * education, status line, social links, theme colour. Cover photo is
     * set separately via [setCoverUrl] after [uploadCover]. Null clears a
     * text field; a null [themeColor] clears it ("" is treated the same).
     * [themeColor] must be null or "#RRGGBB" — the DB check enforces it.
     */
    suspend fun updateProfileExtended(
        userId: String,
        city: String?,
        education: String?,
        statusLine: String?,
        socialLinks: SocialLinks?,
        themeColor: String?
    ): Profile? {
        return try {
            client.from("pingchat_profiles").update(
                {
                    set("city", city)
                    set("education", education)
                    set("status_line", statusLine)
                    set("social_links", socialLinks ?: SocialLinks())
                    set("theme_color", themeColor?.ifBlank { null })
                }
            ) {
                filter { eq("id", userId) }
                select()
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Upload a cover photo to the public 'covers' bucket at
     * <userId>/<timestamp>.<ext> and return its public URL. Covers are
     * wide images, so they are downscaled to a 1600px longest side
     * (avatars use 1024px) and re-encoded as JPEG quality 85. Same
     * GIF/transparency rules as [uploadAvatar]. Returns null on failure.
     */
    suspend fun uploadCover(userId: String, bytes: ByteArray, ext: String, contentType: String): String? {
        return try {
            val compressed = if (ext.equals("gif", ignoreCase = true)) null else compressImage(bytes, 1600)
            val uploadBytes = compressed ?: bytes
            val uploadExt = if (compressed != null) "jpg" else ext
            val uploadContentType = if (compressed != null) "image/jpeg" else contentType
            val path = "$userId/${System.currentTimeMillis()}.$uploadExt"
            client.storage.from("covers").upload(path, uploadBytes) {
                this.contentType = ContentType.parse(uploadContentType)
            }
            client.storage.from("covers").publicUrl(path)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setCoverUrl(userId: String, url: String): Profile? {
        return try {
            client.from("pingchat_profiles").update(
                { set("cover_photo_url", url) }
            ) {
                filter { eq("id", userId) }
                select()
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    // ---------- v3.1.0 public profile: mutual friends + visitors ----------

    /**
     * Mutual friends with another user (my accepted friends ∩ theirs),
     * via the pingchat_mutual_friends RPC — the client cannot compute this
     * itself because the friendships RLS policy only exposes rows the
     * caller is a party to. Returns empty on failure.
     */
    suspend fun loadMutualFriends(otherUserId: String): List<Profile> {
        return try {
            client.postgrest.rpc(
                "pingchat_mutual_friends",
                buildJsonObject { put("p_user_id", otherUserId) }
            ).decodeList<Profile>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Record that I viewed someone's profile (pingchat_record_profile_view
     * RPC). The server ignores self-views and refreshes viewed_at on
     * repeat views. Best-effort: never throws.
     */
    suspend fun recordProfileView(profileUserId: String) {
        try {
            client.postgrest.rpc(
                "pingchat_record_profile_view",
                buildJsonObject { put("p_profile_user_id", profileUserId) }
            )
        } catch (e: Exception) {
            // views are best-effort
        }
    }

    /** How many distinct users viewed this profile (RPC; 0 on failure). */
    suspend fun profileViewCount(profileUserId: String): Long {
        return try {
            client.postgrest.rpc(
                "pingchat_profile_view_count",
                buildJsonObject { put("p_profile_user_id", profileUserId) }
            ).decodeAs<Long>()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Most recent viewers of MY profile, with their profiles. Only the
     * profile owner can read these rows (RLS) — calling this for someone
     * else's profile returns empty. Returns empty on failure.
     */
    suspend fun loadRecentViewers(profileUserId: String, limit: Int = 10): List<ProfileViewerItem> {
        return try {
            val views = client.from("pingchat_profile_views").select {
                filter { eq("profile_user_id", profileUserId) }
                order("viewed_at", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<ProfileView>()
            if (views.isEmpty()) return emptyList()
            val profiles = client.from("pingchat_profiles").select {
                filter { isIn("id", views.map { it.viewerUserId }) }
            }.decodeList<Profile>().associateBy { it.id }
            views.map { view -> ProfileViewerItem(view, profiles[view.viewerUserId]) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Upload a profile photo to the public 'avatars' bucket at
     * <userId>/<timestamp>.<ext> and return its public URL.
     * Before uploading, the image is downscaled (longest side at most
     * 1024px) and re-encoded as JPEG quality 85, so a ~2 MB photo no
     * longer fails against the bucket limit. When compression applies,
     * the stored file is a .jpg regardless of the picked format.
     * Returns null on failure.
     */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, ext: String, contentType: String): String? {
        return try {
            // Animated GIFs keep their animation (decoding would flatten
            // them to a single frame); anything else is compressed when
            // it helps. A null result means "upload the original bytes".
            val compressed = if (ext.equals("gif", ignoreCase = true)) null else compressImage(bytes, 1024)
            val uploadBytes = compressed ?: bytes
            val uploadExt = if (compressed != null) "jpg" else ext
            val uploadContentType = if (compressed != null) "image/jpeg" else contentType
            val path = "$userId/${System.currentTimeMillis()}.$uploadExt"
            client.storage.from("avatars").upload(path, uploadBytes) {
                this.contentType = ContentType.parse(uploadContentType)
            }
            client.storage.from("avatars").publicUrl(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downscale + JPEG-compress a picked image: longest side at most
     * [maxSide] px, JPEG quality 85. Avatars use 1024, covers use 1600.
     * Returns the compressed bytes, or null when the image should be
     * uploaded as-is (undecodable, has transparency that JPEG cannot keep,
     * or compression would not make it any smaller). Android framework
     * APIs only — no new dependencies.
     */
    private fun compressImage(bytes: ByteArray, maxSide: Int): ByteArray? {
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            // JPEG has no alpha channel — a transparent image would come
            // out with a black background, so leave those untouched.
            if (bitmap.hasAlpha()) {
                bitmap.recycle()
                return null
            }
            val longest = maxOf(bitmap.width, bitmap.height)
            val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
            val targetWidth = maxOf(1, (bitmap.width * scale).toInt())
            val targetHeight = maxOf(1, (bitmap.height * scale).toInt())
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            val compressed = out.toByteArray()
            // Never upload something bigger than the original.
            if (compressed.size < bytes.size) compressed else null
        } catch (_: Exception) {
            try {
                bitmap.recycle()
            } catch (_: Exception) {
            }
            null
        }
    }

    suspend fun setAvatarUrl(userId: String, url: String): Profile? {
        return try {
            client.from("pingchat_profiles").update(
                { set("avatar_url", url) }
            ) {
                filter { eq("id", userId) }
                select()
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Remove the profile photo (v3.2.0): set avatar_url to NULL in
     * Supabase and return the updated row, so the photo is really gone
     * (the Avatar component falls back to initials). The old Storage
     * file is left in place — it is no longer referenced anywhere.
     */
    suspend fun clearAvatarUrl(userId: String): Profile? {
        return try {
            client.from("pingchat_profiles").update(
                { set("avatar_url", null as String?) }
            ) {
                filter { eq("id", userId) }
                select()
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }
}
