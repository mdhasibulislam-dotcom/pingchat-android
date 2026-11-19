package com.mdhasibulislam.pingchat.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdhasibulislam.pingchat.data.CallHistoryItem
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.CallHistoryViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

/** Settings — theme, privacy, ringtone, notifications, change password, log out. */
@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    prefs: Prefs,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    // v3.1.0 (worker A): Privacy & Security screen ("privacy_security"
    // route) and worker G's Privacy Policy ("privacy_policy" route).
    onPrivacySecurityClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {}
) {
    val profile by viewModel.profile.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val colors = pingChatColors
    val context = LocalContext.current
    var ringtone by remember { mutableStateOf(prefs.ringtoneEnabled) }
    // v3.0.2: push notifications ON/OFF — persisted in Prefs, reflects the
    // saved value whenever Settings is opened.
    var notificationsEnabled by remember { mutableStateOf(prefs.notificationsEnabled) }
    var notificationsHint by remember { mutableStateOf<String?>(null) }
    var isPublic by remember(profile?.isPublic) { mutableStateOf(profile?.isPublic ?: true) }
    var newPassword by remember { mutableStateOf("") }
    var newPassword2 by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = colors.text)
        Spacer(Modifier.height(20.dp))

        SettingsRow(
            title = "Dark theme",
            subtitle = "Use the dark colour scheme.",
            checked = darkTheme,
            onCheckedChange = onDarkThemeChange
        )
        SettingsRow(
            title = "Public profile",
            subtitle = "Anyone can see your full profile. Private profiles show full info only to friends.",
            checked = isPublic,
            onCheckedChange = {
                isPublic = it
                val p = profile
                if (p != null) viewModel.saveProfile(p.fullName, p.bio ?: "", p.phone ?: "", it)
            }
        )
        SettingsRow(
            title = "Ringtone",
            subtitle = "Play a sound for incoming calls.",
            checked = ringtone,
            onCheckedChange = {
                ringtone = it
                prefs.ringtoneEnabled = it
            }
        )

        Spacer(Modifier.height(20.dp))
        Text("Notifications", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        // v3.0.2: real ON/OFF toggle. OFF stops push notifications on this
        // phone (display gate + fcm_token cleared, see ProfileViewModel);
        // ON restores them. Calls are NOT affected — they ring via
        // CallManager, not push notifications.
        SettingsRow(
            title = "Push notifications",
            subtitle = "Get notified on this phone when someone messages or calls you.",
            checked = notificationsEnabled,
            onCheckedChange = { enabled ->
                notificationsEnabled = enabled
                prefs.notificationsEnabled = enabled
                if (enabled && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    notificationsHint =
                        "Notifications are blocked in your phone's system settings — " +
                            "tap \"Turn on notifications\" below to allow them."
                } else {
                    notificationsHint = null
                }
                viewModel.setNotificationsEnabled(enabled)
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "When someone messages or calls you, a push notification is sent to this phone. " +
                    "If notifications stop arriving, sign out and back in once.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text2
            )
        }
        if (notificationsHint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                notificationsHint ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        // v2.0.9: if notifications are off (permission denied, or turned
        // off later), offer a shortcut to the system notification settings.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Turn on notifications")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Privacy & Security", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsNavRow(
            title = "Privacy & Security",
            subtitle = "Who can see you, message you or call you, blocked users, devices.",
            onClick = onPrivacySecurityClick
        )

        Spacer(Modifier.height(24.dp))
        Text("Change password", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPassword2,
            onValueChange = { newPassword2 = it },
            label = { Text("Confirm new password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (newPassword != newPassword2) {
                    passwordError = "Passwords do not match."
                    return@Button
                }
                passwordError = null
                viewModel.changePassword(newPassword)
                newPassword = ""
                newPassword2 = ""
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Update password")
        }

        if (passwordError != null) {
            Spacer(Modifier.height(12.dp))
            Text(passwordError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (info != null) {
            Spacer(Modifier.height(12.dp))
            Text(info ?: "", color = Emerald, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { viewModel.logout() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log out", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "PingChat native — chats, friends, profiles and calls.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted
        )
        // v3.1.0 (worker A): Privacy Policy row at the very bottom of
        // Settings — opens worker G's screen ("privacy_policy" route).
        Spacer(Modifier.height(16.dp))
        SettingsNavRow(
            title = "Privacy Policy",
            subtitle = "How PingChat handles your data.",
            onClick = onPrivacyPolicyClick
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** One tappable navigation row (v3.1.0: Privacy & Security, Privacy Policy). */
@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
