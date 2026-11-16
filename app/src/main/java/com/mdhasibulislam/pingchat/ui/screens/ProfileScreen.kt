package com.mdhasibulislam.pingchat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.PingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

/** My profile: hero, photo upload, edit form, stats, Settings button. */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onSettingsClick: () -> Unit) {
    val profile by viewModel.profile.collectAsState()
    val user by viewModel.user.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val friendsCount by viewModel.friendsCount.collectAsState()
    val chatsCount by viewModel.chatsCount.collectAsState()
    val colors = PingChatColors
    val context = LocalContext.current

    var editing by remember { mutableStateOf(false) }
    var name by remember(profile?.fullName) { mutableStateOf(profile?.fullName ?: "") }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio ?: "") }
    var isPublic by remember(profile?.isPublic) { mutableStateOf(profile?.isPublic ?: true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        viewModel.uploadAvatar(bytes, ext, mime)
    }

    val memberSince = remember(profile?.createdAt) {
        val instant = TimeFormat.parse(profile?.createdAt)
        if (instant == null) ""
        else DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
            .format(instant.atZone(java.time.ZoneId.systemDefault()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero.
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(profile = profile, size = AvatarSize.LG)
            IconButton(
                onClick = { picker.launch("image/*") },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .padding(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(36.dp)) {
                        drawCircle(color = Emerald)
                    }
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            profile?.fullName?.ifBlank { profile?.username } ?: "…",
            style = MaterialTheme.typography.titleLarge,
            color = colors.text
        )
        Text("@${profile?.username ?: ""}", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        Spacer(Modifier.height(8.dp))
        Text(
            profile?.bio?.ifBlank { "No bio yet." } ?: "No bio yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text
        )
        if (memberSince.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Member since $memberSince", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$friendsCount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text("Friends", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$chatsCount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text("Chats", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        if (user?.email != null) {
            Spacer(Modifier.height(8.dp))
            Text("Signed in as ${user!!.email}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (info != null) {
            Spacer(Modifier.height(12.dp))
            Text(info ?: "", color = Emerald, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        if (editing) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Public profile", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Text(
                        "Anyone can see your full profile. Private profiles show full info only to friends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
                Switch(checked = isPublic, onCheckedChange = { isPublic = it })
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.saveProfile(name, bio, isPublic)
                    editing = false
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save profile")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        } else {
            OutlinedButton(
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit profile")
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSettingsClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Settings")
        }
    }
}
