package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Privacy & Security (v3.2.0 redesign, mockup screen 3) — white cards
 * with rounded 16dp corners, section headers, icon + title + subtitle
 * rows with a chevron, a value badge, or a green toggle.
 *
 * Sections:
 * - Account Security: Change Password, Two-Factor Authentication
 *   (On/Off badge from the real MFA state), Active Sessions (my
 *   pingchat_devices rows, with remote log-out), Login History (the
 *   same pingchat_devices rows as sign-in records — real last-seen
 *   data only, never fake rows; honest empty state when none exist).
 * - Privacy Settings: Last Seen & Online, Read Receipts, Typing
 *   Indicator, Profile Visibility, Who Can Call You, Who Can Add
 *   You, Message Privacy.
 * - More Settings: the rest of the 11 confirmed v3.1.0 items —
 *   Login Email, Blocked Users, Delete My Account If Away,
 *   Auto-Delete Messages, Forwarded Messages, Voice Messages.
 *   (Messages, Bio and Devices are already in the sections above as
 *   Message Privacy, Profile Visibility and Active Sessions — all 11
 *   settings are reachable, none dropped.)
 * - Privacy Policy + Terms & Conditions rows at the bottom
 *   (v3.2.4), then the green "Your privacy, our priority" card.
 *
 * v3.2.4 Login Email fix (Bug 3): the row always shows the CURRENT
 * confirmed email; a requested new address shows only as "pending
 * confirmation" (from GoTrue's `new_email`, via
 * AuthRepository.currentPendingLoginEmail) with a resend button,
 * until the new address is confirmed. The displayed value is never
 * swapped on save.
 *
 * Read Receipts / Typing Indicator / Who Can Add You persist in the
 * new columns from database/migration-v320-privacy-toggles.sql —
 * that migration MUST be run or saving any setting fails.
 */
@Composable
fun PrivacySecurityScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenTermsConditions: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onTwoFactorClick: () -> Unit = {}
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
    // v3.2.4 (Bug 3 fix): the new address waiting for confirmation, or
    // null when no email change is pending. Read from the server
    // (AuthRepository.currentPendingLoginEmail, GoTrue's `new_email`),
    // never set optimistically — see the Login Email dialog below.
    var pendingLoginEmail by remember { mutableStateOf<String?>(null) }
    var emailInfo by remember { mutableStateOf<String?>(null) }
    var resendCooldown by remember { mutableStateOf(0) }
    var resendBusy by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var blockedUsers by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var mfaEnabled by remember { mutableStateOf(false) }
    var devicesExpanded by remember { mutableStateOf(false) }
    var devicesExpandedMore by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
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
                pendingLoginEmail = authRepo.currentPendingLoginEmail()
                devices = repo.loadDevices()
                blockedUsers = repo.loadBlockedUsers()
                mfaEnabled = authRepo.isMfaEnabled()
            } catch (e: Exception) {
                error = "Could not load your privacy settings. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Resend-confirmation cooldown (same 60s as the signup
    // verification screen — Supabase enforces it server-side too).
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

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
                    .padding(16.dp)
            ) {
                if (saving) {
                    Text("Saving…", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    Spacer(Modifier.height(8.dp))
                }
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }

                // ---------- Account Security ----------
                SectionHeader("Account Security", Icons.Filled.Shield)
                SettingsCard {
                    NavRow(
                        icon = Icons.Filled.Lock,
                        title = "Change Password",
                        subtitle = "Update your password",
                        onClick = onChangePasswordClick
                    )
                    CardDivider()
                    NavRow(
                        icon = Icons.Filled.Shield,
                        title = "Two-Factor Authentication",
                        subtitle = "Add an extra layer of security",
                        badge = if (mfaEnabled) "On" else "Off",
                        badgeOn = mfaEnabled,
                        onClick = onTwoFactorClick
                    )
                    CardDivider()
                    NavRow(
                        icon = Icons.Filled.PhoneAndroid,
                        title = "Active Sessions",
                        subtitle = "Manage your devices",
                        onClick = { devicesExpanded = !devicesExpanded }
                    )
                    if (devicesExpanded) {
                        DevicesList(
                            devices = devices.filter { !it.revoked },
                            myDeviceId = myDeviceId,
                            emptyText = "No other devices are signed in. This phone is recorded the next time you sign in.",
                            onLogoutDevice = { device ->
                                scope.launch {
                                    try {
                                        repo.logoutDevice(device.deviceId)
                                        devices = repo.loadDevices()
                                    } catch (e: Exception) {
                                        error = "Could not log out that device. Try again."
                                    }
                                }
                            }
                        )
                    }
                    CardDivider()
                    NavRow(
                        icon = Icons.Filled.History,
                        title = "Login History",
                        subtitle = "View recent logins",
                        onClick = { historyExpanded = !historyExpanded }
                    )
                    if (historyExpanded) {
                        if (devices.isEmpty()) {
                            Text(
                                "No sign-ins recorded yet. A sign-in is recorded here the next time you sign in on any device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text2,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            devices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            device.deviceName.ifBlank { "Unknown device" } +
                                                if (device.deviceId == myDeviceId) " (This device)" else "",
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
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ---------- Privacy Settings ----------
                SectionHeader("Privacy Settings", Icons.Filled.Lock)
                SettingsCard {
                    if (s != null) {
                        NavRow(
                            icon = Icons.Filled.Visibility,
                            title = "Last Seen & Online",
                            subtitle = "Who can see your last seen",
                            badge = s.lastSeen.label,
                            badgeOn = true,
                            onClick = { optionTarget = "last_seen" }
                        )
                        CardDivider()
                        ToggleRow(
                            icon = Icons.Filled.DoneAll,
                            title = "Read Receipts",
                            subtitle = "Let others know you've read messages",
                            checked = s.readReceipts,
                            onCheckedChange = { save { repo.setReadReceipts(it) } }
                        )
                        CardDivider()
                        ToggleRow(
                            icon = Icons.Filled.Keyboard,
                            title = "Typing Indicator",
                            subtitle = "Let others see when you're typing",
                            checked = s.typingIndicator,
                            onCheckedChange = { save { repo.setTypingIndicator(it) } }
                        )
                        CardDivider()
                        NavRow(
                            icon = Icons.Filled.Person,
                            title = "Profile Visibility",
                            subtitle = "Who can see your profile",
                            badge = s.bio.label,
                            badgeOn = true,
                            onClick = { optionTarget = "bio" }
                        )
                        CardDivider()
                        NavRow(
                            icon = Icons.Filled.Call,
                            title = "Who Can Call You",
                            subtitle = "Allow calls from",
                            badge = s.calls.label,
                            badgeOn = true,
                            onClick = { optionTarget = "calls" }
                        )
                        CardDivider()
                        NavRow(
                            icon = Icons.Filled.PersonAdd,
                            title = "Who Can Add You",
                            subtitle = "Control friend requests",
                            badge = s.add.label,
                            badgeOn = true,
                            onClick = { optionTarget = "add_who" }
                        )
                        CardDivider()
                        NavRow(
                            icon = Icons.Filled.Message,
                            title = "Message Privacy",
                            subtitle = "Control message requests",
                            onClick = { optionTarget = "messages" }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ---------- More Settings (rest of the 11 v3.1.0 items) ----------
                SectionHeader("More Settings", Icons.Filled.Tune)
                SettingsCard {
                    // v3.2.4 (Bug 3 fix): the row ALWAYS shows the CURRENT
                    // confirmed login email. A requested new address is
                    // shown only as "pending confirmation" in the banner
                    // below — never as the login email, so it can never
                    // look like it changed and then "reverted".
                    NavRow(
                        icon = Icons.Filled.Mail,
                        title = "Login Email",
                        subtitle = loginEmail ?: "Not signed in with email",
                        onClick = {
                            // Draft the pending address if one exists, so
                            // the user can correct a typo instead of
                            // starting over.
                            emailDraft = pendingLoginEmail ?: loginEmail ?: ""
                            emailError = null
                            emailInfo = null
                            emailDialog = true
                        }
                    )
                    // Pending-confirmation banner: the new address, a
                    // short note, and a resend button. Shown only while
                    // GoTrue still reports a pending new_email.
                    val pending = pendingLoginEmail?.takeIf { !it.equals(loginEmail, ignoreCase = true) }
                    if (pending != null) {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text(
                                "Pending confirmation: $pending",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Check that inbox and tap the confirmation link. " +
                                    "Until then, your login email stays ${loginEmail ?: "unchanged"}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        resendBusy = true
                                        try {
                                            authRepo.resendEmailChangeConfirmation(pending)
                                            emailInfo = "Confirmation email re-sent to $pending."
                                            resendCooldown = 60
                                        } catch (e: Exception) {
                                            emailInfo = AuthRepository.friendlyError(e)
                                        } finally {
                                            resendBusy = false
                                        }
                                    }
                                },
                                enabled = !resendBusy && resendCooldown == 0
                            ) {
                                Text(
                                    if (resendCooldown > 0) "Resend in ${resendCooldown}s"
                                    else "Resend confirmation email"
                                )
                            }
                        }
                    }
                    if (emailInfo != null) {
                        Text(
                            emailInfo ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text2,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    CardDivider()
                    NavRow(
                        icon = Icons.Filled.Devices,
                        title = "Devices",
                        subtitle = run {
                            val active = devices.count { !it.revoked }
                            if (active == 0) "No devices signed in"
                            else "$active device${if (active == 1) "" else "s"} signed in"
                        },
                        onClick = { devicesExpandedMore = !devicesExpandedMore }
                    )
                    if (devicesExpandedMore) {
                        DevicesList(
                            devices = devices.filter { !it.revoked },
                            myDeviceId = myDeviceId,
                            emptyText = "No other devices are signed in. This phone is recorded the next time you sign in.",
                            onLogoutDevice = { device ->
                                scope.launch {
                                    try {
                                        repo.logoutDevice(device.deviceId)
                                        devices = repo.loadDevices()
                                    } catch (e: Exception) {
                                        error = "Could not log out that device. Try again."
                                    }
                                }
                            }
                        )
                    }
                    CardDivider()
                    NavRow(
                        icon = Icons.Filled.Block,
                        title = "Blocked Users",
                        subtitle = if (blockedUsers.isEmpty()) "Nobody blocked" else "${blockedUsers.size} blocked",
                        onClick = { blockedExpanded = !blockedExpanded }
                    )
                    if (blockedExpanded) {
                        if (blockedUsers.isEmpty()) {
                            Text(
                                "You have not blocked anyone. Blocked people cannot message you, call you, or see your profile details.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text2,
                                modifier = Modifier.padding(vertical = 8.dp)
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
                    }
                    if (s != null) {
                        CardDivider()
                        NavRow(
                            icon = Icons.Filled.DeleteForever,
                            title = "Delete My Account If Away",
                            subtitle = "Delete this account after ${s.inactiveAfter.label} of inactivity.",
                            onClick = { optionTarget = "inactive" }
                        )
                        CardDivider()
                        NavRow(
                            icon = Icons.Filled.AutoDelete,
                            title = "Auto-Delete Messages",
                            subtitle = "New chats delete messages after: ${s.autoDelete.label}.",
                            onClick = { optionTarget = "auto_delete" }
                        )
                        CardDivider()
                        NavRow(
                            icon = Icons.Filled.Message,
                            title = "Forwarded Messages",
                            subtitle = "Who can forward your messages",
                            badge = s.forwarded.label,
                            badgeOn = true,
                            onClick = { optionTarget = "forwarded" }
                        )
                        CardDivider()
                        NavRow(
                            icon = Icons.Filled.Mic,
                            title = "Voice Messages",
                            subtitle = "Who can send you voice messages",
                            badge = s.voice.label,
                            badgeOn = true,
                            onClick = { optionTarget = "voice" }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsCard {
                    NavRow(
                        icon = Icons.Filled.PrivacyTip,
                        title = "Privacy Policy",
                        subtitle = "How PingChat handles your data.",
                        onClick = onOpenPrivacyPolicy
                    )
                    CardDivider()
                    NavRow(
                        icon = Icons.Filled.Description,
                        title = "Terms & Conditions",
                        subtitle = "Read our terms and conditions.",
                        onClick = onOpenTermsConditions
                    )
                }
                Spacer(Modifier.height(16.dp))
                // Green priority card (mockup footer card).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Emerald)
                        .padding(16.dp)
                ) {
                    Text(
                        "Your privacy, our priority",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Control what you share and who can see it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Your privacy is important to us. You're in control of what others can see.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(Modifier.height(32.dp))
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
                "Who can see your profile",
                Visibility.entries.map { it.db to it.label },
                current.bioVisibility
            )
            "add_who" -> Triple(
                "Who can send you a friend request",
                Visibility.entries.map { it.db to it.label },
                current.addWho
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
                                            "add_who" -> save { repo.setAddWho(Visibility.fromDb(value)) }
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
    // v3.2.4 (Bug 3 fix): the old code did `loginEmail = next` on save,
    // so the row showed the NEW address at once — then "reverted" to the
    // old one on the next open, because with "Confirm email" ON the
    // change only applies after the new address is confirmed. Now the
    // displayed login email is NEVER swapped on save: after saving we
    // re-read both values from the server. The row keeps showing the
    // current confirmed email, and the new address appears only as
    // "pending confirmation" (see the banner under the Login Email row)
    // until the confirmation link is tapped.
    if (emailDialog) {
        AlertDialog(
            onDismissRequest = { emailDialog = false },
            title = { Text("Login Email") },
            text = {
                Column {
                    Text(
                        "Your sign-in email is ${loginEmail ?: "not set"}. " +
                            "Enter a new address and we will send a confirmation " +
                            "to it — your login email stays the same until you " +
                            "confirm the new one.",
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
                    if (next.equals(loginEmail, ignoreCase = true)) {
                        emailError = "That is already your login email."
                        return@TextButton
                    }
                    scope.launch {
                        try {
                            authRepo.updateLoginEmail(next)
                            // Re-read from the server — do NOT assign
                            // `next` to loginEmail here.
                            loginEmail = authRepo.currentLoginEmail()
                            pendingLoginEmail = authRepo.currentPendingLoginEmail()
                            emailDialog = false
                            emailInfo = if (pendingLoginEmail != null) {
                                "Confirmation sent to $next. Your login email " +
                                    "stays ${loginEmail ?: "the same"} until you confirm it."
                            } else if (loginEmail.equals(next, ignoreCase = true)) {
                                "Your login email is now $next."
                            } else {
                                "Email change requested for $next — check that inbox for the confirmation link."
                            }
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

/** Section title above a white card ("Account Security", ...), with a small icon. */
@Composable
private fun SectionHeader(text: String, icon: ImageVector) {
    val colors = pingChatColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            fontWeight = FontWeight.Bold
        )
    }
}

/** White card with 16dp rounded corners holding the rows. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = pingChatColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        content()
    }
}

/** Thin divider between rows inside a card. */
@Composable
private fun CardDivider() {
    HorizontalDivider(color = pingChatColors.border)
}

/**
 * One tappable row: icon + title + subtitle, with a value badge
 * and/or a chevron on the right.
 */
@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeOn: Boolean = true,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.text2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (badgeOn) colors.primarySoft else colors.dangerSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (badgeOn) Emerald else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
    }
}

/** One toggle row: icon + title + subtitle, green switch on the right. */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.text2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Emerald,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = colors.border,
                uncheckedThumbColor = Color.White
            )
        )
    }
}

/** Active (non-revoked) devices with a Log out action for other devices. */
@Composable
private fun DevicesList(
    devices: List<DeviceInfo>,
    myDeviceId: String,
    emptyText: String,
    onLogoutDevice: (DeviceInfo) -> Unit
) {
    val colors = pingChatColors
    if (devices.isEmpty()) {
        Text(
            emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text2,
            modifier = Modifier.padding(vertical = 8.dp)
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
                        "Last seen ${TimeFormat.dayLabel(device.lastSeen)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
                if (!isMine) {
                    TextButton(onClick = { onLogoutDevice(device) }) {
                        Text("Log out", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
