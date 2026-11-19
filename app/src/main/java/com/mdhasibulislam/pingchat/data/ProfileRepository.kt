package com.mdhasibulislam.pingchat.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.ByteArrayOutputStream

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
            val compressed = if (ext.equals("gif", ignoreCase = true)) null else compressAvatar(bytes)
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
     * Downscale + JPEG-compress a picked avatar: longest side at most
     * 1024px, JPEG quality 85. Returns the compressed bytes, or null
     * when the image should be uploaded as-is (undecodable, has
     * transparency that JPEG cannot keep, or compression would not
     * make it any smaller). Android framework APIs only — no new
     * dependencies.
     */
    private fun compressAvatar(bytes: ByteArray): ByteArray? {
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
            val scale = if (longest > 1024) 1024f / longest else 1f
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
}
