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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onEditProfileClick: () -> Unit = {},
    // v3.2.2 fix: Settings tab rows for Notifications / Appearance —
    // MainActivity wires these to the "notifications" / "appearance"
    // routes (same pattern as onPrivacySecurityClick above). Without
    // them the tab's SettingsScreen fell back to its empty defaults
    // and both rows did nothing.
    onNotificationsClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {}
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
    // v3.2.5: unfriend confirmation — set from the preview dialog's
    // Unfriend button, confirmed here before PeopleViewModel.removeFriend.
    var confirmUnfriend by remember { mutableStateOf<Profile?>(null) }

    // v3.2.1: no top bar — the Chats tab owns its header (logo + search
    // + avatar, which opens the own profile), and Calls / People /
    // Settings each have their own in-content title. The old TopAppBar
    // duplicated the Chats header and carried the dark-mode toggle,
    // which now lives only in Settings > Appearance.
    // v3.2.2: the bottom nav is a FLOATING pill bar (Telegram-style),
    // drawn OVER the tab content instead of a Scaffold bottomBar, so
    // lists scroll behind it. Hasibul's design (measured screenshot):
    // 63dp tall, full-pill corners (~31dp), 35dp side margins, 48dp
    // bottom margin, 4dp shadow, translucent card colour (0.72 alpha)
    // with a subtle border; the selected tab is a 58x47dp Emerald
    // rounded-square pill (icon + label both inside it), 18dp from
    // the bar's left edge. The bar itself is added after the tab
    // content below.
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
                    onEditProfileClick = onEditProfileClick,
                    // v3.2.2 fix: forward the Notifications / Appearance
                    // callbacks — this tab is the Settings screen the
                    // user actually sees; without these the rows ran
                    // SettingsScreen's empty defaults and did nothing.
                    onNotificationsClick = onNotificationsClick,
                    onAppearanceClick = onAppearanceClick
                )
            }
            // v3.2.2: floating bottom bar overlay (Telegram-style) —
            // always on top of the tab content; the lists scroll behind
            // it (each list carries 127dp bottom padding so its last
            // row is never hidden by the bar: 63dp bar + 48dp bottom
            // margin + 16dp gap).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 35.dp, end = 35.dp, bottom = 48.dp),
                // 8dp gap between the voice mini-player and the bar —
                // the player never sits flush on the bar.
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // v3.1.0 (worker D): while a voice message plays — even
                // after leaving the chat — a compact player sits above
                // the bottom bar. Tap it to jump back to that chat.
                // Renders nothing when nothing is playing.
                // v3.2.2 FAB-overlap fix: on the Chats tab the FAB sits
                // at bottom 119dp, end 22dp (48dp wide, so its left edge
                // is 70dp from the screen edge). A full-width player
                // above the bar would cover it, so here the player stops
                // 43dp short on the right (35dp column margin + 43dp =
                // 78dp from the screen edge), clearing the FAB by 8dp.
                // Other tabs have no FAB, so the player stays full-width
                // there.
                VoiceMiniPlayer(
                    label = "Voice message",
                    modifier = if (tab == 0 && !showProfile && publicProfile == null) {
                        Modifier.padding(end = 43.dp)
                    } else {
                        Modifier
                    },
                    onTap = {
                        playbackState.conversationId?.let { onOpenChat(it) }
                    }
                )
                Surface(
                    shape = RoundedCornerShape(31.dp),
                    color = colors.card.copy(alpha = 0.72f),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, colors.border.copy(alpha = 0.6f))
                ) {
                    // Custom tab row, not Material3 NavigationBar: its
                    // fixed 80dp height cannot shrink to the 63dp bar,
                    // and the mockup needs a green pill behind the
                    // selected tab. Same tabs, order and behaviour.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(63.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hasibul's design: the selected pill sits 18dp
                        // from the bar's left edge; the first tab's slot
                        // is exactly the 58dp pill width.
                        Spacer(Modifier.width(18.dp))
                        FloatingNavTab(
                            modifier = Modifier.width(58.dp),
                            label = "Chats",
                            selected = tab == 0 && !showProfile,
                            selectedIcon = Icons.Default.ChatBubble,
                            unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                            onClick = { tab = 0; showProfile = false }
                        )
                        FloatingNavTab(
                            modifier = Modifier.weight(1f),
                            label = "Calls",
                            selected = tab == 1 && !showProfile,
                            selectedIcon = Icons.Default.Phone,
                            unselectedIcon = Icons.Outlined.Call,
                            onClick = { tab = 1; showProfile = false }
                        )
                        FloatingNavTab(
                            modifier = Modifier.weight(1f),
                            label = "People",
                            selected = tab == 2 && !showProfile,
                            selectedIcon = Icons.Default.People,
                            unselectedIcon = Icons.Outlined.Group,
                            onClick = { tab = 2; showProfile = false }
                        )
                        FloatingNavTab(
                            modifier = Modifier.weight(1f),
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
            onUnfriend = { confirmUnfriend = profile },
            onViewProfile = { preview = null; publicProfile = profile to state }
        )
    }
    // v3.2.5: unfriend confirmation (same AlertDialog style as the
    // Clear chat confirm in ChatScreen). Confirming removes the
    // friendship; the ex-friend can send a new request afterwards.
    confirmUnfriend?.let { person ->
        AlertDialog(
            onDismissRequest = { confirmUnfriend = null },
            title = { Text("Unfriend ${person.fullName.ifBlank { person.username }}?") },
            text = { Text("You will no longer be friends. You can send a new friend request later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnfriend = null
                    preview = null
                    peopleViewModel.removeFriend(person.id)
                }) { Text("Unfriend", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnfriend = null }) { Text("Cancel") }
            }
        )
    }
}

/** One tab of the v3.2.2 floating pill bar (Hasibul's design: the
 *  selected tab is a 58x47dp Emerald rounded-square pill with the
 *  icon + label both inside it, white; unselected tabs show a plain
 *  24dp muted icon above an 11sp label, centred in their slot).
 *  The first tab's slot is exactly the 58dp pill width (plus an 18dp
 *  inset from the bar edge, applied by the Row); the other three
 *  tabs share the remaining width equally, so the pill is centred
 *  in its slot whichever tab is selected. */
@Composable
private fun FloatingNavTab(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            // Green pill: 58dp wide, 47dp tall, 12dp corners — icon
            // and label both inside, white. (63 - 47) / 2 = 8dp above
            // and below inside the bar.
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 47.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Emerald),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        selectedIcon,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    unselectedIcon,
                    contentDescription = label,
                    tint = colors.muted,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.muted
                )
            }
        }
    }
}
