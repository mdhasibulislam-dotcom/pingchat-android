package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Calls: the `calls` table + the signaling channel. Mirrors js/calls.js
 * and js/call-config.js (same ICE servers, same statuses
 * ringing/accepted/declined/missed/ended, same broadcast events).
 */
class CallRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    companion object {
        /** 30s ring, then the call is marked missed (web: CALL_TIMEOUT_MS). */
        const val CALL_TIMEOUT_MS = 30_000L

        /**
         * ICE servers — identical to the web app (js/call-config.js):
         * Google free STUN + the free OpenRelay TURN relay, so calls work
         * between two phones on mobile data (carrier NAT), where STUN
         * alone cannot carry media.
         */
        val ICE_SERVERS = listOf(
            IceServer("stun:stun.l.google.com:19302"),
            IceServer("stun:stun1.l.google.com:19302"),
            IceServer("stun:stun2.l.google.com:19302"),
            IceServer("stun:stun3.l.google.com:19302"),
            IceServer("stun:stun4.l.google.com:19302"),
            IceServer("stun:openrelay.metered.ca:80"),
            IceServer("turn:openrelay.metered.ca:80", "openrelayproject", "openrelayproject"),
            IceServer("turn:openrelay.metered.ca:80?transport=tcp", "openrelayproject", "openrelayproject"),
            IceServer("turn:openrelay.metered.ca:443", "openrelayproject", "openrelayproject"),
            IceServer("turns:openrelay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject")
        )
    }

    /** One ICE server entry (urls + optional TURN username/credential). */
    data class IceServer(val urls: String, val username: String? = null, val credential: String? = null)

    // ---------- calls table ----------

    /** Insert the ringing call row (the caller creates it, like the web app). */
    suspend fun insertCall(
        callId: String,
        conversationId: String?,
        callerId: String,
        calleeId: String,
        kind: String
    ): CallRow? {
        return try {
            client.from("calls").insert(
                buildJsonObject {
                    put("id", callId)
                    if (conversationId != null) put("conversation_id", conversationId)
                    put("caller_id", callerId)
                    put("callee_id", calleeId)
                    put("kind", kind)
                    put("status", "ringing")
                }
            ) { select() }.decodeSingleOrNull<CallRow>()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateCall(callId: String, patch: Map<String, String?>) {
        try {
            client.from("calls").update(
                {
                    patch.forEach { (key, value) ->
                        if (value != null) set(key, value)
                    }
                }
            ) { filter { eq("id", callId) } }
        } catch (e: Exception) {
            // call history is best-effort; the call itself still works
        }
    }

    /** Callee accepted: status accepted + started_at (web: acceptCall). */
    suspend fun markAccepted(callId: String) {
        updateCall(callId, mapOf("status" to "accepted", "started_at" to Instant.now().toString()))
    }

    /** Call declined (manual decline, or the busy auto-decline). */
    suspend fun markDeclined(callId: String) {
        updateCall(callId, mapOf("status" to "declined"))
    }

    /** Nobody answered within 30s. */
    suspend fun markMissed(callId: String) {
        updateCall(callId, mapOf("status" to "missed"))
    }

    /** Call finished: status ended + ended_at + duration in seconds. */
    suspend fun markEnded(callId: String, durationSeconds: Int?) {
        val patch = mutableMapOf<String, String?>(
            "status" to "ended",
            "ended_at" to Instant.now().toString()
        )
        if (durationSeconds != null) patch["duration_seconds"] = durationSeconds.toString()
        updateCall(callId, patch)
    }

    /** The other side's profile (for the incoming-call screen). */
    suspend fun loadProfile(userId: String): Profile? {
        return try {
            client.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    // ---------- signaling transport (mirrors js/calls.js peerChannel/sendSignal) ----------

    /** My own channel: I receive call-offer/answer/candidates on `calls:<myId>`. */
    fun myCallsChannel(myId: String) = client.realtime.channel("calls:$myId")

    private val peerChannels = mutableMapOf<String, RealtimeChannel>()

    /**
     * The peer's channel, subscribed before use. subscribe() suspends until
     * the channel is joined (or fails) — the same wait the web app does
     * explicitly for SUBSCRIBED, so the first signals are not lost on a
     * fresh channel. A failed channel is never cached.
     */
    private suspend fun peerChannel(peerId: String): RealtimeChannel? {
        peerChannels[peerId]?.let { return it }
        val ch = client.realtime.channel("calls:$peerId")
        val ok = withTimeoutOrNull(10_000) {
            try {
                ch.subscribe()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
        if (!ok) {
            peerChannels.remove(peerId)
            try {
                ch.unsubscribe()
            } catch (e: Exception) {
            }
            return null
        }
        peerChannels[peerId] = ch
        return ch
    }

    /**
     * Send one signal to the peer. On failure the cached channel is dropped
     * and retried once on a fresh channel (a mobile connection drop can
     * leave a dead cached channel); returns false only if both fail.
     */
    suspend fun sendSignal(peerId: String, event: String, payload: Any): Boolean {
        suspend fun attempt(): Boolean {
            val ch = peerChannel(peerId) ?: return false
            return try {
                when (payload) {
                    is CallOfferPayload -> ch.broadcast(event, payload)
                    is CallAnswerPayload -> ch.broadcast(event, payload)
                    is IceCandidatePayload -> ch.broadcast(event, payload)
                    is CallSignalPayload -> ch.broadcast(event, payload)
                    else -> return false
                }
                true
            } catch (e: Exception) {
                false
            }
        }
        if (attempt()) return true
        val stale = peerChannels.remove(peerId)
        if (stale != null) {
            try {
                stale.unsubscribe()
            } catch (e: Exception) {
            }
        }
        return attempt()
    }

    fun dropPeerChannel(peerId: String) {
        val ch = peerChannels.remove(peerId) ?: return
        try {
            ch.unsubscribe()
        } catch (e: Exception) {
        }
    }

    // ---------- incoming-signal flows on my own channel ----------

    @OptIn(SupabaseInternal::class)
    fun offerFlow(channel: RealtimeChannel): Flow<CallOfferPayload> =
        channel.broadcastFlow<CallOfferPayload>("call-offer")

    @OptIn(SupabaseInternal::class)
    fun answerFlow(channel: RealtimeChannel): Flow<CallAnswerPayload> =
        channel.broadcastFlow<CallAnswerPayload>("call-answer")

    @OptIn(SupabaseInternal::class)
    fun candidateFlow(channel: RealtimeChannel): Flow<IceCandidatePayload> =
        channel.broadcastFlow<IceCandidatePayload>("ice-candidate")

    @OptIn(SupabaseInternal::class)
    fun declineFlow(channel: RealtimeChannel): Flow<CallSignalPayload> =
        channel.broadcastFlow<CallSignalPayload>("call-decline")

    @OptIn(SupabaseInternal::class)
    fun endFlow(channel: RealtimeChannel): Flow<CallSignalPayload> =
        channel.broadcastFlow<CallSignalPayload>("call-end")

    @OptIn(SupabaseInternal::class)
    fun cancelFlow(channel: RealtimeChannel): Flow<CallSignalPayload> =
        channel.broadcastFlow<CallSignalPayload>("call-cancel")
}
