package com.mdhasibulislam.pingchat.data

import android.content.Context
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.storage.ktx.storage
import java.time.Instant

/**
 * Shared Firebase instances for the v2.0.0 backend (project ping-chat-fcda6).
 * Config comes from app/google-services.json — public client config, safe to
 * commit. There are no secret keys in this app: Firestore + Storage are
 * protected by firestore.rules / storage.rules, never by a key.
 *
 * The web app stays on its own backend; this Android app is a SEPARATE Firebase
 * system (separate accounts, chats and calls — no cross-platform data).
 */
object FirebaseDb {
    val auth get() = Firebase.auth
    val db get() = Firebase.firestore
    val storage get() = Firebase.storage
    val messaging get() = Firebase.messaging

    /** Storage bucket from google-services.json. */
    const val STORAGE_BUCKET = "ping-chat-fcda6.firebasestorage.app"
}

/**
 * Google Sign-In needs the OAuth *Web* client ID. The google-services Gradle
 * plugin generates it as the string resource `default_web_client_id` — but
 * ONLY when the Firebase console has the Google provider enabled, which
 * puts a Web OAuth client into google-services.json. Right now
 * oauth_client is EMPTY, so this returns null and the Google button shows
 * a friendly "not turned on yet" message instead of crashing.
 * NEVER invent a client ID — enable Google in the console and re-download
 * google-services.json (see README.md).
 */
object FirebaseConfig {
    fun googleWebClientId(context: Context): String? {
        return try {
            val resId = context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
            if (resId == 0) null
            else context.getString(resId).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}

/** Firestore stores timestamps as epoch millis; the models keep ISO strings. */
fun millisToIso(millis: Long?): String? =
    millis?.let { Instant.ofEpochMilli(it).toString() }

fun millisToIsoOrEmpty(millis: Long?): String =
    millis?.let { Instant.ofEpochMilli(it).toString() } ?: ""

fun nowMillis(): Long = System.currentTimeMillis()

fun nowIso(): String = Instant.now().toString()
