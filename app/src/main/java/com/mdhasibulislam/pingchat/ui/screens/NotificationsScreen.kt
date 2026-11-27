package com.mdhasibulislam.pingchat.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
 * ringtone and notificationPreview. The ringtone toggle controls ONLY
 * the incoming-call ringtone (CallManager reads Prefs.ringtoneEnabled);
 * it does not control message sounds and there is no vibration code
 * in the app, so it is labelled "Call ringtone" — never "Sound &
 * Vibration".
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    prefs: Prefs,
    viewModel: ProfileViewModel
) {
    val colors = pingChatColors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // v3.2.5: the master toggle used to read only the local pref
    // (default ON), so it showed ON while the phone's system settings
    // had notifications OFF — and POST_NOTIFICATIONS was only ever
    // asked once, after sign-in (MainActivity). The toggle now reflects
    // the real system state: pref AND
    // NotificationManagerCompat.areNotificationsEnabled(). The system
    // state is re-checked on every resume, so coming back from the
    // system settings updates the toggle immediately.
    var systemNotificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var pushEnabled by remember {
        mutableStateOf(prefs.notificationsEnabled && systemNotificationsEnabled)
    }
    var pushHint by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotificationsEnabled =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                pushEnabled = prefs.notificationsEnabled && systemNotificationsEnabled
                if (systemNotificationsEnabled) pushHint = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Turning the master toggle ON while the system permission is off
    // actually requests POST_NOTIFICATIONS now (Android 13+). The
    // toggle flips on only when it is granted; on denial it stays OFF
    // and the hint points at the system settings. A permanently denied
    // permission returns denied immediately, so the settings button is
    // the way back in that case too.
    val pushPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        systemNotificationsEnabled =
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (granted) {
            pushEnabled = true
            prefs.notificationsEnabled = true
            prefs.pushPrompted = true
            pushHint = null
            viewModel.setNotificationsEnabled(true)
        } else {
            pushEnabled = false
            prefs.notificationsEnabled = false
            pushHint =
                "Notifications are blocked in your phone's system settings — " +
                    "tap \"Turn on notifications\" below to allow them."
            viewModel.setNotificationsEnabled(false)
        }
    }
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
                    if (enabled) {
                        if (systemNotificationsEnabled) {
                            pushEnabled = true
                            prefs.notificationsEnabled = true
                            pushHint = null
                            // The FCM token is re-saved only when the
                            // system permission is really on (v3.2.5) —
                            // before, the token was re-saved on any toggle
                            // ON while Android still blocked delivery.
                            viewModel.setNotificationsEnabled(true)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Ask for the real permission; the toggle
                            // flips only when it is granted (see
                            // pushPermission above).
                            pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // Below Android 13 there is no runtime
                            // permission: the system settings are the
                            // only place to turn notifications back on.
                            pushHint =
                                "Notifications are blocked in your phone's system settings — " +
                                    "tap \"Turn on notifications\" below to allow them."
                        }
                    } else {
                        pushEnabled = false
                        prefs.notificationsEnabled = false
                        pushHint = null
                        viewModel.setNotificationsEnabled(false)
                    }
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "When someone messages or calls you, a push notification is sent to this phone. " +
                    "If notifications stop arriving, check that Push Notifications above is on.",
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
        if (!systemNotificationsEnabled) {
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
                title = "Call ringtone",
                subtitle = "Play a ringtone for incoming calls.",
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
