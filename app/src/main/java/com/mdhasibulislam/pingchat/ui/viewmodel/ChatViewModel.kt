package com.mdhasibulislam.pingchat.ui.viewmodel

import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.AutoDeleteTimer
import com.mdhasibulislam.pingchat.data.ChatMessage
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.ConversationItem
import com.mdhasibulislam.pingchat.data.GroupInfo
import com.mdhasibulislam.pingchat.data.PresenceState
import com.mdhasibulislam.pingchat.data.PrivacyRepository
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
 * One open chat: messages, read ticks, typing, reactions, deletes.
 * Mirrors js/chats.js (openConversation + ticks + typing).
 * v3.1.0 (worker E): also group chats (sender names, no calls), message
 * edit, forward, in-chat search and message pin. Group read receipts are
 * NOT tracked — tickState stays 1-to-1 only (peer read marker).
 */
class ChatViewModel(private val conversationId: String) : ViewModel() {
    private val repo = ChatRepository()
    private val groupRepo = com.mdhasibulislam.pingchat.data.GroupRepository()
    private val privacyRepo = PrivacyRepository()

    val myId: String? get() = repo.myId()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _peer = MutableStateFlow<Profile?>(null)
    val peer: StateFlow<Profile?> = _peer.asStateFlow()

    /** v3.1.0: true when this conversation is a group (header, bubbles). */
    private val _isGroup = MutableStateFlow(false)
    val isGroup: StateFlow<Boolean> = _isGroup.asStateFlow()

    /** v3.1.0: group name / photo (null for 1-to-1 chats). */
    private val _groupName = MutableStateFlow<String?>(null)
    val groupName: StateFlow<String?> = _groupName.asStateFlow()
    private val _groupAvatarUrl = MutableStateFlow<String?>(null)
    val groupAvatarUrl: StateFlow<String?> = _groupAvatarUrl.asStateFlow()

    /** v3.1.0: group members (sender names on bubbles + push targets). */
    private val _groupInfo = MutableStateFlow<GroupInfo?>(null)
    val groupInfo: StateFlow<GroupInfo?> = _groupInfo.asStateFlow()

    /** Sender id -> display name, for group bubbles. */
    val senderNames: Map<String, String>
        get() = _groupInfo.value?.members?.mapNotNull { member ->
            val profile = member.profile ?: return@mapNotNull null
            member.participant.userId to (profile.fullName.ifBlank { profile.username })
        }?.toMap() ?: emptyMap()

    /** v3.1.0: may I see the peer's last seen? (worker A owns the
     *  setting; PrivacyRepository is the fallback until it lands.)
     *  Default true — the header only shows "Last seen …" when this is
     *  true AND the peer is offline AND has a last_seen timestamp. */
    private val _canSeePeerLastSeen = MutableStateFlow(true)
    val canSeePeerLastSeen: StateFlow<Boolean> = _canSeePeerLastSeen.asStateFlow()

    /**
     * v3.2.4 (bug 1): this chat's per-chat auto-delete timer (Off when
     * unset), loaded when the chat opens. The peer profile screen
     * shows it and saves changes via [setAutoDeleteTimer]. 1-to-1
     * only — the peer profile screen does not exist for groups.
     */
    private val _autoDeleteTimer = MutableStateFlow(AutoDeleteTimer.OFF)
    val autoDeleteTimer: StateFlow<AutoDeleteTimer> = _autoDeleteTimer.asStateFlow()

    /** Change this chat's auto-delete timer (peer profile screen).
     *  Optimistic: the picker closes at once; on failure the old
     *  value is restored and the error is shown. */
    fun setAutoDeleteTimer(timer: AutoDeleteTimer) {
        val previous = _autoDeleteTimer.value
        if (timer == previous) return
        _autoDeleteTimer.value = timer
        viewModelScope.launch {
            try {
                privacyRepo.setConversationAutoDelete(conversationId, timer)
            } catch (e: Exception) {
                _autoDeleteTimer.value = previous
                _error.value = "Could not change the disappearing-messages setting. Try again."
            }
        }
    }

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

    // ---------- v3.1.0: edit / forward / search / pin ----------

    /** Message being edited (composer shows edit mode while non-null). */
    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    /** The pinned message of this chat (banner above the messages). */
    private val _pinnedMessage = MutableStateFlow<ChatMessage?>(null)
    val pinnedMessage: StateFlow<ChatMessage?> = _pinnedMessage.asStateFlow()

    /** In-chat search: current query (empty = search closed). */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Ids of loaded messages matching the query, oldest first. The
     *  search runs over the loaded messages only (loadMessages caps at
     *  200) — no server query, so it works offline from cache too. */
    private val _searchMatches = MutableStateFlow<List<String>>(emptyList())
    val searchMatches: StateFlow<List<String>> = _searchMatches.asStateFlow()

    /** Index into [searchMatches] currently jumped to (-1 = none yet). */
    private val _searchMatchIndex = MutableStateFlow(-1)
    val searchMatchIndex: StateFlow<Int> = _searchMatchIndex.asStateFlow()

    /** Chats a message can be forwarded to (all mine except this one). */
    private val _forwardTargets = MutableStateFlow<List<ConversationItem>>(emptyList())
    val forwardTargets: StateFlow<List<ConversationItem>> = _forwardTargets.asStateFlow()

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

    /**
     * v3.2.0 Privacy — my own Read Receipts / Typing Indicator
     * settings, loaded once when the chat opens (fresh per chat: the
     * ViewModel is created per conversation). Permissive defaults
     * (true) until the load finishes; markRead waits for the load so
     * an OFF setting is never missed by a mark that fires first.
     * Loaded in its own coroutine so a slow settings read never
     * delays the messages.
     */
    private var readReceiptsEnabled = true
    private var typingIndicatorEnabled = true
    private var privacySettingsLoaded = false

    // Voice media: recorder + player live here so a rotation or a
    // re-render never loses them.
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingJob: Job? = null
    private var player: MediaPlayer? = null
    private var playingId: String? = null

    init {
        // v3.2.0 Privacy — load my Read Receipts / Typing Indicator
        // settings (see the flags above). Separate coroutine: a slow
        // settings read must never delay the messages below.
        viewModelScope.launch {
            try {
                val settings = privacyRepo.loadMySettings()
                readReceiptsEnabled = settings.readReceipts
                typingIndicatorEnabled = settings.typingIndicator
            } catch (e: Exception) {
                // permissive defaults — the settings stay on
            } finally {
                privacySettingsLoaded = true
            }
        }
        viewModelScope.launch {
            val conv = repo.loadConversation(conversationId)
            if (conv?.isGroup == true) {
                _isGroup.value = true
                _groupName.value = conv.groupName
                _groupAvatarUrl.value = conv.groupAvatarUrl
                _groupInfo.value = groupRepo.loadGroupInfo(conversationId)
            } else {
                val (peerProfile, peerRead) = repo.loadPeer(conversationId)
                _peer.value = peerProfile
                _peerLastReadAt.value = peerRead
                // Last-seen visibility (worker A): only check for 1-to-1.
                if (peerProfile != null) {
                    _canSeePeerLastSeen.value =
                        privacyRepo.canSeeLastSeen(peerProfile.id)
                    // v3.2.4 (bug 1): this chat's auto-delete timer
                    // (Off when unset / migration not run yet).
                    _autoDeleteTimer.value =
                        privacyRepo.getConversationAutoDelete(conversationId)
                }
            }
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
                _pinnedMessage.value = repo.loadPinnedMessage(conversationId)
                refreshSearchMatches()
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

    /** WhatsApp-style push: tell the peer a message arrived (best-effort).
     *  Groups: push every OTHER member (each with their own token) with
     *  the group name as the title. Muted members never get the push in
     *  the first place only when the sender knows — the receiver side
     *  (PingChatMessagingService, Prefs.mutedConversationIds) is the
     *  backstop, so muting works even for pushes sent before muting. */
    private fun notifyPeer(preview: String) {
        val myName = SessionManager.profile.value?.fullName?.ifBlank {
            SessionManager.profile.value?.username
        } ?: "PingChat"
        viewModelScope.launch {
            if (_isGroup.value) {
                val title = _groupName.value?.takeIf { it.isNotBlank() } ?: "Group"
                val members = _groupInfo.value?.members ?: return@launch
                for (member in members) {
                    val profile = member.profile ?: continue
                    if (profile.id == myId) continue
                    val token = profile.fcmToken ?: continue
                    PushSender.sendPush(
                        token, "$myName in $title", preview,
                        mapOf("type" to "message", "conversationId" to conversationId)
                    )
                }
            } else {
                val token = _peer.value?.fcmToken ?: return@launch
                PushSender.sendPush(
                    token, myName, preview,
                    mapOf("type" to "message", "conversationId" to conversationId)
                )
            }
        }
    }

    /** Send a photo picked from the gallery. */
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

    // ---------- voice: record + play ----------

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

    /** Stop recording and send the voice message. */
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

    /** Play a voice message (tap again to stop). */
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

    // ---------- v3.1.0: edit / forward / search / pin ----------

    /** Start editing my own text message (composer switches to edit mode). */
    fun startEdit(message: ChatMessage) {
        if (message.kind != "text" || message.senderId != myId) return
        _editingMessage.value = message
    }

    fun cancelEdit() {
        _editingMessage.value = null
    }

    /** Save the edit (RPC: sender-only, text-only). The local row is
     *  updated at once with edited_at = now so the "edited" label shows
     *  without waiting for the realtime UPDATE to round-trip. */
    fun saveEdit(newBody: String) {
        val target = _editingMessage.value ?: return
        val body = newBody.trim()
        if (body.isEmpty() || body == target.body) {
            _editingMessage.value = null
            return
        }
        _editingMessage.value = null
        viewModelScope.launch {
            if (repo.editMessage(target.id, body)) {
                val editedAt = Instant.now().toString()
                _messages.update { list ->
                    list.map {
                        if (it.id == target.id) it.copy(body = body, editedAt = editedAt)
                        else it
                    }
                }
            } else {
                _error.value = "Could not edit this message. Try again."
            }
        }
    }

    /** Chats a message can be forwarded to (loaded when the picker opens). */
    fun loadForwardTargets() {
        viewModelScope.launch {
            _forwardTargets.value = try {
                repo.loadConversations().filter { it.conversation.id != conversationId }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Forward [message] into [targetConversationId]. Checks
     * PrivacyRepository.canForwardMessage(original sender) first — the
     * forward setting belongs to the ORIGINAL sender (worker A). The
     * RPC then copies the row (refusing view-once / non-members /
     * blocks) and the forwarded copy shows a "Forwarded" label.
     * Returns true when the copy landed.
     */
    fun forwardMessage(message: ChatMessage, targetConversationId: String) {
        viewModelScope.launch {
            if (!privacyRepo.canForwardMessage(message.senderId)) {
                _error.value = "This message cannot be forwarded."
                return@launch
            }
            val newId = repo.forwardMessage(message.id, targetConversationId)
            if (newId == null) {
                _error.value = "Could not forward this message. Try again."
            } else if (targetConversationId == conversationId) {
                loadMessages()
            }
        }
    }

    /** Pin this message (unpins the previous one) or unpin it. */
    fun togglePin(message: ChatMessage) {
        viewModelScope.launch {
            val pin = !message.isPinned
            if (!repo.setMessagePinned(message.id, pin)) {
                _error.value = "Could not ${if (pin) "pin" else "unpin"} this message. Try again."
                return@launch
            }
            if (pin) {
                _pinnedMessage.value = message.copy(isPinned = true)
                _messages.update { list ->
                    list.map {
                        when (it.id) {
                            message.id -> it.copy(isPinned = true)
                            else -> if (it.isPinned) it.copy(isPinned = false) else it
                        }
                    }
                }
            } else {
                _pinnedMessage.value = null
                _messages.update { list ->
                    list.map { if (it.id == message.id) it.copy(isPinned = false) else it }
                }
            }
        }
    }

    /** Unpin the currently pinned message (banner's dismiss action). */
    fun unpinCurrent() {
        val pinned = _pinnedMessage.value ?: return
        togglePin(pinned)
    }

    /** In-chat search: set the query and recompute matches. Empty query
     *  closes the search. Matches are text-message bodies containing the
     *  query (case-insensitive), oldest first. */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        refreshSearchMatches()
    }

    private fun refreshSearchMatches() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) {
            _searchMatches.value = emptyList()
            _searchMatchIndex.value = -1
            return
        }
        val matches = _messages.value
            .filter { it.kind == "text" && it.body.contains(q, ignoreCase = true) }
            .map { it.id }
        val previous = _searchMatches.value
        _searchMatches.value = matches
        // Keep the current position when the list only grew (a new
        // message arrived); otherwise restart from the first match.
        _searchMatchIndex.value = if (matches == previous) _searchMatchIndex.value else 0
        if (matches.isEmpty()) _searchMatchIndex.value = -1
    }

    /** Id of the match the UI should jump to, or null. */
    val currentSearchMatchId: String?
        get() {
            val idx = _searchMatchIndex.value
            val list = _searchMatches.value
            return if (idx in list.indices) list[idx] else null
        }

    fun nextSearchMatch() {
        val list = _searchMatches.value
        if (list.isEmpty()) return
        _searchMatchIndex.value = (_searchMatchIndex.value + 1) % list.size
    }

    fun previousSearchMatch() {
        val list = _searchMatches.value
        if (list.isEmpty()) return
        val cur = _searchMatchIndex.value
        _searchMatchIndex.value = if (cur <= 0) list.size - 1 else cur - 1
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
        // v3.2.0 Privacy — Typing Indicator OFF: broadcast nothing
        // (and wait for the settings load first, so an OFF setting is
        // never missed by a broadcast that fires first).
        if (!privacySettingsLoaded || !typingIndicatorEnabled) return
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
        // v3.2.0 Privacy — Typing Indicator OFF: nothing was ever
        // broadcast, so there is no "stop" to send either.
        if (!privacySettingsLoaded || !typingIndicatorEnabled) return
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
        // v3.2.0 Privacy — Read Receipts OFF: never write last_read_at,
        // so the peer sees no read ticks. Waits for the settings load
        // first, so an OFF setting is never missed by a mark that fires
        // first. NOTE: the same last_read_at drives MY chats-list
        // unread badge, so with receipts off the badge for this chat
        // only clears when receipts are turned back on and the chat is
        // opened again — there is no separate local-only read flag.
        if (!privacySettingsLoaded || !readReceiptsEnabled) return
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
