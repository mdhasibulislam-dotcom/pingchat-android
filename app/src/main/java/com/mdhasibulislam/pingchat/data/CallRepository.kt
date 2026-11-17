package com.mdhasibulislam.pingchat.data

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Calls: the `calls` collection + Firestore signaling (v2.0.0).
 *
 * Layout:
 * - calls/{callId}: callerId, calleeId, conversationId, kind (audio/video),
 *   status (ringing/accepted/declined/missed/ended), callerName,
 *   offerSdp {type, sdp}, answerSdp {type, sdp}, createdAt, startedAt,
 *   endedAt, durationSeconds (millis timestamps),
 *   participants [callerId, calleeId] (denormalized for the candidates
 *   listener — the Firestore rules cannot use get() on the parent call
 *   during a list query, so membership must live on each document)
 * - calls/{callId}/candidates/{autoId}: senderId, candidate, sdpMid,
 *   sdpMLineIndex, participants [callerId, calleeId] (same reason)
 *
 * The callee watches calls where calleeId == me for ringing docs (incoming
 * calls); the caller watches the one call doc for the answer + status, and
 * the candidates subcollection for the peer's ICE candidates. WebRTC media
 * (PeerConnection, OpenRelay TURN) is unchanged from v1.0.5.
 *
 * NOTE: this is a separate Firebase system from the web app — native users
 * can only call other native (Firebase) users, not web users.
 */
class CallRepository {
    private val db get() = FirebaseDb.db

    fun myId(): String? = FirebaseDb.auth.currentUser?.uid

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

    // ---------- calls collection ----------

    /** Create the ringing call doc with the caller's SDP offer. */
    suspend fun insertCall(
        callId: String,
        conversationId: String?,
        callerId: String,
        calleeId: String,
        kind: String,
        callerName: String,
        offer: SdpJson
    ): CallRow? {
        return try {
            val data = mutableMapOf<String, Any?>(
                "callerId" to callerId,
                "calleeId" to calleeId,
                "conversationId" to conversationId,
                "kind" to kind,
                "status" to "ringing",
                "callerName" to callerName,
                "offerSdp" to mapOf("type" to offer.type, "sdp" to offer.sdp),
                "answerSdp" to null,
                "createdAt" to nowMillis(),
                "startedAt" to null,
                "endedAt" to null,
                "durationSeconds" to null,
                "participants" to listOf(callerId, calleeId)
            )
            db.collection("calls").document(callId).set(data).await()
            callRowFromDoc(callId, data)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateCall(callId: String, patch: Map<String, Any?>) {
        try {
            db.collection("calls").document(callId)
                .set(patch, SetOptions.merge()).await()
        } catch (e: Exception) {
            // call history is best-effort; the call itself still works
        }
    }

    /** Callee accepted: status accepted + started_at. */
    suspend fun markAccepted(callId: String) {
        updateCall(callId, mapOf("status" to "accepted", "startedAt" to nowMillis()))
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
            "endedAt" to nowMillis()
        )
        if (durationSeconds != null) patch["durationSeconds"] = durationSeconds.toLong()
        updateCall(callId, patch)
    }

    /** The other side's profile (for the incoming-call screen). */
    suspend fun loadProfile(userId: String): Profile? {
        return try {
            val snap = db.collection("users").document(userId).get().await()
            if (!snap.exists()) null else profileFromDoc(userId, snap.data ?: emptyMap())
        } catch (e: Exception) {
            null
        }
    }

    // ---------- signaling transport (Firestore) ----------

    /** Callee: the callee's SDP answer for one call. */
    suspend fun sendAnswer(callId: String, answer: SdpJson) {
        try {
            db.collection("calls").document(callId).set(
                mapOf("answerSdp" to mapOf("type" to answer.type, "sdp" to answer.sdp)),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * The call's participants, cached per call. Candidate docs carry a
     * denormalized `participants` array (the Firestore rules cannot use
     * get() on the parent call during a list query, so membership must
     * live on each document itself).
     */
    private val participantsCache = mutableMapOf<String, List<String>>()

    private suspend fun participantsForCall(callId: String): List<String> {
        participantsCache[callId]?.let { return it }
        return try {
            val snap = db.collection("calls").document(callId).get().await()
            val caller = snap.getString("callerId")
            val callee = snap.getString("calleeId")
            val list = listOfNotNull(caller, callee)
            if (list.size == 2) participantsCache[callId] = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** One ICE candidate for the peer (goes to the candidates subcollection). */
    suspend fun sendCandidate(callId: String, candidate: IceCandidateJson) {
        val me = myId() ?: return
        try {
            db.collection("calls").document(callId).collection("candidates").add(
                mapOf(
                    "senderId" to me,
                    "candidate" to candidate.candidate,
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "participants" to participantsForCall(callId)
                )
            ).await()
        } catch (e: Exception) {
            // candidates are best-effort; the call may still connect
        }
    }

    /** Tell the other side the call ended/declined (status on the call doc). */
    suspend fun sendCallEnd(callId: String, status: String) {
        try {
            db.collection("calls").document(callId).set(
                mapOf("status" to status),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            // best-effort; markEnded/markDeclined also writes the status
        }
    }

    // ---------- incoming-signal flows ----------

    /** Ringing calls for me (callee side). Emits one offer per new call doc. */
    fun incomingOfferFlow(): Flow<CallOfferPayload> = callbackFlow {
        val me = myId()
        if (me == null) {
            close()
            return@callbackFlow
        }
        val reg = db.collection("calls")
            .whereEqualTo("calleeId", me)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED) return@forEach
                    val data = change.document.data
                    if (data["status"] as? String != "ringing") return@forEach
                    val offer = sdpFromMap(data["offerSdp"]) ?: return@forEach
                    trySend(
                        CallOfferPayload(
                            callId = change.document.id,
                            conversationId = data["conversationId"] as? String,
                            from = CallPeerInfo(
                                data["callerId"] as? String ?: "",
                                data["callerName"] as? String ?: ""
                            ),
                            kind = data["kind"] as? String ?: "audio",
                            sdp = offer
                        )
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    /**
     * A ringing call that went away (caller hung up / call ended while we
     * were still ringing). Emits the call id to dismiss the dialog.
     */
    fun incomingCancelFlow(): Flow<CallSignalPayload> = callbackFlow {
        val me = myId()
        if (me == null) {
            close()
            return@callbackFlow
        }
        val reg = db.collection("calls")
            .whereEqualTo("calleeId", me)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    val data = change.document.data
                    val status = data["status"] as? String
                    if (change.type == DocumentChange.Type.REMOVED ||
                        (change.type == DocumentChange.Type.MODIFIED && status != "ringing")
                    ) {
                        trySend(CallSignalPayload(change.document.id))
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    /** The callee's SDP answer for one call (caller side). Emits once. */
    fun answerFlow(callId: String): Flow<CallAnswerPayload> = callbackFlow {
        var emitted = false
        val reg = db.collection("calls").document(callId)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists() || emitted) return@addSnapshotListener
                val answer = sdpFromMap(snap.get("answerSdp")) ?: return@addSnapshotListener
                emitted = true
                trySend(CallAnswerPayload(callId, answer))
            }
        awaitClose { reg.remove() }
    }

    /**
     * Terminal status of one call (caller side): emits "declined" or
     * "ended" once the doc reaches that status.
     */
    fun callStatusFlow(callId: String): Flow<String> = callbackFlow {
        var lastStatus: String? = null
        val reg = db.collection("calls").document(callId)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val status = snap.getString("status") ?: return@addSnapshotListener
                if ((status == "declined" || status == "ended") && status != lastStatus) {
                    lastStatus = status
                    trySend(status)
                }
            }
        awaitClose { reg.remove() }
    }

    /** The peer's ICE candidates for one call, in arrival order. */
    fun candidatesFlow(callId: String, peerId: String): Flow<IceCandidatePayload> = callbackFlow {
        val me = myId() ?: run { close(); return@callbackFlow }
        // Only the participants filter lives in the query (single field,
        // auto-indexed); the sender is filtered client-side below so no
        // composite index is needed.
        val reg = db.collection("calls").document(callId)
            .collection("candidates")
            .whereArrayContains("participants", me)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED) return@forEach
                    val data = change.document.data
                    if (data["senderId"] as? String != peerId) return@forEach
                    val candidate = data["candidate"] as? String ?: return@forEach
                    trySend(
                        IceCandidatePayload(
                            callId,
                            IceCandidateJson(
                                candidate = candidate,
                                sdpMid = data["sdpMid"] as? String,
                                sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                            )
                        )
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun sdpFromMap(value: Any?): SdpJson? {
        val map = value as? Map<String, Any?> ?: return null
        val type = map["type"] as? String ?: return null
        val sdp = map["sdp"] as? String ?: return null
        return SdpJson(type, sdp)
    }
}
