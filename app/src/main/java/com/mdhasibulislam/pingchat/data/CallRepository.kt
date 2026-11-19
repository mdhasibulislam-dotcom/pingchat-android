package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Calls: the `pingchat_calls` table (call history + status) + Realtime
 * Broadcast signaling (v3.0.0).
 *
 * Signaling runs on channels `calls:<userId>` — I receive call-offer /
 * call-answer / ice-candidate / call-decline / call-end on MY channel, and
 * send each signal to the PEER's channel. There is no document that must
 * exist first, so the v2 M3 bug (the caller's first ICE candidates lost
 * while the call doc did not exist yet) cannot happen: the peer channel is
 * subscribed before the first candidate is sent.
 *
 * The calls row is still written (ringing + offer SDP, then status
 * updates) so call history exists and so a callee whose app was closed
 * can still find a ringing call when it opens (see incomingOfferFlow).
 */
class CallRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    companion object {
        /** 30s ring, then the call is marked missed. */
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

    /** callId -> peer user id, so signals know where to go. */
    private val callPeers = mutableMapOf<String, String>()

    /** Remember the peer for a call before the first ICE candidate exists (M3). */
    fun setCallPeer(callId: String, peerId: String) {
        callPeers[callId] = peerId
    }

    // ---------- calls table ----------

    /**
     * Create the ringing call row with the caller's SDP offer, then
     * broadcast the offer to the callee. Returns the row (null on failure).
     */
    suspend fun insertCall(
        callId: String,
        conversationId: String?,
        callerId: String,
        calleeId: String,
        kind: String,
        callerName: String,
        offer: SdpJson
    ): CallRow? {
        callPeers[callId] = calleeId
        val row = try {
            client.from("pingchat_calls").insert(
                buildJsonObject {
                    put("id", callId)
                    if (conversationId != null) put("conversation_id", conversationId)
                    put("caller_id", callerId)
                    put("callee_id", calleeId)
                    put("caller_name", callerName)
                    put("kind", kind)
                    put("status", "ringing")
                    put("offer_sdp", buildJsonObject {
                        put("type", offer.type)
                        put("sdp", offer.sdp)
                    })
                }
            ) { select() }.decodeSingleOrNull<CallRow>()
        } catch (e: Exception) {
            null
        }
        if (row != null) {
            // Ring the callee even if their app is open: the broadcast is
            // the live offer. (The FCM push in CallManager covers a
            // closed app; the sweep in incomingOfferFlow covers the rest.)
            sendToPeer(
                calleeId,
                "call-offer",
                CallOfferPayload(
                    callId = callId,
                    conversationId = conversationId,
                    from = CallPeerInfo(callerId, callerName),
                    kind = kind,
                    sdp = offer
                )
            )
        }
        return row
    }

    private suspend fun updateCall(callId: String, patch: Map<String, Any?>) {
        try {
            client.from("pingchat_calls").update({
                patch.forEach { (key, value) -> set(key, value) }
            }) {
                filter { eq("id", callId) }
            }
        } catch (e: Exception) {
            // call history is best-effort; the call itself still works
        }
    }

    /** Callee accepted: status accepted + started_at. */
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
        val patch = mutableMapOf<String, Any?>(
            "status" to "ended",
            "ended_at" to Instant.now().toString()
        )
        if (durationSeconds != null) patch["duration_seconds"] = durationSeconds
        updateCall(callId, patch)
    }

    // ---------- per-user call history (pingchat_call_history) ----------

    /**
     * Record one side's own history entry for a finished call. Best-effort:
     * history must never break the call itself. The row belongs to
     * [userId], so deleting it later removes the history for that user
     * only — the other side keeps theirs.
     */
    suspend fun insertCallHistory(
        userId: String,
        callId: String,
        peerId: String,
        peerName: String?,
        kind: String,
        direction: String,
        status: String,
        startedAt: String?,
        endedAt: String?,
        durationSeconds: Int?
    ) {
        try {
            client.from("pingchat_call_history").insert(
                buildJsonObject {
                    put("user_id", userId)
                    put("call_id", callId)
                    put("peer_id", peerId)
                    if (peerName != null) put("peer_name", peerName)
                    put("kind", kind)
                    put("direction", direction)
                    put("status", status)
                    if (startedAt != null) put("started_at", startedAt)
                    if (endedAt != null) put("ended_at", endedAt)
                    if (durationSeconds != null) put("duration_seconds", durationSeconds)
                }
            )
        } catch (e: Exception) {
            // call history is best-effort
        }
    }

    /** My history, newest first (last 100). */
    suspend fun loadCallHistoryRows(): List<CallHistoryRow> {
        val me = myId() ?: return emptyList()
        return try {
            client.from("pingchat_call_history").select {
                filter { eq("user_id", me) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<CallHistoryRow>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Profiles for a set of user ids (avatars + current names for history rows). */
    suspend fun loadProfilesByIds(ids: List<String>): Map<String, Profile> {
        if (ids.isEmpty()) return emptyMap()
        return try {
            client.from("pingchat_profiles").select {
                filter { isIn("id", ids.distinct()) }
            }.decodeList<Profile>().associateBy { it.id }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Delete ONE of my history entries (the other side keeps theirs). */
    suspend fun deleteCallHistory(id: String) {
        val me = myId() ?: return
        try {
            client.from("pingchat_call_history").delete {
                filter {
                    eq("id", id)
                    eq("user_id", me)
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /** The other side's profile (for the incoming-call screen). */
    suspend fun loadProfile(userId: String): Profile? {
        return try {
            client.from("pingchat_profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    // ---------- signaling transport (Realtime Broadcast) ----------

    /** My own channel: I receive call-offer/answer/candidates on `calls:<myId>`. */
    private suspend fun myChannel(): RealtimeChannel? {
        val me = myId() ?: return null
        val name = "calls:$me"
        val channel = RealtimeChannels.get(name) {
            client.realtime.channel(name)
        }
        RealtimeChannels.ensureSubscribed(name)
        return channel
    }

    /**
     * The peer's channel, subscribed before use. subscribe() waits until
     * the channel is joined (or fails) so the first signals are not lost
     * on a fresh channel. A failed channel is never cached.
     */
    private suspend fun peerChannel(peerId: String): RealtimeChannel? {
        val name = "calls:$peerId"
        val channel = RealtimeChannels.get(name) {
            client.realtime.channel(name)
        }
        val ok = withTimeoutOrNull(10_000) {
            try {
                RealtimeChannels.ensureSubscribed(name)
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
        if (!ok) {
            RealtimeChannels.drop(name)
            return null
        }
        return channel
    }

    /**
     * Send one signal to the peer. On failure the cached channel is dropped
     * and retried once on a fresh channel (a mobile connection drop can
     * leave a dead cached channel); returns false only if both fail.
     */
    private suspend fun sendToPeer(peerId: String, event: String, payload: Any): Boolean {
        suspend fun attempt(): Boolean {
            val channel = peerChannel(peerId) ?: return false
            return try {
                when (payload) {
                    is CallOfferPayload -> channel.broadcast(event, payload)
                    is CallAnswerPayload -> channel.broadcast(event, payload)
                    is IceCandidatePayload -> channel.broadcast(event, payload)
                    is CallSignalPayload -> channel.broadcast(event, payload)
                    else -> return false
                }
                true
            } catch (e: Exception) {
                false
            }
        }
        if (attempt()) return true
        RealtimeChannels.drop("calls:$peerId")
        return attempt()
    }

    /** Callee: the callee's SDP answer for one call. Returns false when it could not be sent. */
    suspend fun sendAnswer(callId: String, answer: SdpJson): Boolean {
        val peerId = callPeers[callId] ?: return false
        // Save the answer on the call row too (call history), best-effort.
        try {
            client.from("pingchat_calls").update({
                set("answer_sdp", buildJsonObject {
                    put("type", answer.type)
                    put("sdp", answer.sdp)
                })
            }) {
                filter { eq("id", callId) }
            }
        } catch (e: Exception) {
        }
        return sendToPeer(peerId, "call-answer", CallAnswerPayload(callId, answer))
    }

    /** One ICE candidate for the peer (broadcast — no document needed, M3). */
    suspend fun sendCandidate(callId: String, candidate: IceCandidateJson) {
        val peerId = callPeers[callId] ?: return
        sendToPeer(peerId, "ice-candidate", IceCandidatePayload(callId, candidate))
    }

    /** Tell the other side the call ended/declined (broadcast + row status). */
    suspend fun sendCallEnd(callId: String, status: String) {
        val peerId = callPeers[callId]
        if (peerId != null) {
            val event = if (status == "declined") "call-decline" else "call-end"
            sendToPeer(peerId, event, CallSignalPayload(callId))
        }
        try {
            client.from("pingchat_calls").update({
                set("status", status)
            }) {
                filter { eq("id", callId) }
            }
        } catch (e: Exception) {
            // best-effort; markEnded/markDeclined also writes the status
        }
    }

    /** Drop every call channel (sign-out). */
    suspend fun closeChannels() {
        RealtimeChannels.dropPrefix("calls:")
        callPeers.clear()
    }

    // ---------- incoming-signal flows on my own channel ----------

    /**
     * Ringing calls for me (callee side). Emits one offer per new call.
     * Broadcasts arrive live; the sweep below also catches a call that
     * started while this app was closed (push tap opens the app after the
     * broadcast already went out) — any still-ringing row for me from the
     * last 7 days is emitted once when collection starts if it is still
     * inside the 30s ring window, or marked missed (with a history entry)
     * if the ring already timed out while the app was closed.
     */
    @OptIn(SupabaseInternal::class)
    fun incomingOfferFlow(): Flow<CallOfferPayload> = flow {
        val me = myId()
        if (me != null) {
            try {
                val cutoff = Instant.now().minusSeconds(7 * 24 * 3600).toString()
                val ringing = client.from("pingchat_calls").select {
                    filter {
                        eq("callee_id", me)
                        eq("status", "ringing")
                        gt("created_at", cutoff)
                    }
                }.decodeList<CallRow>()
                ringing.forEach { row ->
                    // A row older than the 30s ring timeout is a call I
                    // missed while the app was closed: it will never ring
                    // now, so mark it missed and save my history entry.
                    val created = TimeFormat.parse(row.createdAt)
                    val ageSeconds = if (created == null) 0
                        else java.time.Duration.between(created, Instant.now()).seconds
                    if (ageSeconds >= CALL_TIMEOUT_MS / 1000) {
                        markMissed(row.id)
                        insertCallHistory(
                            userId = me,
                            callId = row.id,
                            peerId = row.callerId,
                            peerName = row.callerName,
                            kind = row.kind,
                            direction = "incoming",
                            status = "missed",
                            startedAt = null,
                            endedAt = null,
                            durationSeconds = null
                        )
                        return@forEach
                    }
                    val offer = row.offerSdp ?: return@forEach
                    callPeers[row.id] = row.callerId
                    emit(
                        CallOfferPayload(
                            callId = row.id,
                            conversationId = row.conversationId,
                            from = CallPeerInfo(row.callerId, row.callerName ?: ""),
                            kind = row.kind,
                            sdp = offer
                        )
                    )
                }
            } catch (e: Exception) {
                // sweep is best-effort; live broadcasts still work
            }
        }
        val channel = myChannel() ?: return@flow
        channel.broadcastFlow<CallOfferPayload>("call-offer").collect { payload ->
            callPeers[payload.callId] = payload.from.id
            emit(payload)
        }
    }

    /**
     * A ringing call that went away (caller hung up / call ended while we
     * were still ringing). Emits the call id to dismiss the dialog.
     */
    @OptIn(SupabaseInternal::class)
    fun incomingCancelFlow(): Flow<CallSignalPayload> = flow {
        val channel = myChannel() ?: return@flow
        merge(
            channel.broadcastFlow<CallSignalPayload>("call-cancel"),
            channel.broadcastFlow<CallSignalPayload>("call-end")
        ).collect { emit(it) }
    }

    /** The callee's SDP answer for one call (caller side). */
    @OptIn(SupabaseInternal::class)
    fun answerFlow(callId: String): Flow<CallAnswerPayload> = flow {
        val channel = myChannel() ?: return@flow
        channel.broadcastFlow<CallAnswerPayload>("call-answer")
            .filter { it.callId == callId }
            .collect { emit(it) }
    }

    /**
     * Terminal status of one call (caller side): emits "declined" or
     * "ended" once the peer sends it.
     */
    @OptIn(SupabaseInternal::class)
    fun callStatusFlow(callId: String): Flow<String> = flow {
        val channel = myChannel() ?: return@flow
        merge(
            channel.broadcastFlow<CallSignalPayload>("call-decline")
                .map { "declined" to it.callId },
            channel.broadcastFlow<CallSignalPayload>("call-end")
                .map { "ended" to it.callId }
        ).filter { (_, id) -> id == callId }
            .map { (status, _) -> status }
            .collect { emit(it) }
    }

    /** The peer's ICE candidates for one call, in arrival order. */
    @OptIn(SupabaseInternal::class)
    fun candidatesFlow(callId: String, peerId: String): Flow<IceCandidatePayload> = flow {
        val channel = myChannel() ?: return@flow
        channel.broadcastFlow<IceCandidatePayload>("ice-candidate")
            .filter { it.callId == callId }
            .collect { emit(it) }
    }
}
