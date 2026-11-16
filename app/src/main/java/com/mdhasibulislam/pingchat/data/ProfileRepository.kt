package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

/** My profile: view, edit, photo upload. Mirrors js/profile.js + js/settings.js. */
class ProfileRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun getProfile(userId: String): Profile? {
        return try {
            client.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateProfile(userId: String, fullName: String, bio: String?, isPublic: Boolean): Profile? {
        return try {
            client.from("profiles").update(
                {
                    set("full_name", fullName)
                    set("bio", bio)
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
     * <userId>/<timestamp>.<ext> (same layout as the web app) and return
     * its public URL. Returns null on failure.
     */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, ext: String, contentType: String): String? {
        return try {
            val path = "$userId/${System.currentTimeMillis()}.$ext"
            client.storage.from("avatars").upload(path, bytes) {
                this.contentType = ContentType.parse(contentType)
            }
            client.storage.from("avatars").publicUrl(path)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setAvatarUrl(userId: String, url: String): Profile? {
        return try {
            client.from("profiles").update(
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
