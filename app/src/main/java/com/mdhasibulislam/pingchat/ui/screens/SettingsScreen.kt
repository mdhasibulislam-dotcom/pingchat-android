package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdhasibulislam.pingchat.data.CallHistoryItem
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.CallHistoryViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

/**
 * Settings (v3.2.0 redesign, mockup screen 7) — white cards with
 * rounded 16dp corners, icon + title + subtitle + chevron rows.
 *
 * The old inline toggles moved out: dark theme + accent + font size
 * live on the Appearance screen, push/message/call/preview toggles on
 * the Notifications screen, and change password on its own screen.
 * This screen only navigates to them, plus Help, About and Log Out.
 */
@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    prefs: Prefs,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onPrivacySecurityClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {}
) {
    // darkTheme / onDarkThemeChange / prefs are kept for signature
    // compatibility (another worker wires the appearance route); this
    // screen no longer shows a theme toggle itself.
    val profile by viewModel.profile.collectAsState()
    val colors = pingChatColors
    var languageDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp), // v3.2.1: last row clears the floating bar
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = colors.text)

        // Profile card — taps through to Edit Profile.
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditProfileClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (profile != null) {
                    Avatar(profile = profile!!, size = AvatarSize.MD)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile?.fullName?.ifBlank { "PingChat" } ?: "PingChat",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        fontWeight = FontWeight.SemiBold
                    )
                    val username = profile?.username?.ifBlank { null }
                    if (username != null) {
                        Text("@$username", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            }
        }

        SettingsCard {
            SettingsNavRow(
                icon = Icons.Default.Security,
                title = "Privacy & Security",
                subtitle = "Who can see you, message you or call you.",
                onClick = onPrivacySecurityClick
            )
            HorizontalDivider(color = colors.border)
            SettingsNavRow(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "Push, message, call and preview settings.",
                onClick = onNotificationsClick
            )
            HorizontalDivider(color = colors.border)
            SettingsNavRow(
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = "Theme, accent colour and font size.",
                onClick = onAppearanceClick
            )
            HorizontalDivider(color = colors.border)
            SettingsNavRow(
                icon = Icons.Default.Language,
                title = "Language",
                subtitle = null,
                value = "English",
                onClick = { languageDialog = true }
            )
        }

        SettingsCard {
            SettingsNavRow(
                icon = Icons.Default.HelpOutline,
                title = "Help & Support",
                subtitle = "Get help using PingChat.",
                onClick = { helpDialog = true }
            )
            HorizontalDivider(color = colors.border)
            SettingsNavRow(
                icon = Icons.Default.Info,
                title = "About PingChat",
                subtitle = "Version 3.2.0",
                onClick = { aboutDialog = true }
            )
        }

        OutlinedButton(
            onClick = { viewModel.logout() },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "PingChat native — chats, friends, profiles and calls.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted
        )
    }

    // Language: English is the only language for now — an honest list,
    // not a dead button.
    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text("Language") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = true, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text("English", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "More languages coming soon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) { Text("Done") }
            }
        )
    }

    // Help & Support: short help text + a way through to the Privacy Policy.
    if (helpDialog) {
        AlertDialog(
            onDismissRequest = { helpDialog = false },
            title = { Text("Help & Support") },
            text = {
                Text(
                    "Need help with PingChat?\n\n" +
                        "Chats, friends, profiles and calls all work from the tabs at the " +
                        "bottom. If notifications stop arriving, sign out and back in once.\n\n" +
                        "You can also read how PingChat handles your data in the Privacy Policy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text
                )
            },
            confirmButton = {
                TextButton(onClick = { helpDialog = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        helpDialog = false
                        onPrivacyPolicyClick()
                    }
                ) { Text("Privacy Policy") }
            }
        )
    }

    // About: the real version, 3.2.0.
    if (aboutDialog) {
        AlertDialog(
            onDismissRequest = { aboutDialog = false },
            title = { Text("About PingChat") },
            text = {
                Text(
                    "PingChat\nVersion 3.2.0\n\nConnect • Chat • Share\n\n" +
                        "A simple, private messaging app — chats, friends, profiles and calls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text
                )
            },
            confirmButton = {
                TextButton(onClick = { aboutDialog = false }) { Text("Close") }
            }
        )
    }
}

/** A white card with rounded 16dp corners (v3.2.0 mockup style). */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = pingChatColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

/** One tappable navigation row: icon + title + subtitle + chevron (v3.2.0). */
@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    value: String? = null,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
    }
}

/** Calls tab: my call history, newest first (WhatsApp-style list). A
 *  finished call is recorded for each side separately, so long-pressing
 *  a row deletes that entry for me only — the other side keeps theirs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen() {
    val viewModel: CallHistoryViewModel = viewModel()
    val history by viewModel.history.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val colors = pingChatColors
    var menuItem by remember { mutableStateOf<CallHistoryItem?>(null) }

    // Refresh every time the tab is opened — a call can finish while
    // another tab is showing.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Calls", style = MaterialTheme.typography.headlineMedium, color = colors.text)
                Text("Your recent calls.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (history.isEmpty() && !loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = colors.muted, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text("No calls yet", style = MaterialTheme.typography.titleLarge, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Start a call from any chat — tap the phone or video button at the top.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
                items(history, key = { it.row.id }) { item ->
                    CallHistoryRow(item = item, onLongPress = { menuItem = item })
                }
            }
        }
    }

    // Long-press menu (same bottom-sheet pattern as the chat message menu).
    val deleting = menuItem
    if (deleting != null) {
        ModalBottomSheet(
            onDismissRequest = { menuItem = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                TextButton(
                    onClick = { viewModel.delete(deleting); menuItem = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { menuItem = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** One history row: peer avatar + name, direction/status, date/time, duration. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallHistoryRow(item: CallHistoryItem, onLongPress: () -> Unit) {
    val colors = pingChatColors
    val row = item.row
    // WhatsApp-style: a missed incoming call is red, everywhere in the row.
    val missed = row.status == "missed" && row.direction == "incoming"
    val displayProfile = item.peer ?: Profile(
        id = row.peerId, username = "", fullName = row.peerName ?: "Unknown"
    )
    val kindLabel = if (row.kind == "video") "Video call" else "Voice call"
    val statusLabel = when (row.status) {
        "missed" -> "Missed $kindLabel"
        "declined" -> "Declined $kindLabel"
        "unanswered" -> "No answer"
        else -> "${if (row.direction == "outgoing") "Outgoing" else "Incoming"} $kindLabel"
    }
    val duration = row.durationSeconds
        ?.takeIf { row.status == "ended" }
        ?.let { TimeFormat.fmtDuration(it) }
    val subtitle = buildString {
        append(statusLabel)
        if (duration != null) append(" · $duration")
        append(" · ${TimeFormat.dayLabel(row.createdAt)}")
    }
    val directionIcon = when {
        missed -> Icons.Default.CallMissed
        row.direction == "outgoing" -> Icons.Default.CallMade
        else -> Icons.Default.CallReceived
    }
    val accent = if (missed) MaterialTheme.colorScheme.error else colors.muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile = displayProfile, size = AvatarSize.MD)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayProfile.fullName.ifBlank { displayProfile.username },
                style = MaterialTheme.typography.titleMedium,
                color = if (missed) MaterialTheme.colorScheme.error else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = accent)
        }
        Spacer(Modifier.width(8.dp))
        Icon(directionIcon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }
}
