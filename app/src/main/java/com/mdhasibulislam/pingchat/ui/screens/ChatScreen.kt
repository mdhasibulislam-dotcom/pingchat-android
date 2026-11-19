package com.mdhasibulislam.pingchat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.mdhasibulislam.pingchat.data.ChatMessage
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.ConversationItem
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.PushSender
import com.mdhasibulislam.pingchat.data.Reaction
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.TickState
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.data.PrivacyRepository
import com.mdhasibulislam.pingchat.data.VoicePlaybackManager
import com.mdhasibulislam.pingchat.data.messageDisplayText
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.components.TickIcon
import com.mdhasibulislam.pingchat.ui.components.TypingIndicator
import com.mdhasibulislam.pingchat.ui.components.VoiceMessageBubble
import com.mdhasibulislam.pingchat.ui.components.VoiceMicButton
import com.mdhasibulislam.pingchat.ui.components.VoiceMiniPlayer
import com.mdhasibulislam.pingchat.ui.components.VoiceRecorder
import com.mdhasibulislam.pingchat.ui.components.VoiceRecorderBar
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.WhatsAppNoticeBgDark
import com.mdhasibulislam.pingchat.ui.theme.WhatsAppNoticeBgLight
import com.mdhasibulislam.pingchat.ui.theme.WhatsAppTickGrey
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.CallViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class ChatViewModelFactory(private val conversationId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(conversationId) as T
    }
}

private sealed interface ChatListItem {
    data class Day(val text: String, val anchorId: String) : ChatListItem
    data class Gap(val text: String, val anchorId: String) : ChatListItem
    data class Msg(val message: ChatMessage) : ChatListItem
}

private fun buildDisplayItems(messages: List<ChatMessage>): List<ChatListItem> {
    // WhatsApp-style: only a day pill ("Today" / "Yesterday" / "18 Nov")
    // when the day changes. No time pills between messages — the time
    // lives inside each bubble, bottom-right.
    val out = mutableListOf<ChatListItem>()
    var prev: ChatMessage? = null
    for (m in messages) {
        val p = prev
        if (p == null || !TimeFormat.isSameDay(p.createdAt, m.createdAt)) {
            val label = TimeFormat.dayLabelWhatsApp(m.createdAt)
            if (label.isNotBlank()) out += ChatListItem.Day(label, m.id)
        }
        out += ChatListItem.Msg(m)
        prev = m
    }
    return out
}

private val ReactionEmojis = listOf("❤️", "😂", "😮", "😢", "😡", "👍")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit
) {
    val factory = remember(conversationId) { ChatViewModelFactory(conversationId) }
    val viewModel: ChatViewModel = viewModel(factory = factory)
    val messages by viewModel.messages.collectAsState()
    val peer by viewModel.peer.collectAsState()
    val peerLastReadAt by viewModel.peerLastReadAt.collectAsState()
    val reactions by viewModel.reactions.collectAsState()
    val typingPeer by viewModel.typingPeer.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val error by viewModel.error.collectAsState()
    val replyTo by viewModel.replyTo.collectAsState()
    val deliveredIds by viewModel.deliveredIds.collectAsState()
    val voiceDurations by viewModel.voiceDurations.collectAsState()
    // v3.1.0 voice (worker D): the global VoicePlaybackManager owns
    // playback and the VoiceRecorder owns recording — the ViewModel's
    // recording/playing flows are no longer read here.
    val voiceRecorder = remember { VoiceRecorder() }
    val isVoiceRecording by voiceRecorder.isRecording.collectAsState()
    val voiceLocked by voiceRecorder.locked.collectAsState()
    val playbackState by VoicePlaybackManager.state.collectAsState()
    var voiceAllowed by remember { mutableStateOf(true) }
    var voiceSending by remember { mutableStateOf(false) }
    val chatRepo = remember { ChatRepository() }
    val privacyRepo = remember { PrivacyRepository() }
    val onlineIds by SessionManager.onlineIds.collectAsState()
    val myId = viewModel.myId
    val colors = pingChatColors
    // v3.1.0: group header / edit / forward / search / pin state.
    val isGroup by viewModel.isGroup.collectAsState()
    val groupName by viewModel.groupName.collectAsState()
    val groupAvatarUrl by viewModel.groupAvatarUrl.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val pinnedMessage by viewModel.pinnedMessage.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchMatches by viewModel.searchMatches.collectAsState()
    val searchMatchIndex by viewModel.searchMatchIndex.collectAsState()
    val forwardTargets by viewModel.forwardTargets.collectAsState()
    val canSeePeerLastSeen by viewModel.canSeePeerLastSeen.collectAsState()
    val senderNames = viewModel.senderNames

    val peerOnline = peer?.let { onlineIds.contains(it.id) } ?: false
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var menuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf<ChatMessage?>(null) }
    // v3.1.0: search bar, forward picker, group settings overlay.
    var showSearch by remember { mutableStateOf(false) }
    var forwardMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showGroupSettings by remember { mutableStateOf(false) }

    // Gallery picker (photo only — no video, no camera in this version).
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        viewModel.sendPhoto(bytes, mime)
    }

    // Mic permission, then start recording into a temp file (v3.1.0:
    // VoiceRecorder owns the MediaRecorder, so swipe-to-cancel works —
    // the old ViewModel recorder had no cancel path).
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            voiceRecorder.start(File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a"))
        } else {
            Toast.makeText(
                context,
                "Microphone permission is needed to record voice messages.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    /** Returns true when recording actually started. */
    fun requestMicAndRecord(): Boolean {
        if (!voiceAllowed) {
            Toast.makeText(context, "Voice messages are not allowed in this chat.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val started = voiceRecorder.start(File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a"))
            if (!started) {
                Toast.makeText(context, "Could not start recording. Try again.", Toast.LENGTH_SHORT).show()
            }
            return started
        }
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        return false
    }

    // Audio/video calls: mic permission (plus camera for video calls),
    // then start a real WebRTC call to the peer. The peer must be online.
    val callViewModel: CallViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var pendingCallKind by remember { mutableStateOf<String?>(null) }
    val callPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val kind = pendingCallKind
        pendingCallKind = null
        val p = viewModel.peer.value
        if (kind == null || p == null) return@rememberLauncherForActivityResult
        if (result.values.all { it }) {
            scope.launch {
                val err = callViewModel.startCall(p, kind, conversationId)
                if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                context,
                if (kind == "video") "Microphone and camera permission is needed for video calls."
                else "Microphone permission is needed for audio calls.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    fun requestCall(kind: String) {
        // WhatsApp-style: always let the call go out — if the other side
        // does not answer in 30s it is marked missed. Never block on the
        // online dot: presence can lag a moment, and the peer may be right
        // there with the app open.
        val p = viewModel.peer.value ?: return
        // Audio calls need the microphone only; video calls need the
        // camera too (v2.0.9: audio no longer demands CAMERA).
        val needed = if (kind == "video") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            scope.launch {
                val err = callViewModel.startCall(p, kind, conversationId)
                if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
        } else {
            pendingCallKind = kind
            callPermission.launch(needed)
        }
    }

    /** Best-effort push for a sent voice message (mirrors ChatViewModel.notifyPeer). */
    fun notifyVoicePeer() {
        val token = peer?.fcmToken ?: return
        val myName = SessionManager.profile.value?.fullName?.ifBlank {
            SessionManager.profile.value?.username
        } ?: "PingChat"
        scope.launch {
            PushSender.sendPush(
                token, myName, "Sent a voice message",
                mapOf("type" to "message", "conversationId" to conversationId)
            )
        }
    }

    // v3.1.0 voice send path (worker D): stop the VoiceRecorder and send
    // the bytes through the same backend call the old path used, then
    // refresh the list and push the peer — mirroring
    // ChatViewModel.send + notifyPeer (which are private to the VM).
    fun sendVoiceRecording() {
        if (!voiceRecorder.isRecording.value || voiceSending) return
        voiceSending = true
        scope.launch {
            val bytes = voiceRecorder.stopAndGetBytes()
            if (bytes == null || bytes.isEmpty()) {
                voiceSending = false
                Toast.makeText(context, "Could not record voice message. Try again.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sent = chatRepo.sendVoice(conversationId, bytes)
            voiceSending = false
            if (sent != null) {
                viewModel.loadMessages()
                notifyVoicePeer()
            } else {
                Toast.makeText(context, "Could not send voice message. Check your connection and try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cancelVoiceRecording() {
        if (!voiceRecorder.isRecording.value) return
        voiceRecorder.cancel()
    }

    // 120s auto-send: the recorder stops itself and hands the bytes over.
    LaunchedEffect(voiceRecorder) {
        voiceRecorder.onAutoStop = { sendVoiceRecording() }
    }

    // Voice permission (worker A: PrivacyRepository.canSendVoiceMessageTo
    // checks the peer's Voice Messages setting + blocks): the mic is
    // blocked when the peer does not allow voice messages from me.
    // In a group (peer == null) voice is always allowed.
    LaunchedEffect(peer?.id) {
        val id = peer?.id ?: return@LaunchedEffect
        voiceAllowed = privacyRepo.canSendVoiceMessageTo(id)
    }

    // Discard an in-progress recording when leaving the chat. Playback
    // is NOT stopped here — the global VoicePlaybackManager keeps
    // playing in the background (mini player), that is the feature.
    DisposableEffect(voiceRecorder) {
        onDispose { voiceRecorder.release() }
    }

    // Closing the fullscreen photo viewer just closes it. (v3.1.0:
    // no view-once — a photo stays in the chat; the server deletes
    // it 7 days after it was sent, see migration-v310-media-expiry.sql.)
    fun closePhotoViewer() {
        viewingPhoto = null
    }

    // Seen only counts when the user is actually looking at the chat.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setVisible(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setVisible(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setVisible(false)
            // Leaving mid-typing: tell the peer at once so their typing
            // indicator clears instead of lingering on its 3s timeout.
            viewModel.onTypingStopped()
        }
    }

    // Delivered is sticky: once the peer is online, my messages stay delivered.
    LaunchedEffect(peerOnline) {
        if (peerOnline && myId != null) {
            viewModel.markDelivered(messages.filter { it.senderId == myId }.map { it.id })
        }
    }

    val displayItems = remember(messages) { buildDisplayItems(messages).asReversed() }
    LaunchedEffect(messages.size) {
        // Do not yank the list while the user is jumping between search
        // matches — a new message arriving mid-search must not scroll.
        if (searchQuery.isBlank() && messages.isNotEmpty()) listState.scrollToItem(0)
    }

    // Edit mode: prefill the composer with the message being edited.
    LaunchedEffect(editingMessage) {
        val target = editingMessage
        if (target != null) input = target.body
    }

    // Search: jump to the current match (displayItems is newest-first,
    // so find the match's position there). Runs on every next/previous.
    val currentMatchId = searchMatches.getOrNull(searchMatchIndex)
    LaunchedEffect(currentMatchId) {
        val id = currentMatchId ?: return@LaunchedEffect
        val index = displayItems.indexOfFirst { it is ChatListItem.Msg && it.message.id == id }
        if (index >= 0) listState.scrollToItem(index)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.chatBg)
    ) {
        // Header, WhatsApp-style: back, avatar + name + status,
        // video call, audio call, more (profile). statusBarsPadding keeps
        // the header below the system status bar (targetSdk 37 is
        // edge-to-edge, so content otherwise draws under the clock/battery).
        // v3.1.0: groups show the group photo + name + member count and
        // NO call buttons (calls stay 1-to-1); tapping a group header
        // opens group settings. The 1-to-1 status line shows "Last seen …"
        // only when PrivacyRepository.canSeeLastSeen allows it (worker A
        // owns that setting — the check lives in ChatViewModel).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            val headerProfile = if (isGroup) {
                Profile(
                    id = conversationId,
                    username = groupName ?: "Group",
                    fullName = groupName ?: "Group",
                    avatarUrl = groupAvatarUrl
                )
            } else {
                peer
            }
            Avatar(profile = headerProfile, size = AvatarSize.SM, online = if (isGroup) null else peerOnline)
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isGroup) Modifier.clickable { showGroupSettings = true }
                        else Modifier
                    )
            ) {
                Text(
                    text = if (isGroup) groupName?.takeIf { it.isNotBlank() } ?: "Group"
                    else peer?.fullName?.ifBlank { peer?.username } ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isGroup) {
                        if (typingPeer) "typing…" else "${groupInfo?.memberCount ?: 0} members"
                    } else if (peerOnline) {
                        "Online"
                    } else if (canSeePeerLastSeen && peer?.lastSeen != null) {
                        "Last seen ${TimeFormat.fmtListTime(peer!!.lastSeen)}"
                    } else {
                        "Offline"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!isGroup && peerOnline) colors.text else colors.muted
                )
            }
            IconButton(onClick = {
                val opening = !showSearch
                showSearch = opening
                if (!opening) viewModel.setSearchQuery("")
            }) {
                Icon(Icons.Default.Search, contentDescription = "Search in chat", tint = colors.text)
            }
            if (!isGroup) {
                IconButton(onClick = { requestCall("video") }) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video call", tint = colors.text)
                }
                IconButton(onClick = { requestCall("audio") }) {
                    Icon(Icons.Default.Phone, contentDescription = "Audio call", tint = colors.text)
                }
            }
            IconButton(onClick = { if (isGroup) showGroupSettings = true else showProfile = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = colors.text)
            }
        }

        // Voice mini player (v3.1.0, worker D): while a voice message
        // plays — even after leaving this chat — a compact player sits
        // at the top of the screen. Tap it to jump back to the message.
        if (playbackState.messageId != null) {
            VoiceMiniPlayer(
                label = "Voice message" + (peer?.fullName?.let { " • $it" } ?: ""),
                onTap = {
                    val id = playbackState.messageId ?: return@VoiceMiniPlayer
                    val idx = displayItems.indexOfFirst {
                        it is ChatListItem.Msg && it.message.id == id
                    }
                    if (idx >= 0) scope.launch { listState.scrollToItem(idx) }
                }
            )
        }

        // In-chat search bar (v3.1.0): type to find text messages; the
        // arrows jump between matches, oldest first.
        if (showSearch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search messages") },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.inputBg,
                        unfocusedContainerColor = colors.inputBg,
                        disabledContainerColor = colors.inputBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (searchMatches.isNotEmpty()) {
                    Text(
                        text = "${searchMatchIndex + 1}/${searchMatches.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { viewModel.previousSearchMatch() }) {
                        Text("↑", color = colors.text)
                    }
                    IconButton(onClick = { viewModel.nextSearchMatch() }) {
                        Text("↓", color = colors.text)
                    }
                }
                IconButton(onClick = {
                    showSearch = false
                    viewModel.setSearchQuery("")
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close search", tint = colors.muted)
                }
            }
        }

        // Pinned message banner (v3.1.0): tap jumps to the message, the
        // X unpins it. One pinned message per chat, visible to everyone.
        val pinned = pinnedMessage
        if (pinned != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card2)
                    .clickable {
                        val index = displayItems.indexOfFirst {
                            it is ChatListItem.Msg && it.message.id == pinned.id
                        }
                        if (index >= 0) scope.launch { listState.scrollToItem(index) }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pinned message",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    Text(
                        replyPreview(pinned),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { viewModel.unpinCurrent() }) {
                    Icon(Icons.Default.Close, contentDescription = "Unpin", tint = colors.muted)
                }
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Clear chat?") },
                text = { Text("This hides all messages in this chat for you only.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClear = false
                        viewModel.clearChat()
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                }
            )
        }

        // Messages.
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (typingPeer) {
                item {
                    TypingIndicator(peer = peer, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            items(displayItems, key = {
                when (it) {
                    // Anchor each label to the message that follows it: two labels
                    // can have the same text (e.g. an empty label when the date
                    // is missing, or the same clock time on different days), and
                    // duplicate keys crash the list. Message ids are unique.
                    is ChatListItem.Day -> "day-${it.anchorId}"
                    is ChatListItem.Gap -> "gap-${it.anchorId}"
                    is ChatListItem.Msg -> it.message.id
                }
            }) { item ->
                when (item) {
                    is ChatListItem.Day -> DayLabel(item.text)
                    is ChatListItem.Gap -> DayLabel(item.text)
                    is ChatListItem.Msg -> {
                        val m = item.message
                        val mine = m.senderId == myId
                        val tick = if (mine) viewModel.tickState(m, peerOnline, deliveredIds) else null
                        val replyTarget = m.replyToId?.let { id -> messages.firstOrNull { it.id == id } }
                        MessageBubble(
                            message = m,
                            mine = mine,
                            tickState = tick,
                            replyTo = replyTarget,
                            reactions = reactions[m.id].orEmpty(),
                            senderName = if (isGroup && !mine) senderNames[m.senderId] else null,
                            onLongPress = { menuMessage = m },
                            onReactionChipClick = { emoji -> viewModel.toggleReaction(m.id, emoji) },
                            onPhotoClick = { viewingPhoto = it },
                            voiceDurationSec = voiceDurations[m.id],
                            conversationId = conversationId
                        )
                    }
                }
            }
            // WhatsApp-style encryption notice at the very top of the chat.
            item(key = "e2e-notice") {
                EncryptionNotice()
            }
        }

        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // v3.1.0: edit-mode banner (composer is prefilled; send saves
        // the edit instead of sending a new message).
        if (editingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card2)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Editing message",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    viewModel.cancelEdit()
                    input = ""
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel edit", tint = colors.muted)
                }
            }
        }

        // Reply preview.
        if (replyTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card2)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Replying to ${if (replyTo!!.senderId == myId) "yourself" else peer?.fullName ?: "them"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    Text(
                        replyPreview(replyTo!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { viewModel.setReplyTo(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = colors.muted)
                }
            }
        }

        // Composer, WhatsApp-style: pill-shaped field (attach inside it),
        // mic when empty, green send circle when typing.
        // imePadding lifts the composer with the keyboard (the manifest
        // already uses adjustResize); navigationBarsPadding keeps it above
        // the system gesture bar when the keyboard is closed.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isVoiceRecording) {
                // v3.1.0 (worker D): recording bar — live timer + live
                // waveform; slide-up locks, slide-left cancels. Send and
                // cancel buttons appear once locked.
                VoiceRecorderBar(
                    recorder = voiceRecorder,
                    onSend = { sendVoiceRecording() },
                    onCancel = { cancelVoiceRecording() },
                    modifier = Modifier.weight(1f)
                )
            } else {
                TextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (it.isNotBlank()) viewModel.onTyping() else viewModel.onTypingStopped()
                    },
                    placeholder = { Text("Type a message…") },
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.inputBg,
                        unfocusedContainerColor = colors.inputBg,
                        disabledContainerColor = colors.inputBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(onClick = { photoPicker.launch("image/*") }) {
                            Icon(Icons.Default.Add, contentDescription = "Send a photo", tint = colors.muted)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                if ((input.isNotBlank() || editingMessage != null) && !isVoiceRecording) {
                    // Green send circle (WhatsApp-style). In edit mode
                    // this saves the edit instead of sending a new
                    // message (v3.1.0).
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Emerald)
                            .clickable(enabled = !sending) {
                                if (editingMessage != null) {
                                    viewModel.saveEdit(input)
                                } else {
                                    viewModel.send(input)
                                }
                                input = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    // v3.1.0 (worker D): hold to record, slide up to lock,
                    // slide left to cancel. Stays mounted while recording
                    // so the hold gesture is never cut off by recomposition.
                    VoiceMicButton(
                        blocked = !voiceAllowed,
                        recording = isVoiceRecording,
                        locked = voiceLocked,
                        onPressStart = { requestMicAndRecord() },
                        onLock = { voiceRecorder.setLocked(true) },
                        onCancelArmed = { armed -> voiceRecorder.setCancelArmed(armed) },
                        onReleaseSend = { sendVoiceRecording() },
                        onCancel = { cancelVoiceRecording() },
                        onBlockedTap = {
                            Toast.makeText(
                                context,
                                "Voice messages are not allowed in this chat.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }

    // Long-press menu (bottom sheet): React / Reply / Copy / Delete.
    val sheetMessage = menuMessage
    if (sheetMessage != null) {
        val mine = sheetMessage.senderId == myId
        ModalBottomSheet(
            onDismissRequest = { menuMessage = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("React", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReactionEmojis.forEach { emoji ->
                        TextButton(onClick = {
                            viewModel.toggleReaction(sheetMessage.id, emoji)
                            menuMessage = null
                        }) {
                            Text(emoji, fontSize = 26.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    viewModel.setReplyTo(sheetMessage)
                    menuMessage = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reply", modifier = Modifier.fillMaxWidth())
                }
                // v3.1.0: edit my own text message (opens edit mode in
                // the composer) and pin/unpin any message.
                if (mine && sheetMessage.kind == "text") {
                    TextButton(onClick = {
                        viewModel.startEdit(sheetMessage)
                        menuMessage = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit", modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = {
                    viewModel.loadForwardTargets()
                    forwardMessage = sheetMessage
                    menuMessage = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Forward", modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = {
                    viewModel.togglePin(sheetMessage)
                    menuMessage = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (sheetMessage.isPinned) "Unpin message" else "Pin message",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (sheetMessage.kind == "text") {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(sheetMessage.body))
                        menuMessage = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Copy", modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = {
                    viewModel.deleteForMe(sheetMessage.id)
                    menuMessage = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete for me", modifier = Modifier.fillMaxWidth())
                }
                if (mine) {
                    TextButton(onClick = {
                        viewModel.deleteForEveryone(sheetMessage.id)
                        menuMessage = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete for everyone", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // v3.1.0: forward picker — pick a chat to forward the message to.
    // The forwarded copy carries a "Forwarded" label; the original
    // sender's forward setting is checked in ChatViewModel.forwardMessage.
    val toForward = forwardMessage
    if (toForward != null) {
        AlertDialog(
            onDismissRequest = { forwardMessage = null },
            title = { Text("Forward to…") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (forwardTargets.isEmpty()) {
                        Text("No other chats yet.", color = colors.muted)
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(forwardTargets, key = { it.conversation.id }) { target ->
                                ForwardTargetRow(
                                    item = target,
                                    onClick = {
                                        viewModel.forwardMessage(toForward, target.conversation.id)
                                        forwardMessage = null
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { forwardMessage = null }) { Text("Cancel") }
            }
        )
    }

    // v3.1.0: group settings as a fullscreen dialog overlay (no new
    // navigation route — MainActivity's NavHost is untouched). Leaving
    // the group closes the chat.
    if (showGroupSettings && isGroup) {
        Dialog(
            onDismissRequest = { showGroupSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GroupSettingsScreen(
                conversationId = conversationId,
                onDismiss = { showGroupSettings = false },
                onLeaveGroup = { showGroupSettings = false; onBack() }
            )
        }
    }

    // Peer profile preview (the peer is always a friend here — info + Close).
    if (showProfile) {
        val p = peer
        if (p != null) {
            ProfilePreviewDialog(
                profile = p,
                state = FriendState.FRIENDS,
                online = peerOnline,
                showMessageButton = false,
                onMessage = {},
                onAddFriend = {},
                onCancelRequest = {},
                onAccept = {},
                onDecline = {},
                onDismiss = { showProfile = false },
                onClearChat = { showProfile = false; confirmClear = true }
            )
        }
    }

    // Fullscreen photo viewer. Tap anywhere (or the X) to close.
    // (v3.1.0: no view-once, no Keep — the photo stays in the chat;
    // the server deletes it 7 days after it was sent.)
    val viewing = viewingPhoto
    if (viewing != null && !viewing.mediaUrl.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { closePhotoViewer() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { closePhotoViewer() },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = viewing.mediaUrl,
                    contentDescription = "Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { closePhotoViewer() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

/** Short preview text for a replied-to message (photo/voice have no body). */
private fun replyPreview(message: ChatMessage): String = messageDisplayText(message)

/**
 * True when a photo/voice message's media is gone: it was sent more than
 * 7 days ago (created_at), so the server's daily cleanup
 * (database/migration-v310-media-expiry.sql) has deleted — or will
 * delete — its row + Storage file. A photo/voice row with no media URL
 * at all counts as expired too (its file was removed before the row).
 * Text messages never expire. The client renders expired media as a
 * "deleted" placeholder instead of a broken bubble, which also covers
 * the window before the first cron run (or if pg_cron is not enabled
 * yet) — the age rule is the same on both sides.
 */
private fun isMediaExpired(message: ChatMessage): Boolean {
    if (message.kind != "photo" && message.kind != "voice") return false
    if (message.mediaUrl.isNullOrBlank()) return true
    val created = TimeFormat.parse(message.createdAt) ?: return false
    return created.isBefore(Instant.now().minus(7, ChronoUnit.DAYS))
}

/** WhatsApp-style encryption notice at the top of a chat. */
@Composable
private fun EncryptionNotice() {
    val colors = pingChatColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSystemInDarkTheme()) WhatsAppNoticeBgDark else WhatsAppNoticeBgLight)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Messages are end-to-end encrypted. No one outside of this chat, not even PingChat, can read or listen to them.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DayLabel(text: String) {
    // Blank text renders as an empty grey pill — show nothing instead.
    if (text.isBlank()) return
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = pingChatColors.muted,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(pingChatColors.card2)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    tickState: TickState?,
    replyTo: ChatMessage?,
    reactions: List<Reaction>,
    senderName: String? = null,
    onLongPress: () -> Unit,
    onReactionChipClick: (String) -> Unit,
    onPhotoClick: (ChatMessage) -> Unit,
    voiceDurationSec: Int?,
    conversationId: String
) {
    val colors = pingChatColors
    // WhatsApp bubble: one sharp corner on the side it points to (the tail).
    val bubbleShape = if (mine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 2.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }
    // A photo fills its bubble edge-to-edge, so only the bubble's own
    // top corners show on the image (its bottom corners stay square —
    // the time/ticks row sits below it, inside the same bubble).
    val photoImageShape = if (mine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp)
    }
    val timeText = TimeFormat.fmtTime(message.createdAt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (reactions.isNotEmpty()) 12.dp else 0.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        // Outer box so the reaction pill can overlap the bubble's corner.
        Box(modifier = Modifier.widthIn(max = 300.dp)) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(if (mine) colors.bubbleMe else colors.bubbleThem)
                    .then(
                        if (mine) Modifier
                        else Modifier.border(1.dp, colors.border, bubbleShape)
                    )
                    // Tap a photo bubble to view it fullscreen; long-press
                    // still opens the menu (React / Reply / Delete).
                    // An expired photo opens nothing — its file is gone.
                    .combinedClickable(
                        onClick = { if (message.kind == "photo" && !isMediaExpired(message)) onPhotoClick(message) },
                        onLongClick = onLongPress
                    )
                    // A photo bubble wraps its image tightly — no padding
                    // around the image, WhatsApp-style. Text and voice
                    // bubbles keep their usual inner padding.
                    .padding(
                        start = if (message.kind == "photo") 0.dp else 12.dp,
                        end = if (message.kind == "photo") 0.dp else 8.dp,
                        top = if (message.kind == "photo") 0.dp else 6.dp,
                        bottom = if (message.kind == "photo") 0.dp else 6.dp
                    )
            ) {
                Column {
                    // v3.1.0: group bubbles show the sender's name; a
                    // forwarded message shows a "Forwarded" label.
                    if (senderName != null) {
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Emerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (message.forwardedFrom != null) {
                        Text(
                            text = "Forwarded",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = colors.muted
                        )
                    }
                    if (replyTo != null) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (mine) Color.White.copy(alpha = 0.18f) else colors.card2
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = replyPreview(replyTo),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mine) colors.bubbleMeText.copy(alpha = 0.9f) else colors.muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    when (message.kind) {
                        "photo" -> {
                            // Expired (7+ days old, or no media URL): the
                            // file is gone — show a placeholder, never a
                            // broken image. The time/ticks row below stays.
                            if (isMediaExpired(message)) {
                                Text(
                                    text = "এই ছবিটি মুছে ফেলা হয়েছে",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted,
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp)
                                )
                            } else if (!message.mediaUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Photo",
                                    modifier = Modifier
                                        .widthIn(max = 240.dp)
                                        .height(180.dp)
                                        .clip(photoImageShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = "Photo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (mine) colors.bubbleMeText else colors.text,
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp)
                                )
                            }
                            // The time/ticks row keeps its place below the
                            // photo, unchanged — only its padding is local
                            // now that the bubble itself has no padding.
                            Box(
                                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 6.dp)
                            ) {
                                MetaRow(timeText = timeText, tickState = tickState, mine = mine)
                            }
                        }
                        "voice" -> {
                            // Expired (7+ days old, or no media URL): the
                            // file is gone — placeholder text + time/ticks,
                            // no play button, never a broken bubble.
                            // (v3.1.0: no view-once, no Keep.)
                            if (isMediaExpired(message)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "এই ভয়েস মেসেজটি মুছে ফেলা হয়েছে",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.muted,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = timeText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = WhatsAppTickGrey
                                    )
                                    if (mine && tickState != null) {
                                        Spacer(Modifier.width(2.dp))
                                        TickIcon(state = tickState, onBubble = true)
                                    }
                                }
                            } else {
                                // v3.1.0 (worker D): WhatsApp-style voice
                                // bubble — waveform + progress + seek +
                                // speed, backed by the global
                                // VoicePlaybackManager.
                                VoiceMessageBubble(
                                    message = message,
                                    mine = mine,
                                    timeText = timeText,
                                    tickState = tickState,
                                    knownDurationSec = voiceDurationSec,
                                    conversationId = conversationId
                                )
                            }
                        }
                        else -> {
                            // Text + time + ticks on one line, WhatsApp-style:
                            // the meta sits at the end of the last line.
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    // Never render an empty bubble: a text-kind
                                    // row with a blank body falls back to
                                    // "Voice message" / "Photo" / "Message".
                                    text = messageDisplayText(message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (mine) colors.bubbleMeText else colors.text,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    // v3.1.0: an edited text message shows
                                    // an "edited" label next to its time.
                                    text = timeText + if (message.editedAt != null) " · edited" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = WhatsAppTickGrey
                                )
                                if (mine && tickState != null) {
                                    Spacer(Modifier.width(2.dp))
                                    TickIcon(state = tickState, onBubble = true)
                                }
                            }
                        }
                    }
                }
            }
            if (reactions.isNotEmpty()) {
                // Reaction pill overlapping the bubble's bottom corner.
                Row(
                    modifier = Modifier
                        .align(if (mine) Alignment.BottomEnd else Alignment.BottomStart)
                        .offset(y = 10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.card2)
                        .border(1.dp, colors.border, RoundedCornerShape(999.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                        Text(
                            "$emoji ${list.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text,
                            modifier = Modifier.clickable { onReactionChipClick(emoji) }
                        )
                    }
                }
            }
        }
    }
}

/** Time (+ ticks for my messages), right-aligned at a bubble's bottom. */
@Composable
private fun MetaRow(timeText: String, tickState: TickState?, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = WhatsAppTickGrey
        )
        if (mine && tickState != null) {
            Spacer(Modifier.width(2.dp))
            TickIcon(state = tickState, onBubble = true)
        }
    }
}

/** One row of the v3.1.0 forward picker: group photo/name or peer avatar. */
@Composable
private fun ForwardTargetRow(item: ConversationItem, onClick: () -> Unit) {
    val colors = pingChatColors
    val avatarProfile = if (item.isGroup) {
        Profile(
            id = item.conversation.id,
            username = item.displayName,
            fullName = item.displayName,
            avatarUrl = item.conversation.groupAvatarUrl
        )
    } else {
        item.peer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile = avatarProfile, size = AvatarSize.SM)
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
