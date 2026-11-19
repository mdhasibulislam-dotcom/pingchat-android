package com.mdhasibulislam.pingchat.data

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioTrack
import kotlin.coroutines.resume
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID

/**
 * Real audio/video calls: WebRTC peer connection + Realtime Broadcast
 * signaling (v3.0.0 — channels calls:<userId>, replacing the Firestore
 * calls/{callId} doc + candidates subcollection of v2.0.0). One call at a
 * time, app-wide: the incoming-call dialog and the in-call screen are
 * rendered from MainScreen, whatever tab the user is on.
 *
 * Started once per sign-in from MainActivity (CallManager.start) and
 * stopped on sign-out. All WebRTC work happens on the calling thread
 * (main) for setup; media flows on WebRTC's own threads.
 *
 * NOTE: this is a separate Supabase system from the web app — native users
 * can only call other native (Supabase) users, not web users.
 */
object CallManager {
    private val repo = CallRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _incoming = MutableStateFlow<IncomingCall?>(null)
    val incoming: StateFlow<IncomingCall?> = _incoming.asStateFlow()

    private val _session = MutableStateFlow<CallSession?>(null)
    val session: StateFlow<CallSession?> = _session.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    /** Short user-facing error (shown once by the UI, then cleared). */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    /** Post a short error for the UI to show once. */
    fun postError(message: String) {
        _error.value = message
    }

    // ---------- setup ----------

    private var appContext: Context? = null
    private var myId: String? = null
    private var ringtoneEnabled = true
    private var started = false

    private val signalJobs = mutableListOf<Job>()
    private val callSignalJobs = mutableListOf<Job>()

    /** EGL context shared by the local preview + remote video renderers. */
    val eglContext: EglBase.Context? get() = eglBase?.eglBaseContext

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var capturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrackObj: VideoTrack? = null
    private var ringtone: Ringtone? = null
    private var audioManager: AudioManager? = null

    private var currentCallId: String? = null
    private var currentPeerId: String? = null
    private var currentKind: String? = null
    private var currentConversationId: String? = null
    private var currentDirection: String? = null
    private var currentOffer: SdpJson? = null
    private var callStartedAtMillis: Long? = null
    private var noAnswerJob: Job? = null
    private var timerJob: Job? = null
    private val pendingCandidates = mutableListOf<IceCandidateJson>()

    /** Watch Realtime Broadcast for ringing calls addressed to me. */
    fun start(context: Context, userId: String, prefs: Prefs) {
        if (started && myId == userId) return
        stop()
        started = true
        appContext = context.applicationContext
        myId = userId
        ringtoneEnabled = prefs.ringtoneEnabled
        signalJobs += scope.launch { repo.incomingOfferFlow().collect { onOffer(it) } }
        signalJobs += scope.launch { repo.incomingCancelFlow().collect { onCancel(it) } }
    }

    /** End any call and stop listening (sign-out). */
    fun stop() {
        val s = _session.value
        if (s != null && currentPeerId != null) {
            val callId = s.callId
            val duration = s.durationSec
            scope.launch {
                try {
                    repo.sendCallEnd(callId, "ended")
                    repo.markEnded(callId, duration)
                } catch (e: Exception) {
                }
            }
        }
        scope.launch {
            try {
                repo.closeChannels()
            } catch (e: Exception) {
            }
        }
        started = false
        signalJobs.forEach { it.cancel() }
        signalJobs.clear()
        callSignalJobs.forEach { it.cancel() }
        callSignalJobs.clear()
        noAnswerJob?.cancel()
        timerJob?.cancel()
        stopRingtone()
        endCallInternal(null)
        _incoming.value = null
        myId = null
    }

    /** Per-call listeners: answer (caller only), ICE candidates, end/decline. */
    private fun attachCallListeners(callId: String, peerId: String, outgoing: Boolean) {
        if (outgoing) {
            callSignalJobs += scope.launch { repo.answerFlow(callId).collect { onAnswer(it) } }
        }
        callSignalJobs += scope.launch { repo.candidatesFlow(callId, peerId).collect { onCandidate(it) } }
        callSignalJobs += scope.launch { repo.callStatusFlow(callId).collect { onCallStatus(it) } }
    }

    private fun detachCallListeners() {
        callSignalJobs.forEach { it.cancel() }
        callSignalJobs.clear()
    }

    // ---------- outgoing call ----------

    /**
     * Start an audio/video call to [peer]. Returns null on success, or a
     * short error message for the UI to toast. Caller must already have
     * checked the peer is online and the CAMERA + RECORD_AUDIO permission.
     */
    suspend fun startCall(peer: Profile, kind: String, conversationId: String): String? {
        val me = myId ?: return "You are not signed in."
        if (_session.value != null || _incoming.value != null) return "You are already in a call."
        val context = appContext ?: return "Call setup is not ready yet."
        val callId = UUID.randomUUID().toString()
        ensureFactory(context)
        if (!createLocalTracks(kind, context)) {
            return "Microphone/camera unavailable. Check the permission and try again."
        }
        val pc = makePeerConnection(callId, peer.id) ?: run {
            stopLocalTracks()
            return "Could not start call. Try again."
        }
        peerConnection = pc
        currentCallId = callId
        currentPeerId = peer.id
        currentKind = kind
        currentConversationId = conversationId
        currentDirection = "outgoing"
        // Tell the repo the peer NOW — ICE candidates are generated during
        // createOffer/setLocalDescription below, before insertCall runs, and
        // they need a peer channel to go to (M3: with Firestore they were
        // lost because the call doc did not exist yet).
        repo.setCallPeer(callId, peer.id)
        attachCallListeners(callId, peer.id, outgoing = true)
        setAudioMode(kind)
        _session.value = CallSession(
            callId = callId, peer = peer, kind = kind,
            status = CallStatus.CALLING, direction = "outgoing",
            speakerOn = kind == "video"
        )
        val offer = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<SessionDescription?> { cont ->
                pc.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        if (cont.isActive) cont.resume(desc)
                    }

                    override fun onCreateFailure(error: String) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String) {}
                }, MediaConstraints())
            }
        }
        if (offer == null) {
            endCallInternal(null)
            return "Could not start call. Try again."
        }
        val localOk = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onSetFailure(error: String) {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onCreateSuccess(desc: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, offer)
            }
        } ?: false
        if (!localOk) {
            endCallInternal(null)
            return "Could not start call. Try again."
        }
        val myName = SessionManager.profile.value?.fullName?.ifBlank {
            SessionManager.profile.value?.username
        } ?: "Someone"
        // The ringing call row IS the offer record: creating it rings the callee.
        val row = repo.insertCall(
            callId, conversationId, me, peer.id, kind, myName,
            SdpJson(offer.type.canonicalForm(), offer.description)
        )
        if (row == null) {
            endCallInternal(null)
            return "Could not reach the other side. Check your connection and try again."
        }
        // WhatsApp-style ring: push the callee even if the app is closed.
        peer.fcmToken?.let { token ->
            scope.launch {
                PushSender.sendPush(
                    token, myName,
                    if (kind == "video") "Incoming video call" else "Incoming voice call",
                    mapOf("type" to "call", "callId" to callId, "kind" to kind)
                )
            }
        }
        startNoAnswerTimer(callId)
        return null
    }

    // ---------- incoming call ----------

    private fun onOffer(payload: CallOfferPayload) {
        scope.launch {
            // The same offer can arrive twice (live broadcast + the ringing
            // sweep in incomingOfferFlow) — never auto-decline my own call.
            if (_incoming.value?.callId == payload.callId) return@launch
            if (_session.value != null || _incoming.value != null) {
                // Busy: auto-decline so the caller is not left ringing.
                repo.sendCallEnd(payload.callId, "declined")
                repo.markDeclined(payload.callId)
                return@launch
            }
            val peerProfile = repo.loadProfile(payload.from.id)
            val peer = peerProfile ?: Profile(
                id = payload.from.id, username = "", fullName = payload.from.name
            )
            currentCallId = payload.callId
            currentPeerId = payload.from.id
            currentKind = payload.kind
            currentConversationId = payload.conversationId
            currentDirection = "incoming"
            currentOffer = payload.sdp
            _incoming.value = IncomingCall(
                callId = payload.callId,
                conversationId = payload.conversationId,
                peerId = payload.from.id,
                peerName = peer.fullName.ifBlank { peer.username }.ifBlank { payload.from.name },
                kind = payload.kind,
                offer = payload.sdp
            )
            startRingtone()
            noAnswerJob?.cancel()
            noAnswerJob = scope.launch {
                delay(CallRepository.CALL_TIMEOUT_MS)
                if (_incoming.value?.callId == payload.callId) {
                    stopRingtone()
                    _incoming.value = null
                    repo.markMissed(payload.callId)
                    clearCurrent()
                }
            }
        }
    }

    /**
     * Accept the ringing incoming call. [acceptKind] is "audio" or "video"
     * (the user may answer a video offer as audio-only). Returns null on
     * success, or a short error message.
     */
    suspend fun acceptCall(acceptKind: String): String? {
        val inc = _incoming.value ?: return "No incoming call."
        val context = appContext ?: return "Call setup is not ready yet."
        noAnswerJob?.cancel()
        stopRingtone()
        _incoming.value = null
        val kind = acceptKind
        ensureFactory(context)
        if (!createLocalTracks(kind, context)) {
            scope.launch {
                repo.sendCallEnd(inc.callId, "declined")
                repo.markDeclined(inc.callId)
            }
            clearCurrent()
            return "Microphone/camera unavailable. Check the permission and try again."
        }
        val peerProfile = repo.loadProfile(inc.peerId)
        val peer = peerProfile ?: Profile(id = inc.peerId, username = "", fullName = inc.peerName)
        val pc = makePeerConnection(inc.callId, inc.peerId) ?: run {
            stopLocalTracks()
            clearCurrent()
            return "Could not answer call. Try again."
        }
        peerConnection = pc
        attachCallListeners(inc.callId, inc.peerId, outgoing = false)
        currentKind = kind
        setAudioMode(kind)
        _session.value = CallSession(
            callId = inc.callId, peer = peer, kind = kind,
            status = CallStatus.ACTIVE, direction = "incoming",
            speakerOn = kind == "video"
        )
        val remoteOk = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                pc.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onSetFailure(error: String) {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onCreateSuccess(desc: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, SessionDescription(SessionDescription.Type.fromCanonicalForm(inc.offer.type), inc.offer.sdp))
            }
        } ?: false
        if (!remoteOk) {
            scope.launch {
                repo.sendCallEnd(inc.callId, "declined")
                repo.markDeclined(inc.callId)
            }
            endCallInternal(null)
            return "Could not answer call. Try again."
        }
        val answer = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<SessionDescription?> { cont ->
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        if (cont.isActive) cont.resume(desc)
                    }

                    override fun onCreateFailure(error: String) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String) {}
                }, MediaConstraints())
            }
        }
        if (answer == null) {
            endCallInternal(null)
            return "Could not answer call. Try again."
        }
        val localOk = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onSetFailure(error: String) {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onCreateSuccess(desc: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, answer)
            }
        } ?: false
        if (!localOk) {
            endCallInternal(null)
            return "Could not answer call. Try again."
        }
        // M4: the network can drop right at Accept — sending the answer
        // must never crash the app. A failed send ends the call cleanly
        // instead (the caller sees it as declined).
        val answerSent = try {
            repo.sendAnswer(inc.callId, SdpJson(answer.type.canonicalForm(), answer.description))
        } catch (e: Exception) {
            false
        }
        if (!answerSent) {
            scope.launch {
                repo.sendCallEnd(inc.callId, "declined")
                repo.markDeclined(inc.callId)
            }
            endCallInternal(null)
            return "Could not send the answer. Check your connection and try again."
        }
        repo.markAccepted(inc.callId)
        flushPendingCandidates()
        return null
    }

    /** Decline the ringing incoming call. */
    suspend fun declineCall() {
        val inc = _incoming.value ?: return
        noAnswerJob?.cancel()
        stopRingtone()
        _incoming.value = null
        repo.sendCallEnd(inc.callId, "declined")
        repo.markDeclined(inc.callId)
        clearCurrent()
    }

    // ---------- signaling handlers (Realtime Broadcast) ----------

    private fun onAnswer(payload: CallAnswerPayload) {
        val s = _session.value ?: return
        if (s.callId != payload.callId || s.status != CallStatus.CALLING) return
        noAnswerJob?.cancel()
        val pc = peerConnection ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                scope.launch { flushPendingCandidates() }
            }

            override fun onSetFailure(error: String) {
                scope.launch { endCallInternal("Call failed. Try again.") }
            }

            override fun onCreateSuccess(desc: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }, SessionDescription(SessionDescription.Type.fromCanonicalForm(payload.sdp.type), payload.sdp.sdp))
        _session.update { it?.copy(status = CallStatus.ACTIVE) }
    }

    private fun onCandidate(payload: IceCandidatePayload) {
        val s = _session.value ?: return
        if (s.callId != payload.callId) return
        val pc = peerConnection
        val cand = IceCandidate(
            payload.candidate.sdpMid,
            payload.candidate.sdpMLineIndex,
            payload.candidate.candidate
        )
        if (pc != null && pc.remoteDescription != null) {
            try {
                pc.addIceCandidate(cand)
            } catch (e: Exception) {
            }
        } else {
            pendingCandidates.add(payload.candidate)
        }
    }

    private fun flushPendingCandidates() {
        val pc = peerConnection ?: return
        val queued = pendingCandidates.toList()
        pendingCandidates.clear()
        queued.forEach { cand ->
            try {
                pc.addIceCandidate(IceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.candidate))
            } catch (e: Exception) {
            }
        }
    }

    /** The call doc reached a terminal status (caller side). */
    private fun onCallStatus(status: String) {
        when (status) {
            "declined" -> onDecline()
            "ended" -> onRemoteEnd()
        }
    }

    private fun onDecline() {
        val s = _session.value ?: return
        noAnswerJob?.cancel()
        scope.launch { repo.markDeclined(s.callId) }
        endCallInternal("Call declined.")
    }

    private fun onRemoteEnd() {
        val s = _session.value ?: return
        endCallInternal(null)
    }

    private fun onCancel(payload: CallSignalPayload) {
        // The caller hung up while we were still ringing: stop ringing.
        val inc = _incoming.value
        if (inc != null && inc.callId == payload.callId) {
            noAnswerJob?.cancel()
            stopRingtone()
            _incoming.value = null
            clearCurrent()
            return
        }
        val s = _session.value ?: return
        if (s.callId != payload.callId) return
        endCallInternal(null)
    }

    // ---------- hang up / in-call controls ----------

    /** Hang up: tell the other side, mark the row ended, tear down locally. */
    suspend fun endCall() {
        val s = _session.value ?: return
        noAnswerJob?.cancel()
        val wasAnswered = s.status == CallStatus.ACTIVE && callStartedAtMillis != null
        repo.sendCallEnd(s.callId, "ended")
        repo.markEnded(s.callId, s.durationSec.takeIf { wasAnswered })
        endCallInternal(null)
    }

    /** Mute / unmute my microphone. */
    fun toggleMute() {
        val track = localAudioTrack ?: return
        track.setEnabled(!track.enabled())
        _session.update { it?.copy(muted = !track.enabled()) }
    }

    /** Speakerphone on/off (video calls start on speaker, audio on earpiece). */
    fun toggleSpeaker() {
        val am = audioManager ?: return
        val on = !am.isSpeakerphoneOn
        am.isSpeakerphoneOn = on
        _session.update { it?.copy(speakerOn = on) }
    }

    /** Turn my camera off/on (video calls). */
    fun toggleCamera() {
        val track = localVideoTrackObj ?: return
        track.setEnabled(!track.enabled())
        _session.update { it?.copy(cameraEnabled = track.enabled()) }
    }

    /** Switch between front and back camera (video calls). */
    fun switchCamera() {
        try {
            capturer?.switchCamera(null)
        } catch (e: Exception) {
        }
    }

    // ---------- peer connection ----------

    private fun ensureFactory(context: Context) {
        if (factory != null) return
        val egl = EglBase.create()
        eglBase = egl
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    /** Mic (+ camera for video): local tracks the peer will receive. */
    private fun createLocalTracks(kind: String, context: Context): Boolean {
        val f = factory ?: return false
        val audioSource = f.createAudioSource(MediaConstraints())
        localAudioTrack = f.createAudioTrack("ARDAMSa0", audioSource)
        if (kind == "video") {
            val enumerator = Camera2Enumerator(context)
            val names = enumerator.deviceNames
            val front = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
            if (front == null) return false
            val cap = enumerator.createCapturer(front, null) as? CameraVideoCapturer ?: return false
            val egl = eglBase ?: return false
            val source = f.createVideoSource(cap.isScreencast)
            try {
                cap.initialize(
                    SurfaceTextureHelper.create("CaptureThread", egl.eglBaseContext),
                    context.applicationContext,
                    source.capturerObserver
                )
                cap.startCapture(1280, 720, 30)
            } catch (e: Exception) {
                return false
            }
            capturer = cap
            videoSource = source
            localVideoTrackObj = f.createVideoTrack("ARDAMSv0", source)
            _localVideoTrack.value = localVideoTrackObj
        }
        return true
    }

    private fun makePeerConnection(callId: String, peerId: String): PeerConnection? {
        val f = factory ?: return null
        val iceServers = CallRepository.ICE_SERVERS.map { server ->
            val builder = PeerConnection.IceServer.builder(server.urls)
            if (server.username != null) builder.setUsername(server.username)
            if (server.credential != null) builder.setPassword(server.credential)
            builder.createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(iceServers)
        val pc = f.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    repo.sendCandidate(
                        callId,
                        IceCandidateJson(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                    )
                }
            }

            override fun onAddTrack(receiver: RtpReceiver, streams: Array<MediaStream>) {
                val track = receiver.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                    _session.update { it?.copy(hasRemoteVideo = true) }
                }
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                    onConnected()
                } else if (state == PeerConnection.PeerConnectionState.FAILED) {
                    scope.launch {
                        endCallInternal("Connection failed. The other side may be unreachable.")
                    }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }) ?: return null
        localAudioTrack?.let { pc.addTrack(it, listOf("ARDAMS")) }
        localVideoTrackObj?.let { pc.addTrack(it, listOf("ARDAMS")) }
        return pc
    }

    /** Media is flowing: start the call timer. */
    private fun onConnected() {
        if (callStartedAtMillis != null) return
        callStartedAtMillis = System.currentTimeMillis()
        _session.update { it?.copy(startedAtMillis = callStartedAtMillis) }
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                val started = callStartedAtMillis ?: break
                val sec = ((System.currentTimeMillis() - started) / 1000).toInt()
                _session.update { it?.copy(durationSec = sec) }
            }
        }
    }

    private fun startNoAnswerTimer(callId: String) {
        noAnswerJob?.cancel()
        noAnswerJob = scope.launch {
            delay(CallRepository.CALL_TIMEOUT_MS)
            if (currentCallId == callId && _session.value?.status == CallStatus.CALLING) {
                repo.markMissed(callId)
                endCallInternal("No answer — call marked as missed.")
            }
        }
    }

    // ---------- teardown ----------

    /** Local teardown only (remote side ended/declined, or an error). */
    private fun endCallInternal(message: String?) {
        noAnswerJob?.cancel()
        timerJob?.cancel()
        detachCallListeners()
        stopRingtone()
        try {
            peerConnection?.close()
        } catch (e: Exception) {
        }
        peerConnection = null
        stopLocalTracks()
        resetAudioMode()
        _session.value = null
        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        pendingCandidates.clear()
        currentCallId = null
        currentPeerId = null
        currentKind = null
        currentConversationId = null
        currentDirection = null
        currentOffer = null
        callStartedAtMillis = null
        if (message != null) _error.value = message
    }

    private fun stopLocalTracks() {
        try {
            capturer?.stopCapture()
        } catch (e: Exception) {
        }
        try {
            capturer?.dispose()
        } catch (e: Exception) {
        }
        capturer = null
        try {
            videoSource?.dispose()
        } catch (e: Exception) {
        }
        videoSource = null
        try {
            localAudioTrack?.dispose()
        } catch (e: Exception) {
        }
        localAudioTrack = null
        localVideoTrackObj = null
    }

    private fun clearCurrent() {
        detachCallListeners()
        currentCallId = null
        currentPeerId = null
        currentKind = null
        currentConversationId = null
        currentDirection = null
        currentOffer = null
        pendingCandidates.clear()
    }

    // ---------- ringtone + audio mode ----------

    private fun startRingtone() {
        if (!ringtoneEnabled) return
        try {
            stopRingtone()
            val context = appContext ?: return
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val rt = RingtoneManager.getRingtone(context, uri)
            rt.play()
            ringtone = rt
        } catch (e: Exception) {
            // ringtone is best-effort
        }
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
        }
        ringtone = null
    }

    private fun setAudioMode(kind: String) {
        try {
            val context = appContext ?: return
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = kind == "video"
        } catch (e: Exception) {
        }
    }

    private fun resetAudioMode() {
        try {
            audioManager?.let {
                it.mode = AudioManager.MODE_NORMAL
                it.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
        }
        audioManager = null
    }
}
