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
import com.mdhasibulislam.pingchat.data.Reaction
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.TickState
import com.mdhasibulislam.pingchat.data.TypingPayload
import io.github.jan.supabase.realtime.RealtimeChannel
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

    private var roomChannel: RealtimeChannel? = null
    private var typingChannel: RealtimeChannel? = null
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

    init {
        viewModelScope.launch {
            val (peerProfile, peerRead) = repo.loadPeer(conversationId)
            _peer.value = peerProfile
            _peerLastReadAt.value = peerRead
            loadMessages()
            startRoomChannel()
            startTypingChannel()
        }
        viewModelScope.launch {
            SessionManager.feedTick.collect {
                delay(250)
                loadMessages()
                markReadIfVisible()
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
                _reactions.value = repo.loadReactions(ids)
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
            } else {
                loadMessages()
            }
            _sending.value = false
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
     * View-once consume: the recipient opening a photo or finishing a
     * voice message deletes the row from the database (via the RPC,
     * which refuses the sender / already-consumed / non-participant).
     * Never consumes my own sent media.
     */
    fun consumeViewOnce(message: ChatMessage) {
        if (!message.viewOnce) return
        if (message.senderId == myId) return
        if (!consumedIds.add(message.id)) return
        viewModelScope.launch {
            if (repo.consumeMessage(message.id)) {
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

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                repo.toggleReaction(messageId, emoji)
                _reactions.value = repo.loadReactions(_messages.value.map { it.id })
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    fun deleteForMe(messageId: String) {
        viewModelScope.launch {
            if (repo.deleteForMe(messageId)) {
                _messages.update { list -> list.filterNot { it.id == messageId } }
            }
        }
    }

    fun deleteForEveryone(messageId: String) {
        viewModelScope.launch {
            try {
                repo.deleteForEveryone(messageId)
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

    // ---------- typing (debounced 300ms + throttled 2.5s, like the web app) ----------

    fun onTyping() {
        val channel = typingChannel ?: return
        typingDebounceJob?.cancel()
        typingDebounceJob = viewModelScope.launch {
            delay(300)
            val now = System.currentTimeMillis()
            if (now - typingLastSentAt < 2500) return@launch
            typingLastSentAt = now
            val me = repo.myId() ?: return@launch
            val name = SessionManager.profile.value?.fullName ?: "Someone"
            repo.sendTyping(channel, TypingPayload(me, name))
        }
    }

    fun onTypingStopped() {
        typingDebounceJob?.cancel()
        typingLastSentAt = 0
        val channel = typingChannel ?: return
        val me = repo.myId() ?: return
        viewModelScope.launch {
            repo.sendTypingStop(channel, TypingPayload(me))
        }
    }

    private fun markReadIfVisible() {
        if (!visible) return
        viewModelScope.launch {
            repo.markRead(conversationId)
        }
    }

    private fun startRoomChannel() {
        val channel = repo.roomChannel(conversationId)
        roomChannel = channel
        viewModelScope.launch {
            try {
                repo.peerReadFlow(channel, conversationId).collect { lastRead ->
                    _peerLastReadAt.value = lastRead
                }
            } catch (e: Exception) {
                // read markers are best-effort
            }
        }
        viewModelScope.launch {
            try {
                channel.subscribe()
            } catch (e: Exception) {
                // offline
            }
        }
    }

    private fun startTypingChannel() {
        val channel = repo.typingChannel(conversationId)
        typingChannel = channel
        viewModelScope.launch {
            try {
                repo.typingFlow(channel).collect { payload ->
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
                repo.typingStopFlow(channel).collect { payload ->
                    if (payload.userId == repo.myId()) return@collect
                    typingHideJob?.cancel()
                    _typingPeer.value = false
                }
            } catch (e: Exception) {
                // typing is best-effort
            }
        }
        viewModelScope.launch {
            try {
                channel.subscribe()
            } catch (e: Exception) {
                // offline
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
        val room = roomChannel
        val typing = typingChannel
        roomChannel = null
        typingChannel = null
        viewModelScope.launch {
            try {
                room?.unsubscribe()
            } catch (e: Exception) {
            }
            try {
                typing?.unsubscribe()
            } catch (e: Exception) {
            }
        }
        super.onCleared()
    }
}
