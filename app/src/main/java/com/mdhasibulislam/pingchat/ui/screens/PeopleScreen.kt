package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.Friendship
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.AccentBlue
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.PeopleViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rows shown before "See all" expands a request section in place. */
private const val REQUEST_PREVIEW_COUNT = 3

/** People tab: directory + friend requests. Mirrors js/people.js + js/friends.js. */
@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel,
    onOpenChat: (String) -> Unit,
    onPreview: (Profile, FriendState) -> Unit
) {
    val users by viewModel.users.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    val mutualCounts by viewModel.mutualCounts.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val openedConversation by viewModel.openedConversation.collectAsState()
    val onlineIds by SessionManager.onlineIds.collectAsState()
    val myProfile by SessionManager.profile.collectAsState()
    val colors = pingChatColors
    var query by remember { mutableStateOf("") }
    // 0 = Requests, 1 = All Users (mockup order: Requests first).
    var tab by remember { mutableIntStateOf(0) }
    var incomingExpanded by remember { mutableStateOf(false) }
    var sentExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(openedConversation) {
        val id = openedConversation ?: return@LaunchedEffect
        viewModel.consumeOpenedConversation()
        onOpenChat(id)
    }

    val matchesQuery: (Profile) -> Boolean = {
        query.isBlank() ||
            it.fullName.contains(query, ignoreCase = true) ||
            it.username.contains(query, ignoreCase = true)
    }
    val filtered = remember(users, query) { users.filter(matchesQuery) }
    val filteredIncoming = remember(requests, query) { requests.filter { matchesQuery(it.second) } }
    val filteredSent = remember(sentRequests, query) { sentRequests.filter(matchesQuery) }
    val shownIncoming =
        if (incomingExpanded) filteredIncoming else filteredIncoming.take(REQUEST_PREVIEW_COUNT)
    val shownSent =
        if (sentExpanded) filteredSent else filteredSent.take(REQUEST_PREVIEW_COUNT)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("People", style = MaterialTheme.typography.headlineMedium, color = colors.text)
                Text("Find friends and send requests.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            // Add person: jump to the directory and focus the search field.
            IconButton(onClick = {
                tab = 1
                searchFocusRequester.requestFocus()
            }) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = "Add person",
                    tint = colors.primary
                )
            }
        }
        // Messenger-style pill search: rounded, grey fill, no outline.
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search people...") },
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
                .focusRequester(searchFocusRequester)
        )
        TabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Requests")
                        // Real pending incoming count; hidden when zero.
                        if (requests.isNotEmpty()) {
                            Badge(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ) {
                                Text(
                                    requests.size.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            )
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("All Users") })
        }
        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (tab == 0) {
            // Requests tab: incoming + sent + profile-visibility info,
            // all in one scroll so nothing is out of reach.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 127.dp)
            ) {
                item {
                    RequestSectionHeader(
                        title = "Incoming Requests",
                        showSeeAll = filteredIncoming.size > REQUEST_PREVIEW_COUNT,
                        expanded = incomingExpanded,
                        onToggleExpand = { incomingExpanded = !incomingExpanded }
                    )
                }
                if (filteredIncoming.isEmpty() && !loading) {
                    item {
                        Text(
                            "No pending incoming requests.",
                            color = colors.muted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(shownIncoming, key = { it.second.id }) { (friendship, profile) ->
                        IncomingRequestRow(
                            friendship = friendship,
                            profile = profile,
                            online = onlineIds.contains(profile.id),
                            mutualCount = mutualCounts[profile.id] ?: 0,
                            onPreview = { onPreview(profile, FriendState.PENDING_IN) },
                            onAccept = { viewModel.respond(friendship.requesterId, true) },
                            onDecline = { viewModel.respond(friendship.requesterId, false) }
                        )
                    }
                }
                item {
                    RequestSectionHeader(
                        title = "Sent Requests",
                        showSeeAll = filteredSent.size > REQUEST_PREVIEW_COUNT,
                        expanded = sentExpanded,
                        onToggleExpand = { sentExpanded = !sentExpanded }
                    )
                }
                if (filteredSent.isEmpty() && !loading) {
                    item {
                        Text(
                            "No pending sent requests.",
                            color = colors.muted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(shownSent, key = { it.id }) { profile ->
                        SentRequestRow(
                            profile = profile,
                            online = onlineIds.contains(profile.id),
                            onPreview = { onPreview(profile, FriendState.PENDING_OUT) },
                            onCancel = { viewModel.cancelRequest(profile.id) }
                        )
                    }
                }
                item {
                    Text(
                        "Profile Visibility",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
                    )
                    // Highlight the card matching my actual setting
                    // (SessionManager.profile.isPublic); both neutral
                    // while my profile is still loading.
                    VisibilityCard(
                        isPublicCard = true,
                        isCurrent = myProfile?.isPublic == true
                    )
                    Spacer(Modifier.height(12.dp))
                    VisibilityCard(
                        isPublicCard = false,
                        isCurrent = myProfile?.isPublic == false
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        } else {
            if (filtered.isEmpty() && !loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No people found.", color = colors.muted, modifier = Modifier.padding(24.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 127.dp)) {
                    items(filtered, key = { it.id }) { person ->
                        val state = statuses[person.id] ?: FriendState.NONE
                        PersonRow(
                            person = person,
                            online = onlineIds.contains(person.id),
                            state = state,
                            canSeeBio = canSeeDetails(person, state),
                            onPreview = { onPreview(person, state) },
                            onAction = {
                                when (state) {
                                    FriendState.NONE, FriendState.DECLINED -> viewModel.sendRequest(person.id)
                                    FriendState.PENDING_OUT -> viewModel.cancelRequest(person.id)
                                    FriendState.PENDING_IN -> { /* handled in the request row */ }
                                    FriendState.FRIENDS -> viewModel.openChat(person.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Section title + "See all →" link that expands the list in place. */
@Composable
private fun RequestSectionHeader(
    title: String,
    showSeeAll: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            modifier = Modifier.weight(1f)
        )
        if (showSeeAll) {
            TextButton(onClick = onToggleExpand) {
                Text(if (expanded) "Show less" else "See all →", color = colors.primary)
            }
        }
    }
}

/** Incoming request row: avatar + online dot, name, @username, mutual friends, Accept / Decline. */
@Composable
private fun IncomingRequestRow(
    friendship: Friendship,
    profile: Profile,
    online: Boolean,
    mutualCount: Int,
    onPreview: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Tap the row to see who sent it: the sender's profile
            // (photo, name, bio, online status) with Accept / Decline
            // on that same view.
            .clickable(onClick = onPreview)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile = profile, size = AvatarSize.MD, online = online)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.fullName.ifBlank { profile.username },
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("@${profile.username}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            // Real mutual-friend count only; hidden when zero or unknown
            // (a failed lookup must never show a made-up number).
            if (mutualCount > 0) {
                Text(
                    "$mutualCount ${if (mutualCount == 1) "mutual friend" else "mutual friends"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
        }
        Button(
            onClick = onAccept,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text("Accept")
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onDecline,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text("Decline")
        }
    }
}

/** Sent request row: avatar + online dot, name, @username, "Request Sent" + Cancel Request. */
@Composable
private fun SentRequestRow(
    profile: Profile,
    online: Boolean,
    onPreview: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile = profile, size = AvatarSize.MD, online = online)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.fullName.ifBlank { profile.username },
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("@${profile.username}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Request Sent", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        OutlinedButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text("Cancel Request")
        }
    }
}

/**
 * Public / Private profile explainer card, with the mockup's exact
 * wording. [isCurrent] highlights the card matching my actual
 * profile visibility (thicker border + "Current" chip).
 */
@Composable
private fun VisibilityCard(isPublicCard: Boolean, isCurrent: Boolean) {
    val colors = pingChatColors
    val accent = if (isPublicCard) Emerald else AccentBlue
    val title = if (isPublicCard) {
        "Public Profile (Visible to Everyone)"
    } else {
        "Private Profile (Visible to Friends Only)"
    }
    val items = if (isPublicCard) {
        listOf(
            "Profile picture & cover photo visible",
            "About, city, education visible",
            "Social links visible",
            "Stories visible (if enabled)",
            "Anyone can send a friend request"
        )
    } else {
        listOf(
            "Profile photo & cover photo hidden (for non-friends)",
            "About, city, education hidden",
            "Social links hidden",
            "Stories hidden (for non-friends)",
            "Still can send a friend request"
        )
    }
    val note = if (isPublicCard) {
        "Public profile means your information is open for everyone."
    } else {
        "Only your friends can see your full profile."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = accent.copy(alpha = if (isCurrent) 0.9f else 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isPublicCard) Icons.Default.Public else Icons.Default.Lock,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Current", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        items.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.text2)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

/** Private profiles show full info only to friends or while a request is pending. */
fun canSeeDetails(profile: Profile, state: FriendState): Boolean {
    return profile.isPublic ||
        state == FriendState.FRIENDS ||
        state == FriendState.PENDING_IN ||
        state == FriendState.PENDING_OUT
}

@Composable
private fun PersonRow(
    person: Profile,
    online: Boolean,
    state: FriendState,
    canSeeBio: Boolean,
    onPreview: () -> Unit,
    onAction: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            profile = person,
            size = AvatarSize.MD,
            online = online,
            modifier = Modifier.clickable(onClick = onPreview)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPreview)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.fullName.ifBlank { person.username },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (online) {
                    Text(" · Online", style = MaterialTheme.typography.bodySmall, color = colors.text)
                }
            }
            Text("@${person.username}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            if (canSeeBio && !person.bio.isNullOrBlank()) {
                Text(
                    person.bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (state) {
            FriendState.NONE, FriendState.DECLINED -> OutlinedButton(onClick = onAction) { Text("Add friend") }
            FriendState.PENDING_OUT -> OutlinedButton(onClick = onAction) { Text("Pending") }
            FriendState.PENDING_IN -> Text("Request pending", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            FriendState.FRIENDS -> Button(onClick = onAction) { Text("Message") }
        }
    }
}

/** Profile preview dialog — same contents and order as the web app's modal. */
@Composable
fun ProfilePreviewDialog(
    profile: Profile,
    state: FriendState,
    online: Boolean,
    showMessageButton: Boolean,
    onMessage: () -> Unit,
    onAddFriend: () -> Unit,
    onCancelRequest: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    onClearChat: (() -> Unit)? = null,
    /** v3.1.0: open the full public profile screen for this person. */
    onViewProfile: (() -> Unit)? = null
) {
    val colors = pingChatColors
    val memberSince = remember(profile.createdAt) {
        val instant = TimeFormat.parse(profile.createdAt)
        if (instant == null) ""
        else DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US).format(instant.atZone(java.time.ZoneId.systemDefault()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = null,
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Avatar(profile = profile, size = AvatarSize.LG, online = online)
                Spacer(Modifier.height(12.dp))
                Text(
                    profile.fullName.ifBlank { profile.username },
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text
                )
                Text("@${profile.username}", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Text(
                    if (online) "Online" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(Modifier.height(12.dp))
                if (canSeeDetails(profile, state)) {
                    if (!profile.bio.isNullOrBlank()) {
                        Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (memberSince.isNotBlank()) {
                        Text("Member since $memberSince", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onViewProfile != null) {
                        OutlinedButton(onClick = onViewProfile) { Text("View profile") }
                    }
                    when (state) {
                        FriendState.FRIENDS -> {
                            if (showMessageButton) {
                                Button(onClick = onMessage) { Text("Message") }
                            }
                        }
                        FriendState.NONE, FriendState.DECLINED -> {
                            Button(onClick = onAddFriend) { Text("Add friend") }
                        }
                        FriendState.PENDING_OUT -> {
                            OutlinedButton(onClick = onCancelRequest) { Text("Cancel request") }
                        }
                        FriendState.PENDING_IN -> {
                            Button(onClick = onAccept) { Text("Accept") }
                            OutlinedButton(onClick = onDecline) { Text("Decline") }
                        }
                    }
                    if (onClearChat != null) {
                        TextButton(onClick = onClearChat) {
                            Text("Clear chat", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    )
}
