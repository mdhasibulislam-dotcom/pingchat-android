package com.mdhasibulislam.pingchat.ui.viewmodel

import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.ChatMessage
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.PresenceState
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.PushSender
import com.mdhasibulislam.pingchat.data.Reaction
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.TickState
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.data.TypingPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * One open 1-to-1 chat: messages, read ticks, typing, reactions, deletes.
 * Mirrors js/chats.js (openConversation + ticks + typing).
 */
class ChatViewModel(private val conversationId: String) : ViewModel() {
    private val repo = ChatRepository()

    val myId: String? get() = repo.myId()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _peer = MutableStateFlow<Profile?>(null)
    val peer: StateFlow<Profile?> = _peer.asStateFlow()

    private val _peerLastReadAt = MutableStateFlow<String?>(null)
    val peerLastReadAt: StateFlow<String?> = _peerLastReadAt.asStateFlow()

    private val _reactions = MutableStateFlow<Map<String, List<Reaction>>>(emptyMap())
    val reactions: StateFlow<Map<String, List<Reaction>>> = _reactions.asStateFlow()

    private val _typingPeer = MutableStateFlow(false)
    val typingPeer: StateFlow<Boolean> = _typingPeer.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Voice recording in progress (mic held). Max 120s, then auto-send. */
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    /** Id of the voice message currently playing (null = none). */
    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    /** Known voice durations in seconds, by message id. */
    private val _voiceDurations = MutableStateFlow<Map<String, Int>>(emptyMap())
    val voiceDurations: StateFlow<Map<String, Int>> = _voiceDurations.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _replyTo = MutableStateFlow<ChatMessage?>(null)
    val replyTo: StateFlow<ChatMessage?> = _replyTo.asStateFlow()

    /**
     * Delivered is sticky within the session: once the peer is seen online,
     * my messages never revert to a single tick if they go offline again
     * (same as the web app's deliveredIds set).
     */
    private val _deliveredIds = MutableStateFlow<Set<String>>(emptySet())
    val deliveredIds: StateFlow<Set<String>> = _deliveredIds.asStateFlow()

    fun markDelivered(ids: List<String>) {
        _deliveredIds.update { it + ids }
    }

    private var typingHideJob: Job? = null
    private var typingDebounceJob: Job? = null
    private var typingLastSentAt = 0L
    private var visible = false

    // View-once media: recorder + player live here so a rotation or a
    // re-render never loses them. Consumed ids guard double-consume.
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingJob: Job? = null
    private var player: MediaPlayer? = null
    private var playingId: String? = null
    private val consumedIds = mutableSetOf<String>()
    // View-once ids the recipient tapped Keep on. Added synchronously in
    // keepMessage() (before the network call) so a photo-viewer close or
    // a voice finishing a moment later can never consume a kept message.
    private val keptIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val (peerProfile, peerRead) = repo.loadPeer(conversationId)
            _peer.value = peerProfile
            _peerLastReadAt.value = peerRead
            loadMessages()
            startPeerReadFlow()
            startTypingFlows()
        }
        viewModelScope.launch {
            SessionManager.feedTick.collect {
                delay(250)
                // loadMessages() marks read once the fresh list lands
                // (server watermark) — no separate call here, or the
                // mark would run twice per tick with a stale watermark.
                loadMessages()
            }
        }
    }

    /** The chat screen is actually on screen (foreground) — drives mark-read. */
    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) markReadIfVisible()
    }

    fun loadMessages() {
        viewModelScope.launch {
            try {
                val list = repo.loadMessages(conversationId)
                _messages.value = list
                val ids = list.map { it.id }
                _reactions.value = repo.loadReactions(conversationId, ids)
                // Messages are in: mark read with the fresh server
                // watermark. This is the call that matters — the
                // open-time mark (setVisible) usually runs before the
                // first load, when no server timestamp is known yet.
                markReadIfVisible()
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setReplyTo(message: ChatMessage?) {
        _replyTo.value = message
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() || _sending.value) return
        val replyId = _replyTo.value?.id
        _replyTo.value = null
        onTypingStopped()
        viewModelScope.launch {
            _sending.value = true
            val sent = try {
                repo.sendMessage(conversationId, body, replyId)
            } catch (e: Exception) {
                _error.value = "Could not send message. Check your connection and try again."
                null
            }
            if (sent != null) {
                _messages.update { it + sent }
                notifyPeer(body)
            } else {
                loadMessages()
            }
            _sending.value = false
        }
    }

    /** WhatsApp-style push: tell the peer a message arrived (best-effort). */
    private fun notifyPeer(preview: String) {
        val token = _peer.value?.fcmToken ?: return
        val myName = SessionManager.profile.value?.fullName?.ifBlank {
            SessionManager.profile.value?.username
        } ?: "PingChat"
        viewModelScope.launch {
            PushSender.sendPush(
                token, myName, preview,
                mapOf("type" to "message", "conversationId" to conversationId)
            )
        }
    }

    /** Send a view-once photo picked from the gallery. */
    fun sendPhoto(bytes: ByteArray, mime: String) {
        if (_sending.value) return
        viewModelScope.launch {
            _sending.value = true
            val sent = repo.sendPhoto(conversationId, bytes, mime)
            if (sent != null) {
                _messages.update { it + sent }
                notifyPeer("Sent a photo")
            } else {
                _error.value = "Could not send photo. Check your connection and try again."
            }
            _sending.value = false
        }
    }

    // ---------- view-once voice: record + play ----------

    /** Start recording a voice message into outputFile (max 120s, then auto-send). */
    @Suppress("DEPRECATION")
    fun startRecording(outputFile: File) {
        if (_recording.value) return
        try {
            val rec = MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(outputFile.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            recordingFile = outputFile
            _recording.value = true
            _recordingSeconds.value = 0
            recordingJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    val next = _recordingSeconds.value + 1
                    _recordingSeconds.value = next
                    if (next >= 120) {
                        stopRecordingAndSend()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            _error.value = "Could not start recording. Check the microphone permission and try again."
        }
    }

    /** Stop recording and send the voice message (view-once). */
    fun stopRecordingAndSend() {
        val rec = recorder ?: return
        recorder = null
        recordingJob?.cancel()
        recordingJob = null
        _recording.value = false
        val file = recordingFile
        recordingFile = null
        viewModelScope.launch {
            val bytes = try {
                rec.stop()
                rec.release()
                withContext(Dispatchers.IO) { file?.readBytes() }
            } catch (e: Exception) {
                try {
                    rec.release()
                } catch (_: Exception) {
                }
                null
            }
            file?.delete()
            if (bytes == null || bytes.isEmpty()) {
                _error.value = "Could not record voice message. Try again."
                return@launch
            }
            _sending.value = true
            val sent = repo.sendVoice(conversationId, bytes)
            if (sent != null) {
                _messages.update { it + sent }
                notifyPeer("Sent a voice message")
            } else {
                _error.value = "Could not send voice message. Check your connection and try again."
            }
            _sending.value = false
        }
    }

    /** Play a voice message (tap again to stop). A full listen consumes it. */
    fun playVoice(message: ChatMessage) {
        val url = message.mediaUrl
        if (url.isNullOrBlank()) return
        if (playingId == message.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            val mp = MediaPlayer()
            playingId = message.id
            _playingMessageId.value = message.id
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                _voiceDurations.update { it + (message.id to (mp.duration / 1000)) }
                mp.start()
            }
            mp.setOnCompletionListener {
                stopPlayback()
                // Full listen: view-once consume (recipient only).
                consumeViewOnce(message)
            }
            mp.setOnErrorListener { _, _, _ ->
                stopPlayback()
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            stopPlayback()
            _error.value = "Could not play voice message."
        }
    }

    fun stopPlayback() {
        playingId = null
        _playingMessageId.value = null
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    /**
     * View-once consume: the recipient closing a photo or finishing a
     * voice message deletes the message doc + storage file (the repo
     * refuses the sender / already-consumed / non-participant).
     * Never consumes my own sent media, and never consumes a message
     * the recipient kept — the kept flag is resolved against the live
     * list (the snapshot passed in may predate a Keep tap) and the
     * consume RPC itself refuses kept rows as a server-side backstop.
     */
    fun consumeViewOnce(message: ChatMessage) {
        if (!message.viewOnce) return
        if (message.senderId == myId) return
        if (message.kept || keptIds.contains(message.id)) return
        // Resolve the live row: a Keep tap updates _messages, but the
        // snapshot handed to this call (e.g. from the photo viewer or
        // the voice completion listener) may still say kept = false.
        val current = _messages.value.firstOrNull { it.id == message.id }
        if (current?.kept == true) return
        if (!consumedIds.add(message.id)) return
        viewModelScope.launch {
            if (repo.consumeMessage(conversationId, message.id)) {
                _messages.update { list -> list.filterNot { it.id == message.id } }
                val path = message.mediaPath
                if (path != null) {
                    // Let the fullscreen photo finish loading from its URL
                    // before the file disappears from Storage.
                    delay(5000)
                    val bucket = if (message.kind == "photo") "chat-photos" else "voice-messages"
                    repo.deleteMedia(bucket, path)
                }
            }
        }
    }

    /**
     * Keep a view-once message in the chat (recipient only): the message
     * is NOT deleted after viewing/listening and renders as a normal
     * photo/voice message afterwards. "Keep" means keep in the chat —
     * it does not save a copy to the device. The local list is marked
     * kept synchronously so a consume triggered a moment later (photo
     * viewer closing, voice finishing) cannot race the network call;
     * if the update fails, the flag is rolled back.
     */
    fun keepMessage(message: ChatMessage) {
        if (!message.viewOnce) return
        if (message.senderId == myId) return
        if (message.kept || !keptIds.add(message.id)) return
        _messages.update { list ->
            list.map { if (it.id == message.id) it.copy(kept = true) else it }
        }
        viewModelScope.launch {
            if (!repo.keepMessage(message.id)) {
                keptIds.remove(message.id)
                _messages.update { list ->
                    list.map { if (it.id == message.id) it.copy(kept = false) else it }
                }
                _error.value = "Could not keep this message. Try again."
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                repo.toggleReaction(conversationId, messageId, emoji)
                _reactions.value = repo.loadReactions(conversationId, _messages.value.map { it.id })
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    fun deleteForMe(messageId: String) {
        viewModelScope.launch {
            if (repo.deleteForMe(conversationId, messageId)) {
                _messages.update { list -> list.filterNot { it.id == messageId } }
            }
        }
    }

    fun deleteForEveryone(messageId: String) {
        viewModelScope.launch {
            try {
                repo.deleteForEveryone(conversationId, messageId)
                _messages.update { list -> list.filterNot { it.id == messageId } }
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            try {
                repo.clearChat(conversationId)
                _messages.value = emptyList()
                _reactions.value = emptyMap()
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    /** Tick state for one of MY messages — same 3 states as the web app. */
    fun tickState(message: ChatMessage, peerOnline: Boolean, deliveredIds: Set<String>): TickState {
        val peerRead = _peerLastReadAt.value
        val seen = peerRead != null &&
            com.mdhasibulislam.pingchat.data.TimeFormat.parse(peerRead) != null &&
            com.mdhasibulislam.pingchat.data.TimeFormat.parse(message.createdAt) != null &&
            !com.mdhasibulislam.pingchat.data.TimeFormat.parse(peerRead)!!
                .isBefore(com.mdhasibulislam.pingchat.data.TimeFormat.parse(message.createdAt)!!)
        if (seen) return TickState.SEEN
        if (peerOnline || deliveredIds.contains(message.id)) return TickState.DELIVERED
        return TickState.SENT
    }

    // ---------- typing (leading-edge send + throttled 2.5s, like the web app) ----------

    fun onTyping() {
        val now = System.currentTimeMillis()
        if (now - typingLastSentAt >= 2500) {
            // Leading edge: broadcast immediately on the first keystroke.
            // The old code debounced 300ms — every keystroke cancelled the
            // pending send, so while the user typed continuously (or typed
            // and hit send within 300ms) NO "typing" broadcast ever went
            // out and the peer never saw the indicator. The web app sends
            // on input with no debounce; this matches it.
            typingDebounceJob?.cancel()
            typingLastSentAt = now
            sendTypingNow()
            return
        }
        // Inside the 2.5s throttle window: schedule ONE trailing re-send
        // at the end of the window so the peer's 3s hide-timeout does not
        // expire while I keep typing. A job is already pending: leave it.
        if (typingDebounceJob?.isActive == true) return
        val waitMs = 2500 - (now - typingLastSentAt)
        typingDebounceJob = viewModelScope.launch {
            delay(waitMs)
            typingLastSentAt = System.currentTimeMillis()
            sendTypingNow()
        }
    }

    /** One "typing" broadcast (best-effort, never throws to the caller). */
    private fun sendTypingNow() {
        val me = repo.myId() ?: return
        val name = SessionManager.profile.value?.fullName ?: "Someone"
        viewModelScope.launch {
            repo.sendTyping(conversationId, TypingPayload(me, name))
        }
    }

    fun onTypingStopped() {
        typingDebounceJob?.cancel()
        typingLastSentAt = 0
        val me = repo.myId() ?: return
        viewModelScope.launch {
            repo.sendTypingStop(conversationId, TypingPayload(me))
        }
    }

    /**
     * Mark this chat read — but only with a SERVER timestamp as the
     * watermark. unreadCount counts peer messages with created_at >
     * last_read_at, and created_at is server time, so writing the
     * device clock here left the newest message unread whenever the
     * device clock trailed the server (and re-opening the chat re-wrote
     * the same lagging time, so the badge never cleared). The watermark
     * is the newest loaded message's created_at; a message created
     * after it still counts as unread, exactly as it should. Falls
     * back to device time only when no messages are loaded yet — the
     * post-load mark in loadMessages() then corrects it. On success,
     * pokes the chats list: a participants UPDATE fires no feed tick,
     * so without this the list kept the stale count until the next
     * message event or tab re-entry. The peer's seen tick is untouched
     * — it is driven by this same last_read_at write (peerReadFlow).
     */
    private fun markReadIfVisible() {
        if (!visible) return
        val readUpTo = _messages.value.mapNotNull { TimeFormat.parse(it.createdAt) }
            .maxOrNull() ?: Instant.now()
        viewModelScope.launch {
            if (repo.markRead(conversationId, readUpTo)) {
                SessionManager.notifyRead()
            }
        }
    }

    /** Live peer read-marker for this conversation (drives the seen badge). */
    private fun startPeerReadFlow() {
        viewModelScope.launch {
            try {
                repo.peerReadFlow(conversationId).collect { lastRead ->
                    _peerLastReadAt.value = lastRead
                }
            } catch (e: Exception) {
                // read markers are best-effort
            }
        }
    }

    /** Live typing indicator from the peer (Realtime Broadcast). */
    private fun startTypingFlows() {
        viewModelScope.launch {
            try {
                repo.typingFlow(conversationId).collect { payload ->
                    if (payload.userId == repo.myId()) return@collect
                    _typingPeer.value = true
                    typingHideJob?.cancel()
                    typingHideJob = viewModelScope.launch {
                        delay(3000) // peer hides the indicator after 3s of silence
                        _typingPeer.value = false
                    }
                }
            } catch (e: Exception) {
                // typing is best-effort
            }
        }
        viewModelScope.launch {
            try {
                repo.typingStopFlow(conversationId).collect { payload ->
                    if (payload.userId == repo.myId()) return@collect
                    typingHideJob?.cancel()
                    _typingPeer.value = false
                }
            } catch (e: Exception) {
                // typing is best-effort
            }
        }
    }

    override fun onCleared() {
        recordingJob?.cancel()
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        stopPlayback()
        super.onCleared()
    }
}
