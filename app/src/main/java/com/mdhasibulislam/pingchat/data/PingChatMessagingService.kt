package com.mdhasibulislam.pingchat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mdhasibulislam.pingchat.MainActivity
import com.mdhasibulislam.pingchat.R

/**
 * Push notifications, straight from Firebase Cloud Messaging (v3.0.0 —
 * FCM receive only; the data layer is Supabase). The FCM token is saved
 * to pingchat_profiles.fcm_token by SessionManager on sign-in.
 *
 * Sending goes through the Supabase Edge Function `send-push` (see
 * PushSender.kt): it sends a data message with title, body, type
 * ("message", "call" or "friend_request") and conversationId / senderId,
 * and this service shows it as a notification that opens the app.
 *
 * v2.0.8 style (Hasibul's choice): the app logo as the notification
 * image, a Reply action that answers from the notification bar itself
 * (RemoteInput -> NotificationReplyReceiver), and an Open chat action
 * that opens the chat. No big picture, no sender photo.
 */
class PingChatMessagingService : FirebaseMessagingService() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val EXTRA_FRIEND_REQUEST = "friendRequest"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val KEY_REPLY_TEXT = "reply_text"
        const val ACTION_REPLY = "com.mdhasibulislam.pingchat.REPLY"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // The token is saved on the next sign-in / app start (SessionManager
        // reads FirebaseMessaging.token); nothing to do here.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // v3.0.2: notifications toggle (Settings) — OFF means do not show
        // anything at all. The fcm_token is also cleared while OFF (see
        // ProfileViewModel.setNotificationsEnabled), so senders should not
        // push in the first place; this is the second layer, for any push
        // that still arrives. Calls are NOT gated here — CallManager
        // handles incoming calls, not this toggle.
        if (!Prefs(applicationContext).notificationsEnabled) return
        val data = message.data
        // v3.1.0: muted chats send no notification. The muted ids are
        // cached in Prefs (see Prefs.mutedConversationIds) because this
        // service has no signed-in Supabase session to query with.
        // Muting never affects calls — those are handled by CallManager.
        val conversationId = data["conversationId"]
        if (conversationId != null &&
            Prefs(applicationContext).mutedConversationIds.contains(conversationId)
        ) return
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: ""
        val senderId = data["senderId"]
        val type = data["type"] ?: "message"
        showNotification(title, body, conversationId, senderId, type)
    }

    private fun showNotification(
        title: String,
        body: String,
        conversationId: String?,
        senderId: String?,
        type: String
    ) {
        val channelId = "pingchat-messages"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Messages & calls",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        // One notification per conversation (messages/calls), one per sender
        // (friend requests), so a reply dismisses the right one and a new
        // message replaces the old one.
        val notificationId = conversationId?.hashCode()
            ?: senderId?.hashCode()
            ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        // Tap the notification (or the action button) -> open the chat, or
        // the People tab for a friend request (type=friend_request carries
        // no conversationId).
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (type == "friend_request") {
                putExtra(EXTRA_FRIEND_REQUEST, true)
            } else if (conversationId != null) {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            // App logo as the notification image (not the sender's photo).
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .addAction(
                0,
                if (type == "friend_request") "View request" else "Open chat",
                openPendingIntent
            )

        // Reply from the notification bar (messages only — not calls).
        if (type == "message" && conversationId != null) {
            val replyIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
                action = ACTION_REPLY
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            // RemoteInput needs a MUTABLE PendingIntent on Android 12+.
            val replyPendingIntent = PendingIntent.getBroadcast(
                this, notificationId, replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
                .setLabel("Reply")
                .build()
            val replyAction = NotificationCompat.Action.Builder(0, "Reply", replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build()
            builder.addAction(replyAction)
        }

        manager.notify(notificationId, builder.build())
    }
}
