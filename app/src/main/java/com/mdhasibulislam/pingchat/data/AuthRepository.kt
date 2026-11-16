package com.mdhasibulislam.pingchat.data

import com.onesignal.OneSignal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Authentication + profile bootstrap. Mirrors js/auth.js. */
class AuthRepository {
    private val auth get() = Supabase.client.auth

    fun currentUser(): UserInfo? = auth.currentUserOrNull()
    fun isSignedIn(): Boolean = currentUser() != null

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /**
     * Returns true when the account is signed in immediately (email
     * confirmation off in Supabase), false when a confirmation email was
     * sent and the user must confirm first.
     */
    suspend fun signUp(name: String, username: String, email: String, password: String): Boolean {
        val result = auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("full_name", name)
                put("username", username)
            }
        }
        return result == null
    }

    /** Google sign-in via ID token (native). Needs GOOGLE_WEB_CLIENT_ID set. */
    suspend fun signInWithGoogle(idToken: String) {
        auth.signInWith(IDToken) {
            provider = Google
            this.idToken = idToken
        }
    }

    suspend fun resetPassword(email: String) {
        auth.resetPasswordForEmail(email)
    }

    suspend fun updatePassword(newPassword: String) {
        auth.updateUser {
            password = newPassword
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // ignore — local session is cleared anyway
        }
        PushHelper.logout()
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        return try {
            Supabase.client.postgrest.rpc(
                "is_username_available",
                buildJsonObject { put("p_username", username) }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Make sure the signed-in user has a profile row. The SQL trigger
     * handle_new_user() normally creates it; this is the fallback
     * (same logic as ensureProfile() in the web app, incl. Google avatar
     * backfill and username derivation from the email prefix).
     */
    suspend fun ensureProfile(user: UserInfo): Profile? {
        val client = Supabase.client
        val existing = try {
            client.from("profiles").select {
                filter { eq("id", user.id) }
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
        val meta: JsonObject = user.userMetadata ?: buildJsonObject {}
        val avatar = meta["avatar_url"]?.jsonPrimitive?.contentOrNull
            ?: meta["picture"]?.jsonPrimitive?.contentOrNull
            ?: ""
        if (existing != null) {
            if (existing.avatarUrl.isNullOrBlank() && avatar.isNotBlank()) {
                return try {
                    client.from("profiles").update(
                        { set("avatar_url", avatar) }
                    ) {
                        filter { eq("id", user.id) }
                        select()
                    }.decodeSingleOrNull<Profile>() ?: existing
                } catch (e: Exception) {
                    existing
                }
            }
            return existing
        }
        val fullName = meta["full_name"]?.jsonPrimitive?.contentOrNull
            ?: meta["name"]?.jsonPrimitive?.contentOrNull
            ?: user.email?.substringBefore("@")
            ?: ""
        val username = usernameForNewUser(user, meta)
        val inserted = try {
            client.from("profiles").insert(
                buildJsonObject {
                    put("id", user.id)
                    put("username", username)
                    put("full_name", fullName)
                    if (avatar.isNotBlank()) put("avatar_url", avatar)
                }
            ) { select() }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
        if (inserted != null) return inserted
        // Race with the trigger — re-select before giving up.
        return try {
            client.from("profiles").select {
                filter { eq("id", user.id) }
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun usernameForNewUser(user: UserInfo, meta: JsonObject): String {
        val usernameRe = Regex("^[a-z0-9_]{3,20}$")
        val metaName = meta["username"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
        if (usernameRe.matches(metaName)) return metaName
        val base = (user.email ?: "").substringBefore("@").lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(16)
        if (base.length >= 3) {
            if (isUsernameAvailable(base)) return base
            val suffixed = (base + "_" + (1000..9999).random()).take(20)
            if (usernameRe.matches(suffixed) && isUsernameAvailable(suffixed)) return suffixed
        }
        return "user_" + user.id.replace("-", "").take(10)
    }

    companion object {
        /** Same friendly messages as the web app's friendlyError(). */
        fun friendlyError(e: Throwable): String {
            val m = e.message ?: "Something went wrong. Please try again."
            return when {
                m.contains("Invalid login credentials", ignoreCase = true) ->
                    "Incorrect email or password."
                m.contains("Email not confirmed", ignoreCase = true) ->
                    "Please confirm your email first — check your inbox."
                m.contains("already registered", ignoreCase = true) ->
                    "This email is already registered. Try logging in."
                m.contains("rate limit", ignoreCase = true) || m.contains("too many", ignoreCase = true) ->
                    "Too many attempts. Please wait a minute and try again."
                m.contains("Failed to fetch", ignoreCase = true) || m.contains("NetworkError", ignoreCase = true) ->
                    "Network problem. Check your connection and try again."
                m.contains("duplicate key", ignoreCase = true) || m.contains("23505", ignoreCase = true) ->
                    "That username is already taken."
                m.contains("provider", ignoreCase = true) && m.contains("not enabled", ignoreCase = true) ->
                    "Google sign-in is not turned on for this app yet. Please log in with email instead."
                m.contains("Database error saving new user", ignoreCase = true) ->
                    "Could not create your account. The username may already be taken."
                else -> m
            }
        }
    }
}

/** OneSignal calls, guarded so push can never break the app. */
object PushHelper {
    fun login(userId: String, username: String?) {
        try {
            OneSignal.login(userId)
            if (!username.isNullOrBlank()) OneSignal.User.addTag("username", username)
        } catch (e: Exception) {
            // push is best-effort
        }
    }

    fun logout() {
        try {
            OneSignal.logout()
        } catch (e: Exception) {
            // ignore
        }
    }
}
