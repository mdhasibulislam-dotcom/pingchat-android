package com.mdhasibulislam.pingchat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.VoicePlaybackManager
import com.mdhasibulislam.pingchat.ui.components.VoiceMiniPlayer
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
    // v3.2.1: the bottom nav is a FLOATING pill bar (Telegram-style),
    // drawn OVER the tab content instead of a Scaffold bottomBar, so
    // lists scroll behind it. Rounded 28dp corners, 16dp side margins,
    // 14dp bottom margin, shadow so it visibly floats. The bar itself
    // is added after the tab content below.
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
            // v3.2.1: floating bottom bar overlay (Telegram-style) —
            // always on top of the tab content; the lists scroll behind
            // it (each list carries ~110dp bottom padding so its last
            // row is never hidden by the bar).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, bottom = 14.dp)
            ) {
                // v3.1.0 (worker D): while a voice message plays — even
                // after leaving the chat — a compact player sits above
                // the bottom bar. Tap it to jump back to that chat.
                // Renders nothing when nothing is playing.
                VoiceMiniPlayer(
                    label = "Voice message",
                    onTap = {
                        playbackState.conversationId?.let { onOpenChat(it) }
                    }
                )
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.card,
                    shadowElevation = 8.dp
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        val navColors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = colors.text,
                            selectedTextColor = colors.text,
                            unselectedIconColor = colors.muted,
                            unselectedTextColor = colors.muted
                        )
                        NavigationBarItem(
                            selected = tab == 0 && !showProfile,
                            onClick = { tab = 0; showProfile = false },
                            icon = {
                                Icon(
                                    if (tab == 0) Icons.Default.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = "Chats"
                                )
                            },
                            label = {
                                Text(
                                    "Chats",
                                    fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = tab == 1 && !showProfile,
                            onClick = { tab = 1; showProfile = false },
                            icon = {
                                Icon(
                                    if (tab == 1) Icons.Default.Phone else Icons.Outlined.Call,
                                    contentDescription = "Calls"
                                )
                            },
                            label = {
                                Text(
                                    "Calls",
                                    fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = tab == 2 && !showProfile,
                            onClick = { tab = 2; showProfile = false },
                            icon = {
                                Icon(
                                    if (tab == 2) Icons.Default.People else Icons.Outlined.Group,
                                    contentDescription = "People"
                                )
                            },
                            label = {
                                Text(
                                    "People",
                                    fontWeight = if (tab == 2) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = tab == 3 && !showProfile,
                            onClick = { tab = 3; showProfile = false },
                            icon = {
                                Icon(
                                    if (tab == 3) Icons.Default.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = {
                                Text(
                                    "Settings",
                                    fontWeight = if (tab == 3) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = navColors
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
