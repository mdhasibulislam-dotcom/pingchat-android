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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.colorFor
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * My profile — VIEW ONLY (v3.2.0 mockup style: white cards, rounded 16dp
 * corners, green primary #059669, clean Material icons). Editing moved to
 * [EditProfileScreen], reached via [onEditProfileClick]. Cover photo
 * (existing 'covers' bucket) and avatar can still be changed from here
 * via the camera buttons.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onEditProfileClick: () -> Unit = {}
) {
    val profile by viewModel.profile.collectAsState()
    val user by viewModel.user.collectAsState()
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
    val onlineIds by SessionManager.onlineIds.collectAsState()
    val colors = pingChatColors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Own online state: live presence first, profile flag as fallback.
    val isOnline = profile?.online == true ||
        (user?.uid != null && onlineIds.contains(user!!.uid))

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
        modifier = Modifier.fillMaxSize().background(colors.bg).verticalScroll(rememberScrollState()).padding(bottom = 110.dp) // v3.2.1: last row clears the floating bar
    ) {
        // Top bar with back arrow.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text("Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.text)
        }

        // Cover banner (photo when set, else gradient) with the avatar
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
                // Green camera badge on the avatar: tap = change photo.
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Emerald)
                        .clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(56.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Name + verified badge.
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
            // Green "Online" pill when online.
            if (isOnline) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Emerald).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Online", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                profile?.bio?.ifBlank { "No bio yet." } ?: "No bio yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text
            )
            // One-line status.
            if (!profile?.statusLine.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    profile?.statusLine ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted
                )
            }
            if (memberSince.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Member since $memberSince", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
            if (user?.email != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Signed in as ${user!!.email}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }

            Spacer(Modifier.height(16.dp))
            // Stats: friends, chats, photos sent, voice sent (all real counts).
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
            // Info card.
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
                    onClick = onEditProfileClick
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
            // Who recently viewed my profile (owner-only).
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
                                    com.mdhasibulislam.pingchat.data.TimeFormat.fmtListTime(item.view.viewedAt),
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
                onClick = onEditProfileClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit Profile")
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

/** One info row: icon, label, value; chevron + tap when onClick is set. */
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
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}
