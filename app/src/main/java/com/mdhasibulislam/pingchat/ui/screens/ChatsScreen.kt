package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mdhasibulislam.pingchat.data.ConversationItem
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.data.messageDisplayText
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ChatsViewModel

/** Chats tab: conversation list with search, last message, time, unread badge.
 *  v3.1.0: groups in the same list, long-press menu (pin / mute /
 *  archive), Archived section, new-group button.
 *  v3.1.1: story strip moved to the top, above the search bar. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(viewModel: ChatsViewModel, onOpenChat: (String) -> Unit) {
    val items by viewModel.items.collectAsState()
    val archivedItems by viewModel.archivedItems.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val onlineIds by SessionManager.onlineIds.collectAsState()
    val myId = SessionManager.user.collectAsState().value?.uid
    val colors = pingChatColors
    var query by remember { mutableStateOf("") }
    // v3.1.0: long-press menu target + archived section + new-group overlay.
    var menuItem by remember { mutableStateOf<ConversationItem?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    // Mute needs the on-device muted-ids cache (PingChatMessagingService
    // reads Prefs); MainScreen does not pass Prefs here, so read it from
    // the context and hand it to the ViewModel once.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.attachPrefs(Prefs(context))
        viewModel.refresh()
    }
    // 0 = Chat (all conversations), 1 = Active (online people only).
    var tab by remember { mutableStateOf(0) }

    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter {
            // displayName covers groups (group name) and 1-to-1 (peer name).
            it.displayName.contains(query, ignoreCase = true) ||
                (it.peer?.username?.contains(query, ignoreCase = true) == true)
        }
    }
    // Archived chats are searched the same way, but only shown when the
    // Archived section is expanded.
    val filteredArchived = remember(archivedItems, query) {
        if (query.isBlank()) archivedItems
        else archivedItems.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                (it.peer?.username?.contains(query, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Chats", style = MaterialTheme.typography.headlineMedium, color = colors.text)
                Text("Your conversations.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            // v3.1.0: new group (friends only — the create screen picks
            // members from the friend list).
            IconButton(onClick = { showCreateGroup = true }) {
                Icon(Icons.Default.GroupAdd, contentDescription = "New group", tint = colors.text)
            }
        }
        // v3.1.1: story ring strip at the top of the Chats tab (12-hour
        // stories) — above the search bar and the Chat | Active tabs.
        StoryStrip()
        // Messenger-style pill search: rounded, grey fill, no outline.
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search chats") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.muted) },
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
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        // Small tabs under the search bar: Chat | Active.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChatTabChip(
                text = "Chat",
                selected = tab == 0,
                onClick = { tab = 0 }
            )
            ChatTabChip(
                text = "Active",
                selected = tab == 1,
                onClick = { tab = 1 }
            )
        }
        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (loading && items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Emerald)
            }
        } else if (tab == 1) {
            // Active tab: only the people who are online right now
            // (1-to-1 chats only — groups have no single peer).
            val activeItems = filtered.filter { !it.isGroup && it.peer != null && onlineIds.contains(it.peer.id) }
            if (activeItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No one is active right now.",
                        color = colors.muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(activeItems, key = { it.conversation.id }) { item ->
                        ActiveRow(
                            item = item,
                            onClick = { onOpenChat(item.conversation.id) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (filtered.isEmpty() && filteredArchived.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                if (query.isBlank()) "No chats yet — add a friend to start chatting."
                                else "No chats match \"$query\".",
                                color = colors.muted,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.conversation.id }) { item ->
                        ConversationRow(
                            item = item,
                            online = !item.isGroup && item.peer != null && onlineIds.contains(item.peer.id),
                            myId = myId,
                            onClick = { onOpenChat(item.conversation.id) },
                            onLongPress = { menuItem = item }
                        )
                    }
                    // v3.1.0: Archived section — collapsed by default.
                    // Archived chats stay archived when new messages
                    // arrive; only Unarchive brings one back.
                    if (filteredArchived.isNotEmpty()) {
                        item(key = "archived-header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showArchived = !showArchived }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showArchived) "▼ Archived (${filteredArchived.size})"
                                    else "▶ Archived (${filteredArchived.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.muted,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (showArchived) {
                            items(filteredArchived, key = { "archived-${it.conversation.id}" }) { item ->
                                ConversationRow(
                                    item = item,
                                    online = false,
                                    myId = myId,
                                    onClick = { onOpenChat(item.conversation.id) },
                                    onLongPress = { menuItem = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // v3.1.0: long-press menu for a chat — Pin / Mute / Archive.
    val sheetItem = menuItem
    if (sheetItem != null) {
        ModalBottomSheet(
            onDismissRequest = { menuItem = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    sheetItem.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    viewModel.setPinned(sheetItem, !sheetItem.isPinned)
                    menuItem = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (sheetItem.isPinned) "Unpin chat" else "Pin chat",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(onClick = {
                    viewModel.setMuted(sheetItem, !sheetItem.isMuted)
                    menuItem = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (sheetItem.isMuted) "Unmute notifications" else "Mute notifications",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(onClick = {
                    viewModel.setArchived(sheetItem, !sheetItem.isArchived)
                    menuItem = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (sheetItem.isArchived) "Unarchive chat" else "Archive chat",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // v3.1.0: new-group screen as a fullscreen dialog overlay (no new
    // navigation route — MainActivity's NavHost is untouched). Creating
    // a group opens it at once.
    if (showCreateGroup) {
        Dialog(
            onDismissRequest = { showCreateGroup = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CreateGroupScreen(
                onGroupCreated = { id ->
                    showCreateGroup = false
                    onOpenChat(id)
                },
                onDismiss = { showCreateGroup = false }
            )
        }
    }
}

/** Small pill tab under the search bar (Chat / Active). */
@Composable
private fun ChatTabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = pingChatColors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Emerald else colors.inputBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else colors.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** One online person in the Active tab: avatar + name + "Active now". */
@Composable
private fun ActiveRow(item: ConversationItem, onClick: () -> Unit) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile = item.peer, size = AvatarSize.MD, online = true)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Active now",
                style = MaterialTheme.typography.bodyMedium,
                color = Emerald
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: ConversationItem,
    online: Boolean,
    myId: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val colors = pingChatColors
    val last = item.lastMessage
    // Group preview: "Name: message" for other members' messages, so a
    // group row reads like the chat itself. 1-to-1 rows keep the old
    // "You: …" / plain preview (v3.0.2 behaviour, unchanged).
    val senderPrefix = when {
        last == null || last.senderId == myId -> if (last?.senderId == myId) "You: " else ""
        item.isGroup -> {
            val sender = item.memberProfiles.firstOrNull { it.id == last.senderId }
            val name = sender?.fullName?.ifBlank { sender.username } ?: ""
            if (name.isNotBlank()) "$name: " else ""
        }
        else -> ""
    }
    val preview = when {
        last == null -> "No messages yet — say hello."
        last.kind == "photo" -> senderPrefix + "Photo"
        last.kind == "voice" -> senderPrefix + "Voice message"
        // messageDisplayText never returns blank: a text-kind row with an
        // empty body falls back to "Voice message" / "Photo" / "Message".
        else -> senderPrefix + messageDisplayText(last)
    }
    // Groups have no single peer: show the group photo (or initials).
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
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile = avatarProfile, size = AvatarSize.MD, online = if (item.isGroup) null else online)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // v3.1.0: pinned (top of list) and muted (no notifications).
                if (item.isPinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = colors.muted,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (item.isMuted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Muted",
                        tint = colors.muted,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (last != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = TimeFormat.fmtListTime(last.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Emerald),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
