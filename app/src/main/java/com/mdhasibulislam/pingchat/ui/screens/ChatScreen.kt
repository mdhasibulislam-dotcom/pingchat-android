package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdhasibulislam.pingchat.data.ChatMessage
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.Reaction
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.TickState
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.components.TickIcon
import com.mdhasibulislam.pingchat.ui.components.TypingIndicator
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ChatViewModel

class ChatViewModelFactory(private val conversationId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(conversationId) as T
    }
}

private sealed interface ChatListItem {
    data class Day(val text: String) : ChatListItem
    data class Gap(val text: String) : ChatListItem
    data class Msg(val message: ChatMessage) : ChatListItem
}

private fun buildDisplayItems(messages: List<ChatMessage>): List<ChatListItem> {
    val out = mutableListOf<ChatListItem>()
    var prev: ChatMessage? = null
    for (m in messages) {
        val p = prev
        if (p == null || !TimeFormat.isSameDay(p.createdAt, m.createdAt)) {
            out += ChatListItem.Day(TimeFormat.dayLabel(m.createdAt))
        } else if (TimeFormat.minutesBetween(p.createdAt, m.createdAt) >= 20) {
            out += ChatListItem.Gap(TimeFormat.fmtTime(m.createdAt))
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
    val onlineIds by SessionManager.onlineIds.collectAsState()
    val myId = viewModel.myId
    val colors = pingChatColors

    val peerOnline = peer?.let { onlineIds.contains(it.id) } ?: false
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var menuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showOverflow by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

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
        if (messages.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: back, avatar + online dot, name, Online/Offline, overflow menu.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Avatar(profile = peer, size = AvatarSize.SM, online = peerOnline)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer?.fullName?.ifBlank { peer?.username } ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (peerOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (peerOnline) colors.text else colors.muted
                )
            }
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = colors.text)
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text("View profile") },
                        onClick = {
                            showOverflow = false
                            showProfile = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear chat") },
                        onClick = {
                            showOverflow = false
                            confirmClear = true
                        }
                    )
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
                    is ChatListItem.Day -> "day-${it.text}-${messages.size}"
                    is ChatListItem.Gap -> "gap-${it.text}"
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
                            onLongPress = { menuMessage = m },
                            onReactionChipClick = { emoji -> viewModel.toggleReaction(m.id, emoji) }
                        )
                    }
                }
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
                        replyTo!!.body,
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

        // Composer.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    if (it.isNotBlank()) viewModel.onTyping() else viewModel.onTypingStopped()
                },
                placeholder = { Text("Type a message…") },
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !sending
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (input.isNotBlank()) colors.text else colors.muted
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
                onDismiss = { showProfile = false }
            )
        }
    }
}

@Composable
private fun DayLabel(text: String) {
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
    onLongPress: () -> Unit,
    onReactionChipClick: (String) -> Unit
) {
    val colors = pingChatColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) colors.bubbleMe else colors.bubbleThem)
                .then(
                    if (mine) Modifier
                    else Modifier.border(1.dp, colors.border, RoundedCornerShape(16.dp))
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
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
                            text = replyTo.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (mine) Color.White.copy(alpha = 0.9f) else colors.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (message.kind == "voice") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (mine) Color.White else colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Voice message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mine) Color.White else colors.text
                        )
                    }
                    Text(
                        text = "Playback comes in the next update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mine) Color.White.copy(alpha = 0.8f) else colors.muted
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mine) Color.White else colors.text,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (mine && tickState != null) {
                            Spacer(Modifier.width(6.dp))
                            TickIcon(state = tickState, onBubble = true)
                        }
                    }
                }
            }
        }
        if (reactions.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                    TextButton(onClick = { onReactionChipClick(emoji) }) {
                        Text(
                            "$emoji ${list.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text
                        )
                    }
                }
            }
        }
    }
}
