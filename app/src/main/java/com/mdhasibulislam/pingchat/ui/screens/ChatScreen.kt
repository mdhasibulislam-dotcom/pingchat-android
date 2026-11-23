package com.mdhasibulislam.pingchat.ui.screens

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.mdhasibulislam.pingchat.data.FriendsRepository
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.ProfileRepository
import com.mdhasibulislam.pingchat.data.PushSender
import com.mdhasibulislam.pingchat.data.Reaction
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.Supabase
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
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    onBack: () -> Unit,
    /**
     * v3.1.1: open another chat — a group just created from the peer's
     * profile, or "Message" on a nested profile opened from inside it.
     * Defaults to a no-op; MainActivity must wire it to
     * navController.navigate("chat/$id") (the same lambda MainScreen
     * already gets as onOpenChat).
     */
    onOpenChat: (String) -> Unit = {},
    /**
     * v3.2.0 (worker F): the peer profile's "Privacy & Security" row
     * opens the global Privacy & Security screen — MainActivity wires
     * this to navController.navigate("privacy_security").
     */
    onOpenPrivacySecurity: () -> Unit = {}
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
    val autoDeleteTimer by viewModel.autoDeleteTimer.collectAsState()
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
    val searchFocus = remember { FocusRequester() }
    var forwardMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showGroupSettings by remember { mutableStateOf(false) }

    // v3.1.1: full public profile opened from the chat header (1-to-1
    // only) — the same PublicProfileScreen the People tab opens.
    // ChatScreen is a separate NavHost destination from MainScreen, so
    // MainScreen's publicProfile state can't be reached from here; the
    // screen is rendered as a fullscreen overlay instead (same pattern
    // as the group-settings overlay below), and its data is loaded from
    // the same repos PeopleViewModel uses (ProfileRepository for mutual
    // friends / view count, PrivacyRepository for bio + last-seen
    // visibility, FriendsRepository for the friendship state).
    val profileRepo = remember { ProfileRepository() }
    val friendsRepo = remember { FriendsRepository() }
    val prefs = remember { Prefs(context) }
    var publicProfile by remember { mutableStateOf<Profile?>(null) }
    var publicProfileState by remember { mutableStateOf(FriendState.FRIENDS) }
    var profileMutualFriends by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var profileViewCount by remember { mutableStateOf(0L) }
    var profileVisibility by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var peerBlocked by remember { mutableStateOf(false) }
    var chatMuted by remember { mutableStateOf(false) }
    // v3.2.0 (worker F): flags the peer contact-info screen shows —
    // loaded in the LaunchedEffect(showProfile) below when it opens.
    var chatPinned by remember { mutableStateOf(false) }
    var chatArchived by remember { mutableStateOf(false) }
    var chatTheme by remember(conversationId) { mutableStateOf(prefs.chatTheme(conversationId)) }
    // v3.2.0: Delete Chat (peer profile) clears, then goes back to the
    // chats list — set by the profile's onDeleteChat, read by the
    // confirmClear dialog's confirm button.
    var clearThenBack by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }

    /**
     * Load everything PublicProfileScreen needs for [profile], mirroring
     * MainScreen's LaunchedEffect(publicProfile): mutual friends + view
     * count + view recording (ProfileRepository), friendship state
     * (FriendsRepository — the chat peer is always FRIENDS), and the
     * privacy-checked bio / last-seen visibility (PrivacyRepository,
     * falling back to PeopleScreen.canSeeDetails, like
     * PeopleViewModel.visibilityFor does). Also loads this chat's mute
     * flag and whether I blocked the peer, for the actions footer.
     */
    suspend fun loadPublicProfileState(profile: Profile) {
        val state = if (profile.id == peer?.id) FriendState.FRIENDS
        else try {
            friendsRepo.friendshipStatus(profile.id)
        } catch (_: Exception) {
            FriendState.NONE
        }
        publicProfileState = state
        // Fallback first so the screen never waits on the network.
        val fallback = canSeeDetails(profile, state)
        profileVisibility = fallback to fallback
        profileMutualFriends = try {
            profileRepo.loadMutualFriends(profile.id)
        } catch (_: Exception) {
            emptyList()
        }
        profileViewCount = try {
            profileRepo.profileViewCount(profile.id)
        } catch (_: Exception) {
            0L
        }
        try {
            profileRepo.recordProfileView(profile.id)
            profileViewCount = profileRepo.profileViewCount(profile.id)
        } catch (_: Exception) {
            // views are best-effort
        }
        profileVisibility = try {
            privacyRepo.canSeeBioOf(profile.id) to privacyRepo.canSeeLastSeen(profile.id)
        } catch (_: Exception) {
            fallback to fallback
        }
        peerBlocked = try {
            privacyRepo.isBlockedByMe(profile.id)
        } catch (_: Exception) {
            false
        }
        chatMuted = profile.id == peer?.id &&
            prefs.mutedConversationIds.contains(conversationId)
    }

    /** Open the full public profile for [profile] (header tap / ⋮ menu). */
    fun openPublicProfile(profile: Profile) {
        profileMutualFriends = emptyList()
        profileViewCount = 0L
        profileVisibility = null
        peerBlocked = false
        publicProfileState = if (profile.id == peer?.id) FriendState.FRIENDS else FriendState.NONE
        publicProfile = profile
    }

    /**
     * v3.2.0 (worker F): open the WhatsApp-style contact-info screen
     * for the chat peer (ChatPeerProfileScreen). The flags it needs
     * load in the LaunchedEffect(showProfile) below. Groups never
     * reach here — their header opens group settings instead.
     */
    fun openPeerProfile() {
        if (peer == null) return
        showProfile = true
    }

    // (Re)load the profile data whenever a different profile opens.
    LaunchedEffect(publicProfile?.id) {
        val profile = publicProfile ?: return@LaunchedEffect
        loadPublicProfileState(profile)
    }

    // v3.2.0 (worker F): when the peer contact-info screen opens, load
    // the flags it shows — blocked / muted / pinned / archived / theme.
    // (loadPublicProfileState above only runs for PublicProfileScreen.)
    LaunchedEffect(showProfile) {
        if (!showProfile) return@LaunchedEffect
        val p = peer ?: return@LaunchedEffect
        peerBlocked = try {
            privacyRepo.isBlockedByMe(p.id)
        } catch (_: Exception) {
            false
        }
        chatMuted = prefs.mutedConversationIds.contains(conversationId)
        val item = try {
            chatRepo.loadConversations()
                .firstOrNull { it.conversation.id == conversationId }
        } catch (_: Exception) {
            null
        }
        chatPinned = item?.isPinned == true
        chatArchived = item?.isArchived == true
        chatTheme = prefs.chatTheme(conversationId)
    }

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
            // v3.1.1: do NOT auto-start a recording here — no finger is
            // on the mic anymore, so no gesture owns it (it would record
            // unlocked, with no way to finish it except bar buttons the
            // user never saw appear). The user holds the mic again.
            Toast.makeText(
                context,
                "Microphone allowed — hold the mic to record.",
                Toast.LENGTH_SHORT
            ).show()
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
        // v3.1.1: the mic button stays mounted while recording (fix 1
        // below), so a tap during a recording must not start a second
        // one — and must not toast either. Returning false tells the
        // button's gesture loop this press owns nothing.
        if (voiceRecorder.isRecording.value) return false
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
    // v3.1.1: a call started from a nested profile (a mutual friend opened
    // from inside the peer's profile) — the peer to call, instead of the
    // chat peer. Null means "call the chat peer" (existing behaviour).
    var pendingCallProfile by remember { mutableStateOf<Profile?>(null) }
    val callPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val kind = pendingCallKind
        pendingCallKind = null
        // v3.1.1: a nested profile's call carries its own peer (and needs
        // its own 1-to-1 conversation); otherwise call the chat peer.
        val nested = pendingCallProfile
        pendingCallProfile = null
        val p = nested ?: viewModel.peer.value
        if (kind == null || p == null) return@rememberLauncherForActivityResult
        if (result.values.all { it }) {
            scope.launch {
                val cid = if (nested != null) {
                    try {
                        chatRepo.getOrCreateConversation(nested.id)
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    conversationId
                }
                if (cid == null) {
                    Toast.makeText(context, "Could not start the call. Try again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val err = callViewModel.startCall(p, kind, cid)
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

    /**
     * v3.1.1: audio/video call from a public profile opened from this
     * chat. The chat peer reuses requestCall; any other profile (a
     * mutual friend opened from inside the peer's profile) gets its own
     * 1-to-1 conversation first — the same path as
     * PeopleViewModel.startCall, minus the PeopleViewModel.
     */
    fun requestProfileCall(profile: Profile, kind: String) {
        if (profile.id == peer?.id) {
            requestCall(kind)
            return
        }
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
                val cid = try {
                    chatRepo.getOrCreateConversation(profile.id)
                } catch (_: Exception) {
                    null
                }
                if (cid == null) {
                    Toast.makeText(context, "Could not start the call. Try again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val err = callViewModel.startCall(profile, kind, cid)
                if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
        } else {
            pendingCallKind = kind
            pendingCallProfile = profile
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

    // v3.2.0: opening the search (from the peer profile's Search button)
    // focuses the search field at once, so the keyboard opens with it.
    LaunchedEffect(showSearch) {
        if (showSearch) searchFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // v3.2.0 (worker F): per-chat theme — the peer profile's Chat
            // Theme choice replaces the normal chat background for this
            // conversation; "default" keeps colors.chatBg.
            .background(chatThemeColor(chatTheme, colors.chatBg))
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
            // v3.1.1: tapping the peer's avatar or name (1-to-1 only) opens
            // their public profile. Group headers still open group settings.
            Avatar(
                profile = headerProfile,
                size = AvatarSize.SM,
                online = if (isGroup) null else peerOnline,
                modifier = if (isGroup) Modifier else Modifier.clickable {
                    openPeerProfile()
                }
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isGroup) Modifier.clickable { showGroupSettings = true }
                        else Modifier.clickable { openPeerProfile() }
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
            IconButton(onClick = { if (isGroup) showGroupSettings = true else openPeerProfile() }) {
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
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocus)
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
                        // v3.2.0: Delete Chat (peer profile) goes back to
                        // the chats list after clearing. The clear RPC is
                        // a single quick call — give it a head start so
                        // leaving the screen (which clears the ViewModel)
                        // cannot cancel it mid-flight.
                        if (clearThenBack) {
                            clearThenBack = false
                            scope.launch {
                                delay(500)
                                onBack()
                            }
                        }
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
            }
            Spacer(Modifier.width(8.dp))
            // v3.1.1 (issue 4, fix 1): the mic button lives OUTSIDE the
            // recording/text-field switch, in the same slot in both
            // states, so it is never unmounted while recording. It used
            // to live inside the `else` branch above: start() set
            // _isRecording, recomposition removed the button, Compose
            // cancelled its pointerInput coroutine mid-hold, and
            // finger-up / slide-up / slide-left were never delivered —
            // onReleaseSend never fired and the recording stuck until
            // the 120s auto-stop. (The old comment claiming it "stays
            // mounted" was wrong — the `else` branch unmounted it.)
            if (!isVoiceRecording && (input.isNotBlank() || editingMessage != null)) {
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
                // Hold to record, slide up to lock, slide left to
                // cancel. While recording it shows the red / locked state
                // and the same hold owns the gesture to the end.
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

    // v3.2.0 (worker F): WhatsApp-style contact-info screen for the
    // 1-to-1 peer (mockups 7433_0_9gjz.png + 7434_1_aa8a.png) — replaces
    // ProfilePreviewDialog. Full-screen dialog overlay, same pattern as
    // the group-settings overlay. All actions reuse the existing pieces:
    // calls (requestCall), in-chat search, create-group, mute
    // (setChatMuted + Prefs), clear chat (confirmClear), shared media
    // (fullscreen photo viewer), pin (one pinned message per chat),
    // block/unblock (confirmBlock), archive/pin (participant flags),
    // share (profileShareLink), report (pingchat_reports).
    if (showProfile) {
        val p = peer
        if (p != null && !isGroup) {
            val peerName = p.fullName.ifBlank { p.username }
            val sharedPhotos = messages.filter { it.kind == "photo" && !isMediaExpired(it) }
            Dialog(
                onDismissRequest = { showProfile = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ChatPeerProfileScreen(
                    profile = p,
                    online = peerOnline,
                    canSeeLastSeen = canSeePeerLastSeen,
                    sharedPhotosCount = sharedPhotos.size,
                    // One pinned message per chat (ChatViewModel) — the
                    // "Starred Messages" count is 0 or 1, and tapping it
                    // jumps to that pinned message.
                    pinnedCount = if (pinnedMessage != null) 1 else 0,
                    isBlocked = peerBlocked,
                    isMuted = chatMuted,
                    isPinned = chatPinned,
                    isArchived = chatArchived,
                    chatTheme = chatTheme,
                    autoDeleteTimer = autoDeleteTimer,
                    onAutoDeleteSelected = { viewModel.setAutoDeleteTimer(it) },
                    onBack = { showProfile = false },
                    onAudioCall = { requestCall("audio") },
                    onVideoCall = { requestCall("video") },
                    onSearch = { showProfile = false; showSearch = true },
                    onCreateGroup = { showCreateGroup = true },
                    onMuteToggle = {
                        val next = !chatMuted
                        scope.launch {
                            val ok = try {
                                chatRepo.setChatMuted(conversationId, next)
                            } catch (_: Exception) {
                                false
                            }
                            if (ok) {
                                prefs.setConversationMuted(conversationId, next)
                                chatMuted = next
                            } else {
                                Toast.makeText(context, "Could not change mute setting. Try again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onClearChat = { showProfile = false; confirmClear = true },
                    onSharedMedia = {
                        // Opens the newest shared photo in the existing
                        // fullscreen viewer.
                        messages.lastOrNull { it.kind == "photo" && !isMediaExpired(it) }?.let {
                            showProfile = false
                            viewingPhoto = it
                        } ?: Toast.makeText(context, "No shared media yet.", Toast.LENGTH_SHORT).show()
                    },
                    onOpenPinned = {
                        val pinned = pinnedMessage
                        showProfile = false
                        if (pinned != null) {
                            val index = displayItems.indexOfFirst {
                                it is ChatListItem.Msg && it.message.id == pinned.id
                            }
                            if (index >= 0) scope.launch { listState.scrollToItem(index) }
                        } else {
                            Toast.makeText(context, "No pinned messages yet.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBlockToggle = {
                        if (peerBlocked) {
                            scope.launch {
                                try { privacyRepo.unblockUser(p.id) } catch (_: Exception) { }
                                peerBlocked = false
                                Toast.makeText(context, "$peerName unblocked.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            confirmBlock = true
                        }
                    },
                    onDeleteChat = {
                        showProfile = false
                        clearThenBack = true
                        confirmClear = true
                    },
                    onOpenPrivacySecurity = onOpenPrivacySecurity,
                    onPinToggle = {
                        val next = !chatPinned
                        scope.launch {
                            val ok = try {
                                chatRepo.setChatPinned(conversationId, next)
                            } catch (_: Exception) {
                                false
                            }
                            if (ok) chatPinned = next
                            else Toast.makeText(
                                context,
                                "Could not ${if (next) "pin" else "unpin"} this chat. Try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onArchiveToggle = {
                        val next = !chatArchived
                        scope.launch {
                            val ok = try {
                                chatRepo.setChatArchived(conversationId, next)
                            } catch (_: Exception) {
                                false
                            }
                            if (ok) {
                                chatArchived = next
                                if (next) {
                                    // Archived chats leave the main list —
                                    // go back to it.
                                    showProfile = false
                                    onBack()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Could not ${if (next) "archive" else "unarchive"} this chat. Try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onShareContact = {
                        val link = profileShareLink(p.username)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "@${p.username}\n$link")
                        }
                        context.startActivity(
                            Intent.createChooser(sendIntent, "Share contact")
                        )
                    },
                    // Report (web spec §6.6): the Android app had no
                    // report UI before v3.2.0 — this inserts into
                    // pingchat_reports (RLS: reporter_id = auth.uid()),
                    // same 5 reasons + optional details as the web app.
                    // There is no admin review UI in the app; reports
                    // are stored only.
                    onReportSubmit = { reason, details ->
                        val me = chatRepo.myId()
                        if (me == null) {
                            Toast.makeText(context, "Could not send report. Try again.", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                try {
                                    Supabase.client.from("pingchat_reports").insert(
                                        buildJsonObject {
                                            put("reporter_id", me)
                                            put("reported_id", p.id)
                                            put("reason", reason)
                                            put("details", details)
                                        }
                                    )
                                    Toast.makeText(context, "Thanks, your report was sent.", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Could not send report. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onChatThemeSelected = { key ->
                        prefs.setChatTheme(conversationId, key)
                        chatTheme = key
                    }
                )
            }
        }
    }

    // v3.2.0: full public profile opened from the chat header or the ⋮
    // menu (1-to-1 only). The WhatsApp-style contact actions — Mute
    // notifications, Create a group with them, shared-media count, and
    // Block / Unblock — now live INSIDE PublicProfileScreen (wired via
    // its on* callbacks below); the old footer under the screen is gone.
    // No new backend features.
    val openProfile = publicProfile
    if (openProfile != null) {
        val myPeer = peer
        val isChatPeer = myPeer != null && openProfile.id == myPeer.id
        val peerName = openProfile.fullName.ifBlank { openProfile.username }
        val sharedPhotos = if (isChatPeer) messages.filter { it.kind == "photo" && !isMediaExpired(it) } else emptyList()
        Dialog(
            onDismissRequest = { publicProfile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PublicProfileScreen(
                        profile = openProfile,
                        state = publicProfileState,
                        online = onlineIds.contains(openProfile.id),
                        canSeeBio = profileVisibility?.first
                            ?: canSeeDetails(openProfile, publicProfileState),
                        canSeeLastSeen = profileVisibility?.second
                            ?: canSeeDetails(openProfile, publicProfileState),
                        mutualFriends = profileMutualFriends,
                        viewCount = profileViewCount,
                        recentViewers = null,
                        friendsCount = 0,
                        mediaSent = 0,
                        onBack = { publicProfile = null },
                        onMessage = {
                            if (isChatPeer) {
                                // Already in this chat.
                                publicProfile = null
                            } else {
                                scope.launch {
                                    val cid = try {
                                        chatRepo.getOrCreateConversation(openProfile.id)
                                    } catch (_: Exception) {
                                        null
                                    }
                                    if (cid == null) {
                                        Toast.makeText(context, "Could not open chat. Try again.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        publicProfile = null
                                        onOpenChat(cid)
                                    }
                                }
                            }
                        },
                        onAudioCall = { requestProfileCall(openProfile, "audio") },
                        onVideoCall = { requestProfileCall(openProfile, "video") },
                        onAddFriend = {
                            scope.launch {
                                try { friendsRepo.sendFriendRequest(openProfile.id) } catch (_: Exception) { }
                                loadPublicProfileState(openProfile)
                            }
                        },
                        onCancelRequest = {
                            scope.launch {
                                try { friendsRepo.cancelFriendRequest(openProfile.id) } catch (_: Exception) { }
                                loadPublicProfileState(openProfile)
                            }
                        },
                        onAccept = {
                            scope.launch {
                                try { friendsRepo.respondFriendRequest(openProfile.id, true) } catch (_: Exception) { }
                                loadPublicProfileState(openProfile)
                            }
                        },
                        onDecline = {
                            scope.launch {
                                try { friendsRepo.respondFriendRequest(openProfile.id, false) } catch (_: Exception) { }
                                loadPublicProfileState(openProfile)
                            }
                        },
                        onOpenProfile = { other -> openPublicProfile(other) },
                        isBlocked = peerBlocked,
                        isMuted = chatMuted,
                        sharedMediaCount = sharedPhotos.size,
                        onBlockToggle = {
                            if (peerBlocked) {
                                scope.launch {
                                    try { privacyRepo.unblockUser(openProfile.id) } catch (_: Exception) { }
                                    peerBlocked = false
                                    Toast.makeText(context, "$peerName unblocked.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                confirmBlock = true
                            }
                        },
                        onCreateGroup = if (isChatPeer) ({ showCreateGroup = true }) else null,
                        onMuteToggle = if (isChatPeer) ({
                            val next = !chatMuted
                            scope.launch {
                                val ok = try {
                                    chatRepo.setChatMuted(conversationId, next)
                                } catch (_: Exception) {
                                    false
                                }
                                if (ok) {
                                    prefs.setConversationMuted(conversationId, next)
                                    chatMuted = next
                                } else {
                                    Toast.makeText(context, "Could not change mute setting. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) else null,
                        onSharedMedia = if (isChatPeer) ({
                            // Opens the newest shared photo in the
                            // existing fullscreen viewer.
                            messages.lastOrNull { it.kind == "photo" && !isMediaExpired(it) }?.let {
                                publicProfile = null
                                viewingPhoto = it
                            }
                        }) else null
                    )
                }
            }
        }
    }

    // v3.1.1: "Create a group with <peer>" — the existing create-group
    // flow, with the peer pre-selected.
    if (showCreateGroup) {
        Dialog(
            onDismissRequest = { showCreateGroup = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CreateGroupScreen(
                onGroupCreated = { id ->
                    showCreateGroup = false
                    showProfile = false
                    publicProfile = null
                    onOpenChat(id)
                },
                onDismiss = { showCreateGroup = false },
                // v3.2.0: creating a group from the peer contact-info
                // screen pre-selects the chat peer (publicProfile is
                // null on that path — it only exists for the old
                // PublicProfileScreen flow).
                initialSelectedIds = publicProfile?.id?.let { setOf(it) }
                    ?: peer?.id?.let { setOf(it) } ?: emptySet()
            )
        }
    }

    // v3.1.1: confirm before blocking — blocking silences the peer
    // everywhere (messages, calls, friend requests).
    if (confirmBlock) {
        // v3.2.0: the block can now also come from the peer contact-info
        // screen (ChatPeerProfileScreen), where publicProfile is null —
        // fall back to the chat peer.
        val target = publicProfile ?: peer
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block ${target?.fullName?.ifBlank { target?.username } ?: "this user"}?") },
            text = { Text("You won't receive messages or calls from them. You can unblock them anytime from here or from Settings > Privacy & Security > Blocked users.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    val id = target?.id ?: return@TextButton
                    val name = target.fullName.ifBlank { target.username }
                    scope.launch {
                        try { privacyRepo.blockUser(id) } catch (_: Exception) { }
                        peerBlocked = true
                        Toast.makeText(context, "$name blocked.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) { Text("Cancel") }
            }
        )
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
                            overflow = TextOverflow.Ellipsis,
                            // v3.1.1: in a photo bubble no row may be wider
                            // than the photo (240dp max) or it stretches the
                            // bubble past the image — see the MetaRow note
                            // below. Text bubbles keep the 300dp cap.
                            modifier = if (message.kind == "photo") Modifier.widthIn(max = 240.dp) else Modifier
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
                                // v3.1.1: same 240dp cap as the sender name —
                                // a long reply preview must not widen a
                                // photo bubble past its image.
                                .then(if (message.kind == "photo") Modifier.widthIn(max = 240.dp) else Modifier)
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
                    // v3.2.2: a story reply shows the story quoted above
                    // the reply text (StoryReplyQuote is public in
                    // StoryScreens.kt, same package — no import needed).
                    if (message.storyId != null) {
                        StoryReplyQuote(message, mine = mine)
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
                            // v3.1.1: the time/ticks row must NOT use
                            // fillMaxWidth (see MetaRow) — it forced every
                            // photo bubble to the 300dp outer cap, leaving
                            // a dark gap right of the photo. It now wraps
                            // its content and sits at the bubble's right
                            // edge; only its padding is local now that the
                            // bubble itself has no padding.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 6.dp)
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
    // v3.1.1: NO fillMaxWidth here. This row lives inside a photo
    // bubble's wrapping Column; fillMaxWidth expanded it to the 300dp
    // outer cap, so every photo bubble was 300dp wide while the photo
    // itself is at most 240dp (less for portraits) — the dark gap on
    // the photo's right, which the v3.0.2 "0 padding" fix never touched.
    // It now wraps its content; the caller aligns it to the bubble's
    // right edge.
    Row(
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
