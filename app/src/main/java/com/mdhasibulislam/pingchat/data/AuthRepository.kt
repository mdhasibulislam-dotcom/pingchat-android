package com.mdhasibulislam.pingchat.data

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

/** Authentication + profile bootstrap on Supabase Auth (GoTrue) + PostgREST. */
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
     * Login with an email OR a username. A username has no "@" — it is
     * resolved to its sign-in email first (RPC pingchat_email_for_username,
     * see database/migration-username-login.sql). No phone-number login:
     * phone auth is deliberately not offered.
     */
    suspend fun signInWithUsernameOrEmail(login: String, password: String) {
        val email = if ("@" in login) {
            login.trim()
        } else {
            emailForUsername(login.trim().lowercase())
                ?: throw IllegalStateException(
                    "No account with that username. Check the spelling, or log in with your email."
                )
        }
        signIn(email, password)
    }

    /** The sign-in email for a username, or null when there is none. */
    suspend fun emailForUsername(username: String): String? {
        return try {
            Supabase.client.postgrest.rpc(
                "pingchat_email_for_username",
                buildJsonObject { put("p_username", username) }
            ).decodeAs<String?>()
        } catch (e: Exception) {
            // The RPC is missing (migration not run yet) — username login
            // cannot work until it is. Say so plainly instead of
            // pretending the username does not exist.
            val m = e.message ?: ""
            if (m.contains("pingchat_email_for_username", ignoreCase = true) ||
                m.contains("Could not find the function", ignoreCase = true) ||
                m.contains("404", ignoreCase = true)
            ) {
                throw IllegalStateException(
                    "Username login is not set up yet — please log in with your email for now."
                )
            }
            null
        }
    }

    /**
     * Creates the account and its pingchat_profiles row. "Confirm email" is
     * OFF in this project, so sign-up signs the user in immediately and this
     * returns true. Returns false only when a confirmation email was sent
     * and the user must confirm first (if the setting is ever turned on).
     */
    suspend fun signUp(name: String, username: String, email: String, password: String): Boolean {
        val uname = username.trim().lowercase()
        if (!isUsernameAvailable(uname)) {
            throw IllegalStateException("That username is already taken.")
        }
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("full_name", name.trim())
                put("username", uname)
            }
        }
        return auth.currentSessionOrNull() != null
    }

    /** Google sign-in via ID token (native). Needs the Google provider enabled in Supabase. */
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
        val uid = auth.currentUserOrNull()?.id
        try {
            if (uid != null) {
                // Take my presence offline and drop the push token mapping.
                Supabase.client.from("pingchat_profiles").update({
                    set("online", false)
                    set("last_seen", nowIso())
                    set("fcm_token", null)
                }) {
                    filter { eq("id", uid) }
                }
            }
        } catch (e: Exception) {
            // sign-out must never fail on a best-effort write
        }
        try {
            auth.signOut()
        } catch (e: Exception) {
            // ignore — local session is cleared anyway
        }
    }

    /** Username check via RPC — works before sign-up (granted to anon too). */
    suspend fun isUsernameAvailable(username: String): Boolean {
        return try {
            Supabase.client.postgrest.rpc(
                "pingchat_is_username_available",
                buildJsonObject { put("p_username", username) }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Make sure the signed-in user has a pingchat_profiles row. For Google
     * sign-ins this also backfills the Google display name / photo and
     * derives a username from the email prefix when no row exists yet.
     * (There is no auto-create trigger on purpose — a trigger would also
     * create PingChat rows for the exam app's users, who share this project.)
     */
    suspend fun ensureProfile(user: UserInfo): Profile? {
        val client = Supabase.client
        val existing = try {
            client.from("pingchat_profiles").select {
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
                    client.from("pingchat_profiles").update(
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
            client.from("pingchat_profiles").insert(
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
        // Race with another sign-in — re-select before giving up.
        return try {
            client.from("pingchat_profiles").select {
                filter { eq("id", user.id) }
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun usernameForNewUser(user: UserInfo, meta: JsonObject): String {
        val usernameRe = Regex("^[a-z0-9_]{3,20}$")
        val metaName = meta["username"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
        if (usernameRe.matches(metaName) && isUsernameAvailable(metaName)) return metaName
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
        /** Short, friendly error messages for the auth screens. */
        fun friendlyError(e: Throwable): String {
            val m = e.message ?: "Something went wrong. Please try again."
            return when {
                m.contains("Invalid login credentials", ignoreCase = true) ->
                    "Incorrect email or password."
                m.contains("Email not confirmed", ignoreCase = true) ->
                    "Please confirm your email first — check your inbox."
                m.contains("already registered", ignoreCase = true) ||
                    m.contains("User already registered", ignoreCase = true) ->
                    "This email is already registered. Try logging in."
                m.contains("rate limit", ignoreCase = true) ||
                    m.contains("too many", ignoreCase = true) ->
                    "Too many attempts. Please wait a minute and try again."
                m.contains("Failed to fetch", ignoreCase = true) ||
                    m.contains("NetworkError", ignoreCase = true) ||
                    m.contains("Unable to resolve host", ignoreCase = true) ->
                    "Network problem. Check your connection and try again."
                m.contains("duplicate key", ignoreCase = true) ||
                    m.contains("23505", ignoreCase = true) ->
                    "That username is already taken."
                m.contains("not friends", ignoreCase = true) ->
                    "You need to be friends first."
                m.contains("provider", ignoreCase = true) &&
                    m.contains("not enabled", ignoreCase = true) ->
                    "Google sign-in is not turned on for this app yet. Please log in with email instead."
                m.contains("Database error saving new user", ignoreCase = true) ->
                    "Could not create your account. The username may already be taken."
                else -> m.lineSequence().firstOrNull()?.take(140)
                    ?: "Something went wrong. Please try again."
            }
        }
    }
}
