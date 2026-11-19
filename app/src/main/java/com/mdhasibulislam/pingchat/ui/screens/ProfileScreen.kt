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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
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
import coil.compose.AsyncImage
import com.mdhasibulislam.pingchat.data.SocialLinks
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

/** Theme colour presets for the public profile header (v3.1.0). */
private val ThemeColorPresets = listOf(
    "Default" to null,
    "Emerald" to "#10B981",
    "Blue" to "#3B82F6",
    "Purple" to "#8B5CF6",
    "Pink" to "#EC4899",
    "Orange" to "#F97316"
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
    val viewCount by viewModel.viewCount.collectAsState()
    val recentViewers by viewModel.recentViewers.collectAsState()
    val uploadingCover by viewModel.uploadingCover.collectAsState()
    val colors = pingChatColors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var editing by remember { mutableStateOf(false) }
    var name by remember(profile?.fullName) { mutableStateOf(profile?.fullName ?: "") }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio ?: "") }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone ?: "") }
    var isPublic by remember(profile?.isPublic) { mutableStateOf(profile?.isPublic ?: true) }
    // v3.1.0 extended fields.
    var city by remember(profile?.city) { mutableStateOf(profile?.city ?: "") }
    var education by remember(profile?.education) { mutableStateOf(profile?.education ?: "") }
    var statusLine by remember(profile?.statusLine) { mutableStateOf(profile?.statusLine ?: "") }
    var facebook by remember(profile?.socialLinks) { mutableStateOf(profile?.socialLinks?.facebook ?: "") }
    var instagram by remember(profile?.socialLinks) { mutableStateOf(profile?.socialLinks?.instagram ?: "") }
    var github by remember(profile?.socialLinks) { mutableStateOf(profile?.socialLinks?.github ?: "") }
    var themeColor by remember(profile?.themeColor) { mutableStateOf(profile?.themeColor ?: "") }

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

    // v3.1.0: cover photo picker — same image handling as the avatar.
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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
        viewModel.uploadCover(bytes, ext, mime)
    }

    val memberSince = remember(profile?.createdAt) {
        val instant = TimeFormat.parse(profile?.createdAt)
        if (instant == null) ""
        else DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
            .format(instant.atZone(java.time.ZoneId.systemDefault()))
    }

    // Cover banner gradient: theme colour (or emerald) into the avatar colour.
    val coverBrush = remember(profile?.username, profile?.themeColor) {
        val theme = parseThemeColor(profile?.themeColor)
        Brush.linearGradient(listOf(theme ?: Emerald, colorFor(profile?.username)))
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // Cover banner (photo when set, else gradient) with the photo
        // overlapping its bottom edge, plus a change-cover button.
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val coverUrl = profile?.coverPhotoUrl
            if (!coverUrl.isNullOrBlank() && coverUrl.startsWith("https://")) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(coverBrush))
            }
            IconButton(
                onClick = { coverPicker.launch("image/*") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                if (uploadingCover) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change cover photo", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
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
            // Name + verified badge (v3.1.0).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile?.fullName?.ifBlank { profile?.username } ?: "…",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text
                )
                if (profile?.isVerified == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF1D9BF0),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
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
            // v3.1.0: one-line status.
            if (!profile?.statusLine.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    profile?.statusLine ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted
                )
            }
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
                // v3.1.0 extended fields.
                OutlinedTextField(
                    value = statusLine,
                    onValueChange = { statusLine = it },
                    label = { Text("Status (one line — what are you doing now?)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = education,
                    onValueChange = { education = it },
                    label = { Text("Education") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = facebook,
                    onValueChange = { facebook = it },
                    label = { Text("Facebook URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = instagram,
                    onValueChange = { instagram = it },
                    label = { Text("Instagram URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = github,
                    onValueChange = { github = it },
                    label = { Text("GitHub URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Theme colour", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeColorPresets.forEach { (label, hex) ->
                        AssistChip(
                            onClick = { themeColor = hex ?: "" },
                            label = { Text(label) },
                            leadingIcon = {
                                if (hex != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(parseThemeColor(hex) ?: Color.Gray)
                                    )
                                }
                            }
                        )
                    }
                }
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
                        viewModel.saveExtendedProfile(
                            city,
                            education,
                            statusLine,
                            SocialLinks(
                                facebook = facebook.trim().ifBlank { null },
                                instagram = instagram.trim().ifBlank { null },
                                github = github.trim().ifBlank { null }
                            ),
                            themeColor
                        )
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
                    // v3.1.0: city, education, visitors.
                    if (!profile?.city.isNullOrBlank()) {
                        HorizontalDivider(color = colors.border)
                        InfoRow(
                            icon = Icons.Default.LocationOn,
                            label = "City",
                            value = profile?.city ?: "—"
                        )
                    }
                    if (!profile?.education.isNullOrBlank()) {
                        HorizontalDivider(color = colors.border)
                        InfoRow(
                            icon = Icons.Default.School,
                            label = "Education",
                            value = profile?.education ?: "—"
                        )
                    }
                    // v3.1.0: social links (tap not needed here — shown on the public profile).
                    (profile?.socialLinks?.nonBlank() ?: emptyList()).forEach { (network, url) ->
                        HorizontalDivider(color = colors.border)
                        InfoRow(
                            icon = Icons.Default.Link,
                            label = network,
                            value = url
                        )
                    }
                    HorizontalDivider(color = colors.border)
                    InfoRow(
                        icon = Icons.Default.Visibility,
                        label = "Profile visitors",
                        value = if (viewCount > 0) "$viewCount" else "No visitors yet"
                    )
                }
                // v3.1.0: who recently viewed my profile (owner-only).
                if (recentViewers.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
                    ) {
                        Text(
                            "Recent visitors",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        recentViewers.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(profile = item.viewer, size = AvatarSize.SM)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.viewer?.fullName?.ifBlank { item.viewer?.username } ?: "PingChat user",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        TimeFormat.fmtListTime(item.view.viewedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted
                                    )
                                }
                            }
                        }
                    }
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
