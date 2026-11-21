package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.AuthenticatorAssuranceLevel
import io.github.jan.supabase.auth.mfa.FactorType
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
     * ON in this project (v3.2.2), so sign-up sends a confirmation email
     * and this returns false until the user confirms — the caller shows
     * the verification screen. Returns true only when the user is signed
     * in immediately (confirmation off, or an already-confirmed address).
     *
     * [dob] is the ISO yyyy-MM-dd date of birth; it is stored in the
     * sign-up user metadata as "date_of_birth" and copied into the
     * pingchat_profiles row by [ensureProfile].
     *
     * The auth emails redirect to [AUTH_REDIRECT_URL], the app's deep
     * link (see AndroidManifest), so tapping the link opens the app
     * instead of a browser page.
     */
    suspend fun signUp(
        name: String,
        username: String,
        email: String,
        password: String,
        dob: String? = null
    ): Boolean {
        val uname = username.trim().lowercase()
        if (!isUsernameAvailable(uname)) {
            throw IllegalStateException("That username is already taken.")
        }
        auth.signUpWith(Email, redirectUrl = AUTH_REDIRECT_URL) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("full_name", name.trim())
                put("username", uname)
                if (!dob.isNullOrBlank()) put("date_of_birth", dob)
            }
        }
        return auth.currentSessionOrNull() != null
    }

    /**
     * Resend the signup confirmation email (v3.2.2 verification screen).
     * Supabase enforces a 60-second cooldown between sends — the caller
     * disables its resend button for 60s and shows the cooldown error
     * (see [friendlyError]) if the server still refuses.
     */
    suspend fun resendConfirmationEmail(email: String) {
        auth.resendEmail(
            type = OtpType.Email.SIGNUP,
            email = email.trim(),
            redirectUrl = AUTH_REDIRECT_URL
        )
    }

    /**
     * Import the session from an auth deep link (v3.2.2 deep-link fix).
     * Supabase puts the tokens in the URI fragment
     * (#access_token=...&refresh_token=...&type=recovery|signup), so the
     * caller parses them out of the intent data first. Verified against
     * supabase-kt 3.9.0: importAuthToken(accessToken, refreshToken,
     * retrieveUser) imports the session and (with a refresh token)
     * enables auto-refresh. SessionManager's sessionStatus collector
     * then flips isLoggedIn and routes to the app as usual.
     */
    suspend fun importSessionFromDeepLink(accessToken: String, refreshToken: String) {
        auth.importAuthToken(accessToken, refreshToken, retrieveUser = true)
    }

    /** Google sign-in via ID token (native). Needs the Google provider enabled in Supabase. */
    suspend fun signInWithGoogle(idToken: String) {
        auth.signInWith(IDToken) {
            provider = Google
            this.idToken = idToken
        }
    }

    suspend fun resetPassword(email: String) {
        // Explicit redirect so the reset link opens the app's deep link
        // (MainActivity handles it) instead of a browser page.
        auth.resetPasswordForEmail(email, redirectUrl = AUTH_REDIRECT_URL)
    }

    suspend fun updatePassword(newPassword: String) {
        auth.updateUser {
            password = newPassword
        }
    }

    /** Current sign-in email (Supabase Auth), shown in Privacy & Security. */
    fun currentLoginEmail(): String? = auth.currentUserOrNull()?.email

    /**
     * Change the sign-in email (v3.1.0 Privacy: Login Email).
     * Supabase sends a confirmation to the NEW address when email
     * confirmation is on; with confirmation OFF in this project the
     * change applies at once. Throws on an invalid or already
     * registered address — callers show friendlyError(e).
     */
    suspend fun updateLoginEmail(newEmail: String) {
        auth.updateUser {
            email = newEmail.trim()
        }
    }

    // ---------- Two-factor authentication (TOTP, v3.2.0) ----------
    // supabase-kt 3.9.0 auth-kt DOES expose MFA (auth.mfa):
    // enroll / createChallenge / verifyChallenge /
    // createChallengeAndVerify / unenroll / retrieveFactorsForCurrentUser
    // / getAuthenticatorAssuranceLevel / verifiedFactors / status.
    // Verified against the 3.9.0 sources (MfaApi.kt, FactorType.kt).
    // TOTP is the only factor type; no phone factor, no recovery codes.
    // NOTE: TOTP must also be enabled in the Supabase dashboard
    // (Auth > MFA) or enroll() throws — callers show friendlyError(e).

    /** One enrolled TOTP factor, ready to show as a QR code + secret. */
    data class MfaEnrollment(
        val factorId: String,
        /** Secret to type into the authenticator app by hand. */
        val secret: String,
        /** otpauth:// URI — render this as the QR code. */
        val qrUri: String
    )

    /** One verified TOTP factor on this account. */
    data class MfaFactorInfo(
        val id: String,
        val friendlyName: String?,
        val factorType: String
    )

    /**
     * Start TOTP enrollment. Returns the factor id + secret + otpauth
     * URI; the caller shows the QR code, the user scans it, then
     * [verifyTotpCode] confirms the setup. The unverified factor
     * expires after a few minutes, so enroll and verify on one screen.
     */
    suspend fun enrollTotpFactor(friendlyName: String = "PingChat"): MfaEnrollment {
        val factor = auth.mfa.enroll(
            factorType = FactorType.TOTP,
            friendlyName = friendlyName
        )
        return MfaEnrollment(
            factorId = factor.id,
            secret = factor.data.secret,
            qrUri = factor.data.uri
        )
    }

    /**
     * Confirm a TOTP code for [factorId] (enrollment, or the login
     * challenge). Creates a challenge, then verifies the code; on
     * success the session is promoted to AAL2. Deliberately the
     * explicit two-step call, not createChallengeAndVerify — the
     * latter defaults its channel to SMS, which is a phone-factor
     * concept and has no meaning for TOTP.
     */
    suspend fun verifyTotpCode(factorId: String, code: String) {
        val challenge = auth.mfa.createChallenge(factorId = factorId)
        auth.mfa.verifyChallenge(
            factorId = factorId,
            challengeId = challenge.id,
            code = code.trim()
        )
    }

    /** Verified TOTP factors on this account (empty when 2FA is off). */
    suspend fun listVerifiedMfaFactors(): List<MfaFactorInfo> {
        return try {
            auth.mfa.retrieveFactorsForCurrentUser()
                .filter { it.isVerified }
                .map { MfaFactorInfo(it.id, it.friendlyName, it.factorType) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** True when this account has a verified TOTP factor (2FA is on). */
    fun isMfaEnabled(): Boolean {
        return try {
            auth.mfa.status.enabled
        } catch (e: Exception) {
            false
        }
    }

    /**
     * True when the current session is only AAL1 but the account has a
     * verified factor, i.e. a TOTP code is still needed before the
     * session is fully trusted. The login screen uses this right after
     * sign-in: when true it must prompt for the code (see
     * [verifyLoginChallenge]) instead of entering the app.
     */
    fun needsMfaChallenge(): Boolean {
        return try {
            val (current, next) = auth.mfa.getAuthenticatorAssuranceLevel()
            current == AuthenticatorAssuranceLevel.AAL1 &&
                next == AuthenticatorAssuranceLevel.AAL2
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verify the login challenge: the 6-digit code from the user's
     * authenticator app, for their first verified factor. On success
     * the session is promoted to AAL2. Throws on a wrong code —
     * callers show friendlyError(e).
     */
    suspend fun verifyLoginChallenge(code: String) {
        val factor = listVerifiedMfaFactors().firstOrNull()
            ?: throw IllegalStateException("No two-factor method is set up on this account.")
        verifyTotpCode(factor.id, code)
    }

    /**
     * Turn 2FA off: verify a current TOTP code first (unenrolling a
     * verified factor requires an AAL2 session), then unenroll the
     * factor. Throws on a wrong code — callers show friendlyError(e).
     */
    suspend fun disableTotpFactor(factorId: String, code: String) {
        verifyTotpCode(factorId, code)
        auth.mfa.unenroll(factorId = factorId)
    }

    suspend fun signOut() {
        val uid = auth.currentUserOrNull()?.id
        try {
            if (uid != null) {
                // Take my presence offline and drop the push token mapping.
                Supabase.client.from("pingchat_profiles").update({
                    set("online", false)
                    set("last_seen", nowIso())
                    set("fcm_token", null as String?)
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
        // v3.2.2: date of birth from the sign-up metadata (ISO yyyy-MM-dd).
        val dateOfBirth = meta["date_of_birth"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        val inserted = try {
            client.from("pingchat_profiles").insert(
                buildJsonObject {
                    put("id", user.id)
                    put("username", username)
                    put("full_name", fullName)
                    if (avatar.isNotBlank()) put("avatar_url", avatar)
                    if (dateOfBirth != null) put("date_of_birth", dateOfBirth)
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
        /**
         * v3.2.2 deep link: where Supabase auth emails (signup
         * confirmation, password reset, resend) send the user back.
         * Must match the intent-filter in AndroidManifest.xml and the
         * Site URL / Redirect URLs in the Supabase dashboard.
         */
        const val AUTH_REDIRECT_URL = "com.mdhasibulislam.pingchat://login-callback"

        /** Short, friendly error messages for the auth screens. */
        fun friendlyError(e: Throwable): String {
            val m = e.message ?: "Something went wrong. Please try again."
            return when {
                m.contains("Invalid login credentials", ignoreCase = true) ->
                    "Incorrect email or password."
                m.contains("Email link is invalid", ignoreCase = true) ||
                    m.contains("link is invalid or has expired", ignoreCase = true) ||
                    m.contains("otp expired", ignoreCase = true) ||
                    m.contains("expired", ignoreCase = true) && m.contains("email", ignoreCase = true) ->
                    "This link has expired. Request a new one and try again."
                m.contains("Invalid MFA", ignoreCase = true) ||
                    m.contains("Invalid code", ignoreCase = true) ||
                    m.contains("invalid totp", ignoreCase = true) ||
                    m.contains("challenge", ignoreCase = true) && m.contains("expired", ignoreCase = true) ->
                    "That code is incorrect or has expired. Check your authenticator app and try again."
                m.contains("MFA", ignoreCase = true) && m.contains("not enabled", ignoreCase = true) ->
                    "Two-factor authentication is not turned on for this app yet."
                m.contains("AAL2", ignoreCase = true) ->
                    "Enter your authenticator code first, then try again."
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
                else -> "Something went wrong. Please try again."
            }
        }
    }
}
