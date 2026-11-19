package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.AutoDeleteTimer
import com.mdhasibulislam.pingchat.data.DeviceInfo
import com.mdhasibulislam.pingchat.data.InactiveDeleteAfter
import com.mdhasibulislam.pingchat.data.MessagesWho
import com.mdhasibulislam.pingchat.data.PrivacyRepository
import com.mdhasibulislam.pingchat.data.PrivacySettings
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.data.Visibility
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import kotlinx.coroutines.launch

/**
 * Privacy & Security (v3.1.0, worker A) — Telegram-style settings.
 * Opened from Settings ("privacy_security" route). All 11 settings:
 *
 * Security: Login Email, Devices, Blocked Users,
 *   Delete my account if away.
 * Privacy: Last Seen & Online, Messages, Calls, Voice Messages,
 *   Forwarded Messages, Bio, Auto-Delete Messages (global default;
 *   the per-chat timer lives in chat settings and is stored on
 *   pingchat_conversations.auto_delete — see PrivacyRepository).
 *
 * Every row shows its current value; tapping opens the options.
 * The "Privacy Policy" row at the bottom opens worker G's screen
 * ("privacy_policy" route).
 */
@Composable
fun PrivacySecurityScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val colors = pingChatColors
    val context = LocalContext.current
    val repo = remember { PrivacyRepository() }
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<PrivacySettings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loginEmail by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var blockedUsers by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var devicesExpanded by remember { mutableStateOf(false) }
    var blockedExpanded by remember { mutableStateOf(false) }
    var optionTarget by remember { mutableStateOf<String?>(null) }
    var emailDialog by remember { mutableStateOf(false) }
    var emailDraft by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    val myDeviceId = remember { PrivacyRepository.deviceId(context) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                settings = repo.loadMySettings()
                loginEmail = authRepo.currentLoginEmail()
                devices = repo.loadDevices()
                blockedUsers = repo.loadBlockedUsers()
            } catch (e: Exception) {
                error = "Could not load your privacy settings. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    /** Save one settings change, then refresh the row values. */
    fun save(patch: suspend () -> PrivacySettings) {
        scope.launch {
            saving = true
            error = null
            try {
                settings = patch()
            } catch (e: Exception) {
                error = "Could not save that setting. Check your connection and try again."
            } finally {
                saving = false
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
                .background(colors.card)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Privacy & Security",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                fontWeight = FontWeight.Bold
            )
        }

        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            }
        } else {
            val s = settings
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
            if (saving) {
                Text("Saving…", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Spacer(Modifier.height(8.dp))
            }
            if (error != null) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }

            // ---------- Security ----------
            Text("Security", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PrivacyRow(
                title = "Login Email",
                subtitle = loginEmail ?: "Not signed in with email",
                onClick = {
                    emailDraft = loginEmail ?: ""
                    emailError = null
                    emailDialog = true
                }
            )
            PrivacyRow(
                title = "Devices",
                subtitle = if (devices.isEmpty()) "No devices found" else "${devices.size} device${if (devices.size == 1) "" else "s"} signed in",
                onClick = { devicesExpanded = !devicesExpanded }
            )
            if (devicesExpanded) {
                if (devices.isEmpty()) {
                    Text(
                        "No devices recorded yet. This phone is recorded the next time you sign in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text2
                    )
                } else {
                    devices.forEach { device ->
                        val isMine = device.deviceId == myDeviceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    device.deviceName.ifBlank { "Unknown device" } +
                                        if (isMine) " (This device)" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.text,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Last seen ${TimeFormat.dayLabel(device.lastSeen)}" +
                                        if (device.revoked) " · Logged out" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted
                                )
                            }
                            if (!isMine && !device.revoked) {
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            repo.logoutDevice(device.deviceId)
                                            devices = repo.loadDevices()
                                        } catch (e: Exception) {
                                            error = "Could not log out that device. Try again."
                                        }
                                    }
                                }) {
                                    Text("Log out", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            PrivacyRow(
                title = "Blocked Users",
                subtitle = if (blockedUsers.isEmpty()) "Nobody blocked" else "${blockedUsers.size} blocked",
                onClick = { blockedExpanded = !blockedExpanded }
            )
            if (blockedExpanded) {
                if (blockedUsers.isEmpty()) {
                    Text(
                        "You have not blocked anyone. Blocked people cannot message you, call you, or see your profile details.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text2
                    )
                } else {
                    blockedUsers.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(profile = profile, size = AvatarSize.SM)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    profile.fullName.ifBlank { profile.username },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.text,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "@${profile.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        repo.unblockUser(profile.id)
                                        blockedUsers = repo.loadBlockedUsers()
                                    } catch (e: Exception) {
                                        error = "Could not unblock. Try again."
                                    }
                                }
                            }) {
                                Text("Unblock")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (s != null) {
                PrivacyRow(
                    title = "Delete my account if away",
                    subtitle = "Delete this account after ${s.inactiveAfter.label} of inactivity.",
                    onClick = { optionTarget = "inactive" }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---------- Privacy ----------
            Text("Privacy", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (s != null) {
                PrivacyRow(
                    title = "Last Seen & Online",
                    subtitle = "Who can see when you were last online: ${s.lastSeen.label}.",
                    onClick = { optionTarget = "last_seen" }
                )
                PrivacyRow(
                    title = "Messages",
                    subtitle = "Who can message you: ${s.messages.label}.",
                    onClick = { optionTarget = "messages" }
                )
                PrivacyRow(
                    title = "Calls",
                    subtitle = "Who can call you: ${s.calls.label}.",
                    onClick = { optionTarget = "calls" }
                )
                PrivacyRow(
                    title = "Voice Messages",
                    subtitle = "Who can send you voice messages: ${s.voice.label}.",
                    onClick = { optionTarget = "voice" }
                )
                PrivacyRow(
                    title = "Forwarded Messages",
                    subtitle = "Who can forward your messages: ${s.forwarded.label}.",
                    onClick = { optionTarget = "forwarded" }
                )
                PrivacyRow(
                    title = "Bio",
                    subtitle = "Who can see your bio: ${s.bio.label}.",
                    onClick = { optionTarget = "bio" }
                )
                PrivacyRow(
                    title = "Auto-Delete Messages",
                    subtitle = "New chats delete messages after: ${s.autoDelete.label}. Change the timer for one chat from its chat settings.",
                    onClick = { optionTarget = "auto_delete" }
                )
            }

            Spacer(Modifier.height(24.dp))
            PrivacyRow(
                title = "Privacy Policy",
                subtitle = "How PingChat handles your data.",
                onClick = onOpenPrivacyPolicy
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Blocked people cannot message you, call you, or see your profile details. They are not told that you blocked them.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
            }
        }
    }

    // ---------- option picker dialog ----------
    val target = optionTarget
    val current = settings
    if (target != null && current != null) {
        val (title, options, selected) = when (target) {
            "last_seen" -> Triple(
                "Last Seen & Online",
                Visibility.entries.map { it.db to it.label },
                current.lastSeenVisibility
            )
            "messages" -> Triple(
                "Who can message you",
                MessagesWho.entries.map { it.db to it.label },
                current.messagesWho
            )
            "calls" -> Triple(
                "Who can call you",
                Visibility.entries.map { it.db to it.label },
                current.callsWho
            )
            "voice" -> Triple(
                "Who can send you voice messages",
                Visibility.entries.map { it.db to it.label },
                current.voiceWho
            )
            "forwarded" -> Triple(
                "Who can forward your messages",
                Visibility.entries.map { it.db to it.label },
                current.forwardedWho
            )
            "bio" -> Triple(
                "Who can see your bio",
                Visibility.entries.map { it.db to it.label },
                current.bioVisibility
            )
            "auto_delete" -> Triple(
                "Auto-delete messages in new chats",
                AutoDeleteTimer.entries.map { it.db to it.label },
                current.autoDeleteDefault
            )
            "inactive" -> Triple(
                "Delete my account if away for",
                InactiveDeleteAfter.entries.map { it.db to it.label },
                current.inactiveDeleteAfter
            )
            else -> Triple("", emptyList(), "")
        }
        if (options.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { optionTarget = null },
                title = { Text(title) },
                text = {
                    Column {
                        options.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        optionTarget = null
                                        when (target) {
                                            "last_seen" -> save { repo.setLastSeenVisibility(Visibility.fromDb(value)) }
                                            "messages" -> save { repo.setMessagesWho(MessagesWho.fromDb(value)) }
                                            "calls" -> save { repo.setCallsWho(Visibility.fromDb(value)) }
                                            "voice" -> save { repo.setVoiceWho(Visibility.fromDb(value)) }
                                            "forwarded" -> save { repo.setForwardedWho(Visibility.fromDb(value)) }
                                            "bio" -> save { repo.setBioVisibility(Visibility.fromDb(value)) }
                                            "auto_delete" -> save { repo.setAutoDeleteDefault(AutoDeleteTimer.fromDb(value)) }
                                            "inactive" -> save { repo.setInactiveDeleteAfter(InactiveDeleteAfter.fromDb(value)) }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = value == selected,
                                    onClick = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { optionTarget = null }) { Text("Cancel") }
                }
            )
        }
    }

    // ---------- change login email dialog ----------
    if (emailDialog) {
        AlertDialog(
            onDismissRequest = { emailDialog = false },
            title = { Text("Login Email") },
            text = {
                Column {
                    Text(
                        "Your sign-in email. A confirmation may be sent to the new address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text2
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emailDraft,
                        onValueChange = { emailDraft = it },
                        label = { Text("New email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (emailError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(emailError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = emailDraft.trim()
                    if ("@" !in next || "." !in next.substringAfter("@")) {
                        emailError = "Enter a valid email address."
                        return@TextButton
                    }
                    scope.launch {
                        try {
                            authRepo.updateLoginEmail(next)
                            loginEmail = next
                            emailDialog = false
                        } catch (e: Exception) {
                            emailError = AuthRepository.friendlyError(e)
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { emailDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/** One tappable settings row: title + current value, chevron on the right. */
@Composable
private fun PrivacyRow(title: String, subtitle: String, onClick: () -> Unit) {
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
