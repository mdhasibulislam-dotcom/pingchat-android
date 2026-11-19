package com.mdhasibulislam.pingchat.data

import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.storage.Storage
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Supabase backend for v3.0.0 — the NEW project "hasibulialam's Project"
 * (tjsalnhtuqqigyhvgpnb), shared with the MCQ exam app. PingChat tables are
 * all prefixed `pingchat_` (see database/schema-supabase-v3.sql); the exam
 * app's tables are never touched.
 *
 * Only the PUBLISHABLE key lives here; it is safe in client code because
 * every table is protected by Row Level Security. NEVER put a secret /
 * service_role key in this project.
 *
 * Push RECEIVING still uses Firebase Cloud Messaging (firebase-messaging +
 * app/google-services.json, project ping-chat-fcda6) — FCM only, no other
 * Firebase service. Push SENDING goes through the send-push Edge Function
 * (see PushSender.kt).
 */
object Supabase {
    const val URL = "https://tjsalnhtuqqigyhvgpnb.supabase.co"

    /** Supabase publishable key — public client config, safe to embed. */
    const val ANON_KEY = "sb_publishable__lkX_8sN9yDzjoJFwd_A9A_2zOiaA0f"

    /**
     * Google Sign-In needs the OAuth *Web* client ID — the same Web client
     * already in app/google-services.json (Firebase project ping-chat-fcda6,
     * type 3 oauth_client). The Supabase Auth Google provider must be
     * configured with this client (ID + secret) in the Supabase dashboard;
     * the callback is https://tjsalnhtuqqigyhvgpnb.supabase.co/auth/v1/callback.
     * A client ID is public config, not a secret.
     */
    const val GOOGLE_WEB_CLIENT_ID =
        "103915029688-riiqgn5o2a5sa6goq5tjtr0c3nulvak4.apps.googleusercontent.com"

    val client by lazy {
        createSupabaseClient(URL, ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    /**
     * Wait until the restored session is known (cold process, e.g. the
     * notification-reply receiver). Returns true when signed in.
     * GoTrue restores the saved session asynchronously, so currentUserOrNull()
     * alone can be null for a moment after the app starts.
     */
    suspend fun awaitSignedIn(timeoutMs: Long = 8000): Boolean {
        return try {
            withTimeoutOrNull(timeoutMs) {
                client.auth.sessionStatus.first {
                    it is SessionStatus.Authenticated ||
                        it is SessionStatus.NotAuthenticated ||
                        it is SessionStatus.RefreshFailure
                }
                client.auth.currentSessionOrNull() != null
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** This device's FCM token (null when Firebase Messaging has none yet). */
    suspend fun fcmToken(): String? {
        return try {
            kotlinx.coroutines.tasks.await(FirebaseMessaging.getInstance().token)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * One Realtime channel per name, subscribed once. Supabase channels are
 * cheap, but creating a second channel with the same name (or subscribing
 * twice) drops events, so every repository goes through here.
 */
internal object RealtimeChannels {
    private val channels = mutableMapOf<String, RealtimeChannel>()
    private val subscribed = mutableSetOf<String>()
    private val mutex = Mutex()

    suspend fun get(name: String, create: () -> RealtimeChannel): RealtimeChannel {
        return mutex.withLock { channels.getOrPut(name, create) }
    }

    /** Subscribe once (10s timeout); later calls for the same name are no-ops. */
    suspend fun ensureSubscribed(name: String, block: Boolean = false) {
        val channel = mutex.withLock { channels[name] } ?: return
        val first = mutex.withLock { subscribed.add(name) }
        if (!first) return
        try {
            withTimeoutOrNull(10_000) { channel.subscribe(block) }
        } catch (e: Exception) {
            mutex.withLock { subscribed.remove(name) }
        }
    }

    suspend fun drop(name: String) {
        val channel = mutex.withLock {
            subscribed.remove(name)
            channels.remove(name)
        }
        if (channel != null) {
            try {
                channel.unsubscribe()
            } catch (e: Exception) {
            }
        }
    }

    suspend fun dropPrefix(prefix: String) {
        val names = mutex.withLock { channels.keys.filter { it.startsWith(prefix) } }
        names.forEach { drop(it) }
    }

    suspend fun dropAll() {
        val names = mutex.withLock { channels.keys.toList() }
        names.forEach { drop(it) }
    }
}

/** The models keep ISO strings; these convert epoch millis when needed. */
fun millisToIso(millis: Long?): String? =
    millis?.let { Instant.ofEpochMilli(it).toString() }

fun millisToIsoOrEmpty(millis: Long?): String =
    millis?.let { Instant.ofEpochMilli(it).toString() } ?: ""

fun nowMillis(): Long = System.currentTimeMillis()

fun nowIso(): String = Instant.now().toString()
