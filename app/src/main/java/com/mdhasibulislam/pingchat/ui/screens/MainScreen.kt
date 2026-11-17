package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ChatsViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.PeopleViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

/** Main app shell: top bar + bottom tabs (Chats / Calls / People / Profile). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenChat: (String) -> Unit,
    onSettingsClick: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val chatsViewModel: ChatsViewModel = viewModel()
    val peopleViewModel: PeopleViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val myProfile by SessionManager.profile.collectAsState()
    val onlineIds by SessionManager.onlineIds.collectAsState()
    val colors = pingChatColors

    // Profile preview dialog (People tab) — state lives here so it can use
    // the same PeopleViewModel instance for the friend actions.
    var preview by remember { mutableStateOf<Pair<Profile, FriendState>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Emerald),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("P", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "PingChat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colors.text
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onDarkThemeChange(!darkTheme) }) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (darkTheme) "Light theme" else "Dark theme",
                            tint = colors.text
                        )
                    }
                    Avatar(
                        profile = myProfile,
                        size = AvatarSize.SM,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        },
        bottomBar = {
            // Messenger-style bottom bar: plain background, thin top line, no
            // pill behind the selected icon — selected tab is a filled dark
            // icon, unselected tabs are grey outline icons.
            Column {
                HorizontalDivider(color = colors.border)
                NavigationBar(
                    containerColor = colors.card,
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
                        selected = tab == 0,
                        onClick = { tab = 0 },
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
                        selected = tab == 1,
                        onClick = { tab = 1 },
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
                        selected = tab == 2,
                        onClick = { tab = 2 },
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
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = {
                            Icon(
                                if (tab == 3) Icons.Default.Person else Icons.Outlined.PersonOutline,
                                contentDescription = "Profile"
                            )
                        },
                        label = {
                            Text(
                                "Profile",
                                fontWeight = if (tab == 3) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> ChatsScreen(chatsViewModel, onOpenChat)
                1 -> CallsScreen()
                2 -> PeopleScreen(
                    viewModel = peopleViewModel,
                    onOpenChat = onOpenChat,
                    onPreview = { profile, state -> preview = profile to state }
                )
                3 -> ProfileScreen(profileViewModel, onSettingsClick)
            }
        }
    }

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
            onAccept = { peopleViewModel.respond(profile.id, true) },
            onDecline = { peopleViewModel.respond(profile.id, false) },
            onDismiss = { preview = null }
        )
    }
}
