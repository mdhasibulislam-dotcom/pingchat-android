package com.mdhasibulislam.pingchat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WebRTC call signaling payloads — the field names match the web app's
 * js/calls.js shapes, but the transport is Realtime Broadcast (v3.0.0,
 * channels calls:<userId>) so a native user can only call another native
 * user. Events: call-offer / call-answer / ice-candidate / call-decline /
 * call-end / call-cancel.
 */

/** The caller, as sent in a call-offer (web: payload.from = {id, name}). */
@Serializable
data class CallPeerInfo(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = ""
)

/** An SDP offer/answer (web: pc.localDescription = {type, sdp}). */
@Serializable
data class SdpJson(
    @SerialName("type") val type: String,
    @SerialName("sdp") val sdp: String
)

/** One ICE candidate (web: RTCIceCandidate.toJSON()). */
@Serializable
data class IceCandidateJson(
    @SerialName("candidate") val candidate: String,
    @SerialName("sdpMid") val sdpMid: String? = null,
    @SerialName("sdpMLineIndex") val sdpMLineIndex: Int = 0,
    @SerialName("usernameFragment") val usernameFragment: String? = null
)

/** call-offer: {callId, conversationId, from, kind, sdp}. */
@Serializable
data class CallOfferPayload(
    @SerialName("callId") val callId: String,
    @SerialName("conversationId") val conversationId: String? = null,
    @SerialName("from") val from: CallPeerInfo,
    @SerialName("kind") val kind: String = "audio",
    @SerialName("sdp") val sdp: SdpJson
)

/** call-answer: {callId, sdp}. */
@Serializable
data class CallAnswerPayload(
    @SerialName("callId") val callId: String,
    @SerialName("sdp") val sdp: SdpJson
)

/** ice-candidate: {callId, candidate}. */
@Serializable
data class IceCandidatePayload(
    @SerialName("callId") val callId: String,
    @SerialName("candidate") val candidate: IceCandidateJson
)

/** call-decline / call-end / call-cancel: {callId}. */
@Serializable
data class CallSignalPayload(
    @SerialName("callId") val callId: String
)

/** An incoming call ringing on this device. */
data class IncomingCall(
    val callId: String,
    val conversationId: String?,
    val peerId: String,
    val peerName: String,
    val kind: String,
    val offer: SdpJson
)

/** Status of the call currently on screen (null = no call). */
enum class CallStatus { CALLING, RINGING, ACTIVE }

/** Everything the in-call UI needs. VideoTrack references change rarely. */
data class CallSession(
    val callId: String,
    val peer: Profile,
    val kind: String,
    val status: CallStatus,
    val direction: String, // "outgoing" | "incoming"
    val startedAtMillis: Long? = null,
    val durationSec: Int = 0,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val cameraEnabled: Boolean = true,
    val hasRemoteVideo: Boolean = false
)
