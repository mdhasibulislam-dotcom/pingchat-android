package com.mdhasibulislam.pingchat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mdhasibulislam.pingchat.MainActivity
import com.mdhasibulislam.pingchat.R

/**
 * Push notifications, straight from Firebase Cloud Messaging (v2.0.0 —
 * no third-party push SDK). The FCM token is saved to users/{uid}.fcmToken by
 * SessionManager on sign-in.
 *
 * Server-side sending needs a Cloud Function (or the Firebase console):
 * send a data message with title, body, type ("message" or "call") and
 * conversationId, and this service shows it as a notification that opens
 * the app. Until a sender exists, this service simply never fires — the
 * app works fine without it.
 */
class PingChatMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // The token is saved on the next sign-in / app start (SessionManager
        // reads FirebaseMessaging.token); nothing to do here.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: ""
        val conversationId = data["conversationId"]
        showNotification(title, body, conversationId)
    }

    private fun showNotification(title: String, body: String, conversationId: String?) {
        val channelId = "pingchat-messages"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Messages & calls",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (conversationId != null) putExtra("conversationId", conversationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
