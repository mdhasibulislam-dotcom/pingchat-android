package com.mdhasibulislam.pingchat.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

/**
 * Notifications (v3.2.0, NEW — mockup screen 5).
 *
 * Push Notifications is the master toggle (copied from the old
 * SettingsScreen: Prefs.notificationsEnabled + the system-settings
 * hint + "Turn on notifications" button + viewModel token sync).
 * Below it, per-type toggles persisted in Prefs and enforced for real
 * in PingChatMessagingService: messageNotifications, callNotifications,
 * ringtone (Sound & Vibration) and notificationPreview.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    prefs: Prefs,
    viewModel: ProfileViewModel
) {
    val colors = pingChatColors
    val context = LocalContext.current
    var pushEnabled by remember { mutableStateOf(prefs.notificationsEnabled) }
    var pushHint by remember { mutableStateOf<String?>(null) }
    var messageEnabled by remember { mutableStateOf(prefs.messageNotifications) }
    var callEnabled by remember { mutableStateOf(prefs.callNotifications) }
    var ringtone by remember { mutableStateOf(prefs.ringtoneEnabled) }
    var preview by remember { mutableStateOf(prefs.notificationPreview) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text("Notifications", style = MaterialTheme.typography.titleLarge, color = colors.text, fontWeight = FontWeight.Bold)
        }

        // Master push toggle.
        NotifCard {
            NotifToggleRow(
                icon = Icons.Default.Notifications,
                title = "Push Notifications",
                subtitle = "Get notified on this phone when someone messages or calls you.",
                checked = pushEnabled,
                onCheckedChange = { enabled ->
                    pushEnabled = enabled
                    prefs.notificationsEnabled = enabled
                    if (enabled && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        pushHint =
                            "Notifications are blocked in your phone's system settings — " +
                                "tap \"Turn on notifications\" below to allow them."
                    } else {
                        pushHint = null
                    }
                    viewModel.setNotificationsEnabled(enabled)
                }
            )
        }

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
        if (pushHint != null) {
            Text(
                pushHint ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        // If notifications are off (permission denied, or turned off
        // later), offer a shortcut to the system notification settings.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
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

        NotifCard {
            NotifToggleRow(
                icon = Icons.Default.Message,
                title = "Message Notifications",
                subtitle = "New message, reactions, etc.",
                checked = messageEnabled,
                onCheckedChange = {
                    messageEnabled = it
                    prefs.messageNotifications = it
                }
            )
        }
        NotifCard {
            NotifToggleRow(
                icon = Icons.Default.Call,
                title = "Call Notifications",
                subtitle = "Incoming calls.",
                checked = callEnabled,
                onCheckedChange = {
                    callEnabled = it
                    prefs.callNotifications = it
                }
            )
        }
        NotifCard {
            NotifToggleRow(
                icon = Icons.Default.VolumeUp,
                title = "Sound & Vibration",
                subtitle = "Play a sound for incoming calls and messages.",
                checked = ringtone,
                onCheckedChange = {
                    ringtone = it
                    prefs.ringtoneEnabled = it
                }
            )
        }
        NotifCard {
            NotifToggleRow(
                icon = Icons.Default.Visibility,
                title = "Notification Preview",
                subtitle = "Show message content.",
                checked = preview,
                onCheckedChange = {
                    preview = it
                    prefs.notificationPreview = it
                }
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** A white card with rounded 16dp corners (v3.2.0 mockup style). */
@Composable
private fun NotifCard(content: @Composable () -> Unit) {
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

/** Icon + title + subtitle + toggle row. */
@Composable
private fun NotifToggleRow(
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
