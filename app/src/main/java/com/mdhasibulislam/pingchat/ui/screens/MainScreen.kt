package com.mdhasibulislam.pingchat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.VoicePlaybackManager
import com.mdhasibulislam.pingchat.ui.components.VoiceMiniPlayer
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ChatsViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.PeopleViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

/** Main app shell: bottom tabs (Chats / Calls / People / Settings).
 *  No top bar — each tab owns its own header (the Chats tab header's
 *  avatar opens my Profile); the bottom bar's last tab is Settings. */
@Composable
fun MainScreen(
    onOpenChat: (String) -> Unit,
    onSettingsClick: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    prefs: Prefs,
    // Friend-request notification tap (v3.0.2): open the People tab, where
    // pending requests sit at the top. There is no separate requests route —
    // tabs are local state, not navigation destinations.
    openPeopleTab: Boolean = false,
    onPeopleTabConsumed: () -> Unit = {},
    // v3.1.0 (worker A): Settings > Privacy & Security ("privacy_security"
    // route) and Privacy Policy ("privacy_policy" route, worker G).
    onPrivacySecurityClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    // v3.2.0 (worker A): own-profile overlay's Edit Profile button opens
    // the "edit_profile" route — MainActivity wires this param.
    onEditProfileClick: () -> Unit = {}
) {
    var tab by remember { mutableIntStateOf(0) }
    // Friend-request notification tap: switch to the People tab (2) once,
    // whether the app cold-started on it or was already running.
    LaunchedEffect(openPeopleTab) {
        if (openPeopleTab) {
            tab = 2
            onPeopleTabConsumed()
        }
    }
    var showProfile by remember { mutableStateOf(false) }
    val chatsViewModel: ChatsViewModel = viewModel()
    val peopleViewModel: PeopleViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val onlineIds by SessionManager.onlineIds.collectAsState()
    val colors = pingChatColors

    // v3.1.0: full public profile screen (worker F), opened from the
    // People-tab preview dialog ("View profile") or from a mutual
    // friend / recent viewer inside the profile itself.
    var publicProfile by remember { mutableStateOf<Pair<Profile, FriendState>?>(null) }
    val statuses by peopleViewModel.statuses.collectAsState()
    val friends by peopleViewModel.friends.collectAsState()
    val mutualFriends by peopleViewModel.mutualFriends.collectAsState()
    val profileViewCount by peopleViewModel.profileViewCount.collectAsState()
    val profileVisibility by peopleViewModel.visibility.collectAsState()
    val openedConversation by peopleViewModel.openedConversation.collectAsState()
    val playbackState by VoicePlaybackManager.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(publicProfile) {
        val (profile, state) = publicProfile ?: return@LaunchedEffect
        peopleViewModel.loadPublicProfile(profile.id)
        peopleViewModel.recordProfileView(profile.id)
        peopleViewModel.loadVisibilityFor(profile, state)
    }
    // The public profile replaces the tab content, so PeopleScreen's own
    // openedConversation collector is not composed while it is open —
    // handle the chat-open navigation here instead.
    LaunchedEffect(publicProfile, openedConversation) {
        val id = openedConversation ?: return@LaunchedEffect
        if (publicProfile == null) return@LaunchedEffect
        peopleViewModel.consumeOpenedConversation()
        publicProfile = null
        onOpenChat(id)
    }

    // Calls from a public profile: mic permission (plus camera for video),
    // then PeopleViewModel.startCall — same pattern as ChatScreen.requestCall.
    var pendingCall by remember { mutableStateOf<Pair<String, String>?>(null) }
    val callPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val (userId, kind) = pendingCall ?: return@rememberLauncherForActivityResult
        pendingCall = null
        if (result.values.all { it }) {
            peopleViewModel.startCall(userId, kind)
        } else {
            Toast.makeText(
                context,
                if (kind == "video") "Microphone and camera permission is needed for video calls."
                else "Microphone permission is needed for audio calls.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    fun requestCall(userId: String, kind: String) {
        val needed = if (kind == "video") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            peopleViewModel.startCall(userId, kind)
        } else {
            pendingCall = userId to kind
            callPermission.launch(needed)
        }
    }
    // Show a call error from the public profile as a toast — only while
    // the public profile is open, because PeopleScreen shows the same
    // error inline when it is visible.
    val peopleError by peopleViewModel.error.collectAsState()
    LaunchedEffect(peopleError, publicProfile) {
        if (publicProfile == null) return@LaunchedEffect
        peopleError?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Profile preview dialog (People tab) — state lives here so it can use
    // the same PeopleViewModel instance for the friend actions.
    var preview by remember { mutableStateOf<Pair<Profile, FriendState>?>(null) }

    // v3.2.1: no top bar — the Chats tab owns its header (logo + search
    // + avatar, which opens the own profile), and Calls / People /
    // Settings each have their own in-content title. The old TopAppBar
    // duplicated the Chats header and carried the dark-mode toggle,
    // which now lives only in Settings > Appearance.
    // v3.2.2: the bottom nav is a FLOATING pill bar (Telegram-style),
    // drawn OVER the tab content instead of a Scaffold bottomBar, so
    // lists scroll behind it. Slim glassy bar — 60dp tall, 18dp
    // corners, 12dp side margins, 8dp bottom margin, 4dp shadow,
    // translucent card colour (0.72 alpha) with a subtle border; the
    // selected tab gets a 40dp Emerald circle behind its icon. The bar
    // itself is added after the tab content below.
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Tab content fills the whole screen.
            Box(modifier = Modifier.fillMaxSize()) {
            // v3.1.0 (worker F): full public profile screen. The friend
            // state is read live from the statuses flow, so Accept/Decline
            // from inside the profile updates the buttons immediately.
            val openProfile = publicProfile
            if (openProfile != null) {
                val (profile, state) = openProfile
                val liveState = statuses[profile.id] ?: state
                val (fallbackBio, fallbackLastSeen) = peopleViewModel.visibilityFor(profile, liveState)
                PublicProfileScreen(
                    profile = profile,
                    state = liveState,
                    online = onlineIds.contains(profile.id),
                    canSeeBio = profileVisibility?.first ?: fallbackBio,
                    canSeeLastSeen = profileVisibility?.second ?: fallbackLastSeen,
                    mutualFriends = mutualFriends,
                    viewCount = profileViewCount,
                    recentViewers = null,
                    friendsCount = friends.size,
                    mediaSent = 0,
                    onBack = { publicProfile = null },
                    onMessage = { peopleViewModel.openChat(profile.id) },
                    onAudioCall = { requestCall(profile.id, "audio") },
                    onVideoCall = { requestCall(profile.id, "video") },
                    onAddFriend = { peopleViewModel.sendRequest(profile.id) },
                    onCancelRequest = { peopleViewModel.cancelRequest(profile.id) },
                    onAccept = { peopleViewModel.respond(profile.id, true) },
                    onDecline = { peopleViewModel.respond(profile.id, false) },
                    onOpenProfile = { other ->
                        publicProfile = other to (statuses[other.id] ?: FriendState.NONE)
                    }
                )
            } else if (showProfile) {
                ProfileScreen(
                    profileViewModel,
                    onBack = { showProfile = false },
                    onSettingsClick = { showProfile = false; tab = 3 },
                    onEditProfileClick = { showProfile = false; onEditProfileClick() }
                )
            } else when (tab) {
                0 -> ChatsScreen(chatsViewModel, onOpenChat, onOpenOwnProfile = { showProfile = true })
                1 -> CallsScreen()
                2 -> PeopleScreen(
                    viewModel = peopleViewModel,
                    onOpenChat = onOpenChat,
                    onPreview = { profile, state -> preview = profile to state }
                )
                3 -> SettingsScreen(
                    viewModel = profileViewModel,
                    prefs = prefs,
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                    onPrivacySecurityClick = onPrivacySecurityClick,
                    onPrivacyPolicyClick = onPrivacyPolicyClick,
                    // v3.2.0: Settings profile card is the second Edit
                    // Profile entry point (same route as the overlay).
                    onEditProfileClick = onEditProfileClick
                )
            }
            // v3.2.2: floating bottom bar overlay (Telegram-style) —
            // always on top of the tab content; the lists scroll behind
            // it (each list carries 84dp bottom padding so its last
            // row is never hidden by the bar: 60dp bar + 8dp bottom
            // margin + 16dp gap).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                // 8dp gap between the voice mini-player and the bar —
                // the player never sits flush on the bar.
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // v3.1.0 (worker D): while a voice message plays — even
                // after leaving the chat — a compact player sits above
                // the bottom bar. Tap it to jump back to that chat.
                // Renders nothing when nothing is playing.
                // v3.2.2 FAB-overlap fix: on the Chats tab the FAB sits
                // at bottom 84dp, end 16dp (56dp wide, so its left edge
                // is 72dp from the screen edge). A full-width player
                // above the bar (76dp to ~134dp from the bottom) would
                // cover it, so here the player stops 68dp short on the
                // right (12dp column margin + 68dp = 80dp from the
                // screen edge), clearing the FAB by 8dp. Other tabs
                // have no FAB, so the player stays full-width there.
                VoiceMiniPlayer(
                    label = "Voice message",
                    modifier = if (tab == 0 && !showProfile && publicProfile == null) {
                        Modifier.padding(end = 68.dp)
                    } else {
                        Modifier
                    },
                    onTap = {
                        playbackState.conversationId?.let { onOpenChat(it) }
                    }
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.card.copy(alpha = 0.72f),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, colors.border.copy(alpha = 0.6f))
                ) {
                    // Custom tab row, not Material3 NavigationBar: its
                    // fixed 80dp height cannot shrink to the 60dp bar,
                    // and the mockup needs a green circle behind the
                    // selected icon. Same tabs, order and behaviour.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingNavTab(
                            label = "Chats",
                            selected = tab == 0 && !showProfile,
                            selectedIcon = Icons.Default.ChatBubble,
                            unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                            onClick = { tab = 0; showProfile = false }
                        )
                        FloatingNavTab(
                            label = "Calls",
                            selected = tab == 1 && !showProfile,
                            selectedIcon = Icons.Default.Phone,
                            unselectedIcon = Icons.Outlined.Call,
                            onClick = { tab = 1; showProfile = false }
                        )
                        FloatingNavTab(
                            label = "People",
                            selected = tab == 2 && !showProfile,
                            selectedIcon = Icons.Default.People,
                            unselectedIcon = Icons.Outlined.Group,
                            onClick = { tab = 2; showProfile = false }
                        )
                        FloatingNavTab(
                            label = "Settings",
                            selected = tab == 3 && !showProfile,
                            selectedIcon = Icons.Default.Settings,
                            unselectedIcon = Icons.Outlined.Settings,
                            onClick = { tab = 3; showProfile = false }
                        )
                    }
                }
            }
            } // closes the inner tab-content Box
        } // closes the outer Box
    } // closes the Scaffold

    preview?.let { (profile, state) ->
        ProfilePreviewDialog(
            profile = profile,
            state = state,
            online = onlineIds.contains(profile.id),
            showMessageButton = true,
            onMessage = {
                preview = null
                peopleViewModel.openChat(profile.id)
            },
            onAddFriend = { peopleViewModel.sendRequest(profile.id) },
            onCancelRequest = { peopleViewModel.cancelRequest(profile.id) },
            // Responding from the preview closes it; PeopleViewModel.respond
            // refreshes the requests list, so the row disappears.
            onAccept = { peopleViewModel.respond(profile.id, true); preview = null },
            onDecline = { peopleViewModel.respond(profile.id, false); preview = null },
            onDismiss = { preview = null },
            onViewProfile = { preview = null; publicProfile = profile to state }
        )
    }
}

/** One tab of the v3.2.2 floating pill bar (mockup: dark glassy bar,
 *  selected tab = green circle behind the icon, labels under icons).
 *  Every tab uses the same 40dp icon slot — a solid Emerald circle
 *  with a 22dp white icon when selected, a plain 24dp muted icon
 *  otherwise — so all four labels sit on one baseline. Slot (40dp)
 *  + 2dp gap + 11sp label is 55dp, centred in the 60dp bar. */
@Composable
private fun FloatingNavTab(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Emerald),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        selectedIcon,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Icon(
                    unselectedIcon,
                    contentDescription = label,
                    tint = colors.muted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.text else colors.muted
        )
    }
}
