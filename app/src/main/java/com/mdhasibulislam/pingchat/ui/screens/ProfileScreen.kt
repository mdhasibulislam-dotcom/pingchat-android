package com.mdhasibulislam.pingchat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.colorFor
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

/** WhatsApp-style About presets (tap one to fill the About field). */
private val AboutPresets = listOf(
    "Available", "Busy", "At work", "In a meeting", "Sleeping", "Urgent calls only"
)

/** My profile, WhatsApp-style: cover banner, photo, About, phone, stats, info rows. */
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
    val photosSent by viewModel.photosSent.collectAsState()
    val voiceSent by viewModel.voiceSent.collectAsState()
    val colors = pingChatColors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var editing by remember { mutableStateOf(false) }
    var name by remember(profile?.fullName) { mutableStateOf(profile?.fullName ?: "") }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio ?: "") }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone ?: "") }
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

    // Cover banner gradient: emerald into the avatar colour.
    val coverBrush = remember(profile?.username) {
        Brush.linearGradient(listOf(Emerald, colorFor(profile?.username)))
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // Cover banner with the photo overlapping its bottom edge.
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Box(modifier = Modifier.fillMaxSize().background(coverBrush))
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 48.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Avatar(profile = profile, size = AvatarSize.LG)
                IconButton(
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(36.dp)) {
                            drawCircle(color = Emerald)
                        }
                        if (uploading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(56.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                profile?.fullName?.ifBlank { profile?.username } ?: "…",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text
            )
            Spacer(Modifier.height(2.dp))
            // Username, tap to copy.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString("@${profile?.username ?: ""}"))
                }
            ) {
                Text("@${profile?.username ?: ""}", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy username", tint = colors.muted, modifier = Modifier.size(14.dp))
            }
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
            if (user?.email != null) {
                Spacer(Modifier.height(4.dp))
                Text("Signed in as ${user!!.email}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }

            Spacer(Modifier.height(16.dp))
            // Stats: friends, chats, photos sent, voice sent.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("$friendsCount", "Friends")
                Stat("$chatsCount", "Chats")
                Stat("$photosSent", "Photos")
                Stat("$voiceSent", "Voice")
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
                    label = { Text("About") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AboutPresets.forEach { preset ->
                        AssistChip(onClick = { bio = preset }, label = { Text(preset) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
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
                        viewModel.saveProfile(name, bio, phone, isPublic)
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
                // WhatsApp-style info list.
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
                ) {
                    InfoRow(
                        icon = Icons.Default.Info,
                        label = "About",
                        value = profile?.bio?.ifBlank { "—" } ?: "—"
                    )
                    HorizontalDivider(color = colors.border)
                    InfoRow(
                        icon = Icons.Default.Phone,
                        label = "Phone",
                        value = profile?.phone?.ifBlank { "Add phone number" } ?: "Add phone number",
                        onClick = { editing = true }
                    )
                    HorizontalDivider(color = colors.border)
                    InfoRow(
                        icon = Icons.Default.Person,
                        label = "Username",
                        value = "@${profile?.username ?: ""}",
                        onClick = { clipboard.setText(AnnotatedString("@${profile?.username ?: ""}")) }
                    )
                    HorizontalDivider(color = colors.border)
                    InfoRow(
                        icon = Icons.Default.Email,
                        label = "Email",
                        value = user?.email ?: "—"
                    )
                    HorizontalDivider(color = colors.border)
                    InfoRow(
                        icon = Icons.Default.DateRange,
                        label = "Member since",
                        value = memberSince.ifBlank { "—" }
                    )
                }
                Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val colors = pingChatColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
    }
}

/** One WhatsApp-style info row: icon, label, value; tap when onClick is set. */
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
