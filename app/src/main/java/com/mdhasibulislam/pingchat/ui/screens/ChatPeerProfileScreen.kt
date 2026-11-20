package com.mdhasibulislam.pingchat.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.colorFor
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors

/**
 * Per-chat theme options (v3.2.0, worker F). "default" is the app's
 * normal chat background; the rest are soft solid colours stored per
 * conversation in Prefs (chat_theme_<conversationId>) and applied as
 * ChatScreen's background. The colour is used as-is in both light and
 * dark theme — it is the user's explicit choice for that chat.
 */
data class ChatThemeOption(val key: String, val label: String, val color: Color?)

val ChatThemeOptions = listOf(
    ChatThemeOption("default", "Default", null),
    ChatThemeOption("sage", "Sage", Color(0xFFDDEDE3)),
    ChatThemeOption("sky", "Sky", Color(0xFFDCEBF5)),
    ChatThemeOption("peach", "Peach", Color(0xFFF6E3D3)),
    ChatThemeOption("lavender", "Lavender", Color(0xFFE7E2F2)),
    ChatThemeOption("rose", "Rose", Color(0xFFF5DDE3)),
    ChatThemeOption("sand", "Sand", Color(0xFFF0EAD8))
)

/** Background colour for a stored per-chat theme key (default = [fallback]). */
fun chatThemeColor(key: String, fallback: Color): Color =
    ChatThemeOptions.firstOrNull { it.key == key }?.color ?: fallback

/** Display label for a stored per-chat theme key. */
fun chatThemeLabel(key: String): String =
    ChatThemeOptions.firstOrNull { it.key == key }?.label ?: "Default"

/**
 * Report reasons (web spec §6.6 — the Android app had no report UI
 * before v3.2.0; the reasons, the "Thanks, your report was sent."
 * toast and the pingchat_reports insert match the web app exactly).
 */
private val ReportReasons = listOf(
    "Spam",
    "Harassment",
    "Inappropriate content",
    "Fake account",
    "Other"
)

/**
 * WhatsApp-style contact-info screen for a 1-to-1 chat peer (v3.2.0,
 * worker F; mockups 7433_0_9gjz.png + 7434_1_aa8a.png). Opened from
 * ChatScreen — tapping the peer's name/avatar or the header ⋮.
 *
 * Every row and button does something real; there are no dead controls:
 * - Call / Video Call: the chat's existing call requests with this peer.
 * - Search: closes this screen and opens the chat's in-chat search.
 * - More (⋯): "Create a group with <peer>" only — the ⋮ menu below is
 *   the canonical place for Pin / Mute / Clear / Archive / Share /
 *   Report / Block, so they are NOT duplicated here.
 * - Media, Links & Files / Shared Media: the chat's shared-media view
 *   (photos only — PingChat has no file sending).
 * - Starred Messages: PingChat has pin, not star — the count is the
 *   number of pinned messages in this chat (0 or 1: one pinned message
 *   per chat), and tapping it jumps to that pinned message.
 * - Notifications: the per-chat mute toggle.
 * - Chat Theme: a per-chat background colour (stored in Prefs).
 * - Privacy & Security: the global Privacy & Security screen.
 * - Block User / Delete Chat: the existing block / clear-chat flows.
 *
 * The top-right ⋮ opens the 7-item popup menu from mockup 7434:
 * Pin Chat, Mute Notifications, Clear Chat, Archive Chat, Share
 * Contact, Report Contact, Block User — each wired to the existing
 * feature (see the on* callbacks; labels flip for Pin/Mute/Archive/
 * Block when already set).
 *
 * This screen is for the CHAT peer only. The own profile stays the
 * mockup-1 screen (worker A) and the People tab keeps using
 * PublicProfileScreen — neither is touched here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPeerProfileScreen(
    profile: Profile,
    online: Boolean,
    canSeeLastSeen: Boolean,
    sharedPhotosCount: Int,
    /** Number of pinned messages in this chat: 0 or 1 (one pin per chat). */
    pinnedCount: Int,
    isBlocked: Boolean,
    isMuted: Boolean,
    isPinned: Boolean,
    isArchived: Boolean,
    chatTheme: String,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onSearch: () -> Unit,
    onCreateGroup: () -> Unit,
    onMuteToggle: () -> Unit,
    onClearChat: () -> Unit,
    onSharedMedia: () -> Unit,
    onOpenPinned: () -> Unit,
    onBlockToggle: () -> Unit,
    onDeleteChat: () -> Unit,
    onOpenPrivacySecurity: () -> Unit,
    onPinToggle: () -> Unit,
    onArchiveToggle: () -> Unit,
    onShareContact: () -> Unit,
    /** Called with the chosen reason + optional details when a report is sent. */
    onReportSubmit: (reason: String, details: String?) -> Unit,
    onChatThemeSelected: (String) -> Unit
) {
    val colors = pingChatColors
    val context = LocalContext.current
    val peerName = profile.fullName.ifBlank { profile.username }
    val statusText = profile.statusLine?.takeIf { it.isNotBlank() }
        ?: profile.bio?.takeIf { it.isNotBlank() }
    val bioText = profile.bio?.takeIf { it.isNotBlank() }

    var showTopMenu by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf<String?>(null) }
    var reportDetails by remember { mutableStateOf("") }

    val themeColor = parseThemeColor(profile.themeColor)
    val coverBrush = remember(profile.username, themeColor) {
        Brush.linearGradient(listOf(themeColor ?: Emerald, colorFor(profile.username)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
    ) {
        // ---- Header: blurred cover, back + ⋮, avatar, name, status ----
        Box(modifier = Modifier.fillMaxWidth()) {
            // Cover: the peer's cover photo, blurred, with a scrim fading
            // into the page background; a soft gradient when no cover.
            val coverUrl = profile.coverPhotoUrl
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .blur(24.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(coverBrush)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.10f),
                                colors.bg.copy(alpha = 0.55f),
                                colors.bg
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.6f))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF111B21)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Top-right ⋮ — the 7-item popup menu (mockup 7434).
                    // This is the ONLY place for Pin / Mute / Clear /
                    // Archive / Share / Report / Block.
                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.6f))
                                .clickable { showTopMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color(0xFF111B21)
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isPinned) "Unpin Chat" else "Pin Chat") },
                                leadingIcon = {
                                    Icon(Icons.Default.PushPin, contentDescription = null)
                                },
                                onClick = { showTopMenu = false; onPinToggle() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isMuted) "Unmute Notifications"
                                        else "Mute Notifications"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.NotificationsOff, contentDescription = null)
                                },
                                onClick = { showTopMenu = false; onMuteToggle() }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Chat") },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                },
                                onClick = { showTopMenu = false; onClearChat() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isArchived) "Unarchive Chat" else "Archive Chat"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Archive, contentDescription = null)
                                },
                                onClick = { showTopMenu = false; onArchiveToggle() }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Contact") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                },
                                onClick = { showTopMenu = false; onShareContact() }
                            )
                            DropdownMenuItem(
                                text = { Text("Report Contact") },
                                leadingIcon = {
                                    Icon(Icons.Default.Report, contentDescription = null)
                                },
                                onClick = {
                                    showTopMenu = false
                                    reportReason = null
                                    reportDetails = ""
                                    showReport = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isBlocked) "Unblock User" else "Block User",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { showTopMenu = false; onBlockToggle() }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Avatar with the green online dot — hidden entirely when
                // the peer hides their last seen (canSeeLastSeen = false).
                Avatar(
                    profile = profile,
                    size = AvatarSize.LG,
                    online = if (canSeeLastSeen) online else null
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.isVerified) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Emerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                // "Online" in green — only when the peer is online AND
                // their last-seen visibility allows showing presence.
                if (canSeeLastSeen && online) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Online",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Emerald
                    )
                }
                if (statusText != null) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.primarySoft)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Four round action buttons ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileActionButton(
                    icon = Icons.Default.Phone,
                    label = "Call",
                    iconTint = Emerald,
                    onClick = onAudioCall
                )
                ProfileActionButton(
                    icon = Icons.Default.Videocam,
                    label = "Video Call",
                    iconTint = colors.text,
                    onClick = onVideoCall
                )
                ProfileActionButton(
                    icon = Icons.Default.Search,
                    label = "Search",
                    iconTint = colors.text,
                    onClick = onSearch
                )
                ProfileActionButton(
                    icon = Icons.Default.MoreHoriz,
                    label = "More",
                    iconTint = colors.text,
                    onClick = { showMoreSheet = true }
                )
            }

            // ---- About card ----
            if (bioText != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.text,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = bioText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text
                    )
                }
            }

            // ---- Media / starred card ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
            ) {
                ProfileRow(
                    icon = Icons.Default.Group,
                    label = "Media, Links & Files",
                    value = sharedPhotosCount.toString(),
                    onClick = onSharedMedia
                )
                HorizontalDivider(color = colors.border)
                ProfileRow(
                    icon = Icons.Default.Image,
                    label = "Shared Media",
                    onClick = onSharedMedia
                )
                HorizontalDivider(color = colors.border)
                ProfileRow(
                    icon = Icons.Default.Star,
                    label = "Starred Messages",
                    value = pinnedCount.toString(),
                    onClick = onOpenPinned
                )
            }

            // ---- Notifications / theme / privacy / block card ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    // ON = not muted (matches the chat's mute flag).
                    Switch(
                        checked = !isMuted,
                        onCheckedChange = { onMuteToggle() }
                    )
                }
                HorizontalDivider(color = colors.border)
                ProfileRow(
                    icon = Icons.Default.Palette,
                    label = "Chat Theme",
                    value = chatThemeLabel(chatTheme),
                    onClick = { showThemePicker = true }
                )
                HorizontalDivider(color = colors.border)
                ProfileRow(
                    icon = Icons.Default.Security,
                    label = "Privacy & Security",
                    onClick = onOpenPrivacySecurity
                )
                HorizontalDivider(color = colors.border)
                ProfileRow(
                    icon = Icons.Default.Block,
                    label = if (isBlocked) "Unblock User" else "Block User",
                    labelColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = onBlockToggle
                )
            }

            // ---- Delete Chat ----
            OutlinedButton(
                onClick = onDeleteChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Delete Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ---- More (⋯) sheet: create a group with this peer only. Pin /
    // Mute / Clear / Archive / Share / Report / Block live in the top
    // ⋮ menu and are deliberately NOT duplicated here. ----
    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(
                    onClick = {
                        showMoreSheet = false
                        onCreateGroup()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.GroupAdd,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Create a group with $peerName",
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.text
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ---- Chat Theme picker: Default + soft solid colours (per chat). ----
    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Chat Theme") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ChatThemeOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showThemePicker = false
                                    onChatThemeSelected(option.key)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = chatTheme == option.key,
                                onClick = {
                                    showThemePicker = false
                                    onChatThemeSelected(option.key)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            // Colour swatch (Default shows the app's
                            // normal chat background as "no swatch").
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        option.color ?: colors.chatBg
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemePicker = false }) { Text("Done") }
            }
        )
    }

    // ---- Report Contact dialog (web spec §6.6): 5 reasons + optional
    // details; the insert itself runs in the caller's onReportSubmit. ----
    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("Report $peerName") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose a reason for your report.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text
                    )
                    Spacer(Modifier.height(8.dp))
                    ReportReasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = reason }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = reportReason == reason,
                                onClick = { reportReason = reason }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it.take(500) },
                        label = { Text("Details (optional)") },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val reason = reportReason
                    if (reason == null) {
                        Toast.makeText(
                            context,
                            "Choose a reason for the report.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@TextButton
                    }
                    showReport = false
                    onReportSubmit(reason, reportDetails.trim().ifBlank { null })
                }) { Text("Send report") }
            },
            dismissButton = {
                TextButton(onClick = { showReport = false }) { Text("Cancel") }
            }
        )
    }
}

/** One of the four round action buttons under the profile header. */
@Composable
private fun ProfileActionButton(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.card)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 13.sp,
            color = colors.text
        )
    }
}

/** One tappable row inside a white profile card (icon + label + value + chevron). */
@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    labelColor: Color? = null,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint ?: colors.text,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor ?: colors.text,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(22.dp)
        )
    }
}
