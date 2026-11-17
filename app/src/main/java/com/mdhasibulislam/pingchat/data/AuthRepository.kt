package com.mdhasibulislam.pingchat.data

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Authentication + profile bootstrap on Firebase Auth + Firestore. */
class AuthRepository {
    private val auth get() = FirebaseDb.auth
    private val db get() = FirebaseDb.db

    fun currentUser(): FirebaseUser? = auth.currentUser
    fun isSignedIn(): Boolean = currentUser() != null

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    /**
     * Creates the account and its users/{uid} profile doc. Firebase does
     * not require email confirmation to sign in, so this always returns
     * true (signed in immediately) on success.
     */
    suspend fun signUp(name: String, username: String, email: String, password: String): Boolean {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: throw IllegalStateException("Could not create your account.")
        // The username check needs a signed-in user: the Firestore rules
        // only allow reads to signed-in users, so checking before sign-up
        // is always denied — and the old code reported that denial as
        // "already taken", which blocked every username. Check here, now
        // that this account is signed in.
        val available = try {
            db.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()
                .isEmpty
        } catch (e: Exception) {
            // The check itself failed (e.g. network) — roll the new
            // account back so the user can retry cleanly, and report the
            // real error instead of "taken".
            try { user.delete().await() } catch (ignored: Exception) { /* best effort */ }
            throw e
        }
        if (!available) {
            try { user.delete().await() } catch (ignored: Exception) { /* best effort */ }
            throw IllegalStateException("That username is already taken.")
        }
        val now = nowMillis()
        try {
            db.collection("users").document(user.uid).set(
                mapOf(
                    "username" to username,
                    "fullName" to name,
                    "bio" to null,
                    "avatarUrl" to "",
                    "isPublic" to true,
                    "online" to false,
                    "lastSeen" to now,
                    "createdAt" to now,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            try { user.delete().await() } catch (ignored: Exception) { /* best effort */ }
            throw e
        }
        return true
    }

    /** Google sign-in via ID token -> Firebase credential. */
    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun updatePassword(newPassword: String) {
        val user = auth.currentUser ?: throw IllegalStateException("You are not signed in.")
        user.updatePassword(newPassword).await()
    }

    suspend fun signOut() {
        val uid = auth.currentUser?.uid
        try {
            if (uid != null) {
                // Take my presence offline and drop the push token mapping.
                db.collection("users").document(uid).update(
                    mapOf(
                        "online" to false,
                        "lastSeen" to nowMillis(),
                        "fcmToken" to FieldValue.delete()
                    )
                ).await()
            }
        } catch (e: Exception) {
            // sign-out must never fail on a best-effort write
        }
        auth.signOut()
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        return try {
            val snap = db.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()
            snap.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Make sure the signed-in user has a users/{uid} doc. For Google
     * sign-ins this also backfills the Google display name / photo and
     * derives a username from the email prefix when no doc exists yet.
     */
    suspend fun ensureProfile(user: FirebaseUser): Profile? {
        val ref = db.collection("users").document(user.uid)
        val snap = try {
            ref.get().await()
        } catch (e: Exception) {
            null
        }
        val avatar = user.photoUrl?.toString().orEmpty()
        if (snap != null && snap.exists()) {
            val existing = profileFromDoc(user.uid, snap.data ?: emptyMap())
            if (existing.avatarUrl.isNullOrBlank() && avatar.isNotBlank()) {
                return try {
                    ref.update("avatarUrl", avatar).await()
                    existing.copy(avatarUrl = avatar)
                } catch (e: Exception) {
                    existing
                }
            }
            return existing
        }
        val fullName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: ""
        val username = usernameForNewUser(user)
        val now = nowMillis()
        val data = mutableMapOf<String, Any?>(
            "username" to username,
            "fullName" to fullName,
            "bio" to null,
            "avatarUrl" to avatar,
            "isPublic" to true,
            "online" to false,
            "lastSeen" to now,
            "createdAt" to now,
            "updatedAt" to now
        )
        return try {
            ref.set(data).await()
            profileFromDoc(user.uid, data)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun usernameForNewUser(user: FirebaseUser): String {
        val usernameRe = Regex("^[a-z0-9_]{3,20}$")
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
        return "user_" + user.uid.replace("-", "").take(10)
    }

    companion object {
        /** Short, friendly error messages for the auth screens. */
        fun friendlyError(e: Throwable): String {
            return when (e) {
                is FirebaseAuthInvalidCredentialsException ->
                    "Incorrect email or password."
                is FirebaseAuthInvalidUserException ->
                    "No account with this email. Try registering."
                is FirebaseAuthUserCollisionException ->
                    "This email is already registered. Try logging in."
                is FirebaseAuthWeakPasswordException ->
                    "Password must be at least 8 characters."
                else -> {
                    val m = e.message ?: "Something went wrong. Please try again."
                    when {
                        m.contains("network", ignoreCase = true) ||
                            m.contains("unavailable", ignoreCase = true) ->
                            "Network problem. Check your connection and try again."
                        m.contains("PERMISSION_DENIED", ignoreCase = true) ->
                            "Permission denied — the Firestore rules may not be published yet."
                        m.contains("too many requests", ignoreCase = true) ->
                            "Too many attempts. Please wait a minute and try again."
                        m.contains("not friends", ignoreCase = true) ->
                            "You need to be friends first."
                        else -> m.lineSequence().firstOrNull()?.take(140)
                            ?: "Something went wrong. Please try again."
                    }
                }
            }
        }
    }
}
