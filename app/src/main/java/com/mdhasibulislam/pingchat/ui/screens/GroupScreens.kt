package com.mdhasibulislam.pingchat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mdhasibulislam.pingchat.data.FriendsRepository
import com.mdhasibulislam.pingchat.data.GroupInfo
import com.mdhasibulislam.pingchat.data.GroupRepository
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.colorFor
import com.mdhasibulislam.pingchat.ui.theme.initials
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import kotlinx.coroutines.launch

/**
 * Group chats (v3.1.0, worker E). Both screens render as full-screen
 * overlays inside the existing screens (no new navigation routes —
 * MainActivity's NavHost is untouched):
 * - [CreateGroupScreen] is opened from the Chats tab (new-group button).
 * - [GroupSettingsScreen] is opened from a group chat's header.
 * Group calls are NOT built — calls stay 1-to-1.
 */

// ---------- create group ----------

@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = pingChatColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groupRepo = remember { GroupRepository() }
    val friendsRepo = remember { FriendsRepository() }

    var name by remember { mutableStateOf("") }
    var friends by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoMime by remember { mutableStateOf("image/jpeg") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Group photo: gallery only (photo only — no video, like chat photos).
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return@rememberLauncherForActivityResult
        photoBytes = bytes
        photoMime = context.contentResolver.getType(uri) ?: "image/jpeg"
        photoUri = uri.toString()
    }

    LaunchedEffect(Unit) {
        friends = try {
            friendsRepo.loadFriends()
        } catch (e: Exception) {
            error = "Could not load your friends. Try again."
            emptyList()
        }
        loading = false
    }

    fun create() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || selected.isEmpty() || creating) return
        creating = true
        scope.launch {
            try {
                val id = groupRepo.createGroup(trimmed, selected.toList(), photoBytes, photoMime)
                onGroupCreated(id)
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() }
                    ?: "Could not create the group. Try again."
                creating = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text(
                "New group",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { create() },
                enabled = name.trim().isNotEmpty() && selected.isNotEmpty() && !creating
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Emerald)
                } else {
                    Text("Create", color = Emerald, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group photo (optional): tap to pick from the gallery.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colorFor(name.ifBlank { "Group" }))
                    .clickable { photoPicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Group photo",
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Group, contentDescription = "Add group photo", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Group name") },
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
        }
        Text(
            text = "Add members (${selected.size} selected) — friends only",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Emerald)
            }
        } else if (friends.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No friends yet — add a friend first, then create a group.",
                    color = colors.muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(friends, key = { it.id }) { friend ->
                    val isSelected = selected.contains(friend.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (isSelected) selected - friend.id
                                else selected + friend.id
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(profile = friend, size = AvatarSize.MD)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = friend.fullName.ifBlank { friend.username },
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(checked = isSelected, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

// ---------- group settings ----------

@Composable
fun GroupSettingsScreen(
    conversationId: String,
    onDismiss: () -> Unit,
    onLeaveGroup: () -> Unit
) {
    val colors = pingChatColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groupRepo = remember { GroupRepository() }

    var info by remember { mutableStateOf<GroupInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Profile?>(null) }
    var addable by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var showAddMembers by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        busy = true
        scope.launch {
            try {
                groupRepo.updateGroup(conversationId, photoBytes = bytes, photoMime = mime)
                info = groupRepo.loadGroupInfo(conversationId)
            } catch (e: Exception) {
                error = "Could not change the group photo. Try again."
            }
            busy = false
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            info = groupRepo.loadGroupInfo(conversationId)
            addable = try {
                groupRepo.loadFriendsNotInGroup(conversationId)
            } catch (e: Exception) {
                emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(conversationId) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text(
                "Group settings",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )
        }
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Emerald)
            }
            return@Column
        }
        val current = info
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Could not load this group.", color = colors.muted, modifier = Modifier.padding(24.dp))
            }
            return@Column
        }
        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        // Group photo + name. Admins can tap the photo to change it and
        // edit the name; members see them read-only.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colorFor(current.conversation.groupName ?: "Group"))
                    .then(
                        if (current.isAdmin) Modifier.clickable { photoPicker.launch("image/*") }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val url = current.conversation.groupAvatarUrl
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Group photo",
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = initials(current.conversation.groupName ?: "G"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (editingName) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    TextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
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
                    IconButton(onClick = {
                        val trimmed = nameDraft.trim()
                        if (trimmed.isEmpty()) {
                            editingName = false
                            return@IconButton
                        }
                        busy = true
                        scope.launch {
                            try {
                                groupRepo.updateGroup(conversationId, name = trimmed)
                                info = groupRepo.loadGroupInfo(conversationId)
                            } catch (e: Exception) {
                                error = "Could not rename the group. Try again."
                            }
                            busy = false
                            editingName = false
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save name", tint = Emerald)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = current.conversation.groupName ?: "Group",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text
                    )
                    if (current.isAdmin) {
                        TextButton(onClick = {
                            nameDraft = current.conversation.groupName ?: ""
                            editingName = true
                        }) { Text("Edit") }
                    }
                }
            }
            Text(
                text = "${current.memberCount} members${if (current.isAdmin) " · You are an admin" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )
        }
        Spacer(Modifier.height(16.dp))
        if (current.isAdmin && addable.isNotEmpty()) {
            TextButton(
                onClick = { showAddMembers = !showAddMembers },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Emerald)
                Spacer(Modifier.width(4.dp))
                Text(if (showAddMembers) "Hide friends" else "Add members", color = Emerald)
            }
            if (showAddMembers) {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    items(addable, key = { it.id }) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    busy = true
                                    scope.launch {
                                        try {
                                            groupRepo.addMember(conversationId, friend.id)
                                            reload()
                                        } catch (e: Exception) {
                                            error = "Could not add ${friend.fullName.ifBlank { friend.username }}. Try again."
                                        }
                                        busy = false
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(profile = friend, size = AvatarSize.SM)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = friend.fullName.ifBlank { friend.username },
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Add", color = Emerald)
                        }
                    }
                }
            }
        }
        Text(
            "Members",
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(current.members, key = { it.participant.userId }) { member ->
                val profile = member.profile
                val isMe = member.participant.userId == current.myUserId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(profile = profile, size = AvatarSize.MD)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = (profile?.fullName?.ifBlank { profile.username } ?: "Unknown")
                                + if (isMe) " (you)" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (member.isAdmin) {
                            Text("Admin", style = MaterialTheme.typography.bodySmall, color = Emerald)
                        }
                    }
                    // Admin actions (never on myself — leaving is the
                    // separate button below; the last admin cannot be
                    // removed, the RPC refuses it).
                    if (current.isAdmin && !isMe && profile != null) {
                        if (!member.isAdmin) {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        groupRepo.makeAdmin(conversationId, profile.id)
                                        reload()
                                    } catch (e: Exception) {
                                        error = "Could not make ${profile.username} an admin. Try again."
                                    }
                                }
                            }) { Text("Make admin") }
                        }
                        TextButton(onClick = { confirmRemove = profile }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Emerald)
            }
        }
        OutlinedButton(
            onClick = { confirmLeave = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text("Leave group", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave group?") },
            text = { Text("You will stop receiving messages from this group. If you are the last admin, another member becomes admin.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    scope.launch {
                        try {
                            groupRepo.leaveGroup(conversationId)
                            onLeaveGroup()
                        } catch (e: Exception) {
                            error = "Could not leave the group. Try again."
                        }
                    }
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
            }
        )
    }

    val toRemove = confirmRemove
    if (toRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove member?") },
            text = { Text("Remove ${toRemove.fullName.ifBlank { toRemove.username }} from this group?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    scope.launch {
                        try {
                            groupRepo.removeMember(conversationId, toRemove.id)
                            reload()
                        } catch (e: Exception) {
                            error = "Could not remove this member. Try again."
                        }
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            }
        )
    }
}
