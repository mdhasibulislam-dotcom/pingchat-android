package com.mdhasibulislam.pingchat.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.PingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

/** Settings — theme, privacy, ringtone, notifications, change password, log out. */
@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    prefs: Prefs,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val colors = PingChatColors
    var ringtone by remember { mutableStateOf(prefs.ringtoneEnabled) }
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
                if (p != null) viewModel.saveProfile(p.fullName, p.bio ?: "", it)
            }
        )
        SettingsRow(
            title = "Ringtone",
            subtitle = "Play a sound for incoming calls (Phase 2).",
            checked = ringtone,
            onCheckedChange = {
                ringtone = it
                prefs.ringtoneEnabled = it
            }
        )

        Spacer(Modifier.height(20.dp))
        Text("Notifications", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Your device is linked to your account on sign-in (OneSignal). " +
                    "Push delivery needs the Firebase (FCM) setup — google-services.json is not " +
                    "in the app yet. See README.md, “Push notifications”.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text2
            )
        }

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
            "PingChat native (Phase 1) — chats, friends and profiles. Calls and voice messages come next.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted
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
    val colors = PingChatColors
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

/** Calls tab placeholder — audio/video calls are Phase 2. */
@Composable
fun CallsScreen() {
    val colors = PingChatColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(Icons.Default.Phone, contentDescription = null, tint = colors.muted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Audio & video calls", style = MaterialTheme.typography.titleLarge, color = colors.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Calls are coming in the next update. Chats, friends and profiles already work.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
