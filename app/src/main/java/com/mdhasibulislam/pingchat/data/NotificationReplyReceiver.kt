package com.mdhasibulislam.pingchat.data

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Inline reply from the notification bar (v2.0.8): the user types a
 * reply in the notification shade, this receiver sends it to Firestore
 * exactly like ChatRepository.sendMessage (same message doc + chat-list
 * preview), notifies the peer the same way ChatViewModel does, and
 * dismisses the notification. If sending fails, the notification is
 * left alone so the user can retry from the app.
 *
 * Runs in a cold process too: FirebaseApp is auto-initialised from
 * google-services.json, and the saved login restores synchronously.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = results.getCharSequence(PingChatMessagingService.KEY_REPLY_TEXT)
            ?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        val conversationId =
            intent.getStringExtra(PingChatMessagingService.EXTRA_CONVERSATION_ID) ?: return
        val notificationId =
            intent.getIntExtra(PingChatMessagingService.EXTRA_NOTIFICATION_ID, 0)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sent = ChatRepository().sendMessage(conversationId, text)
                if (sent != null) {
                    notifyPeer(conversationId, text)
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager
                    manager.cancel(notificationId)
                }
            } catch (e: Exception) {
                // Leave the notification up; the user can retry from the app.
            } finally {
                pending.finish()
            }
        }
    }

    /** WhatsApp-style push back to the peer (best-effort — the message is already sent). */
    private suspend fun notifyPeer(conversationId: String, text: String) {
        try {
            val db = FirebaseDb.db
            val me = FirebaseDb.auth.currentUser?.uid ?: return
            val conv = db.collection("conversations").document(conversationId).get().await()
            @Suppress("UNCHECKED_CAST")
            val participants = (conv.data?.get("participants") as? List<*>)
                ?.mapNotNull { it as? String } ?: return
            val peerId = participants.firstOrNull { it != me } ?: return
            val peer = db.collection("users").document(peerId).get().await()
            val token = peer.data?.get("fcmToken") as? String ?: return
            val myDoc = db.collection("users").document(me).get().await()
            val myName = (myDoc.data?.get("fullName") as? String)?.ifBlank { null }
                ?: (myDoc.data?.get("username") as? String)?.ifBlank { null }
                ?: "PingChat"
            PushSender.sendPush(
                token, myName, text,
                mapOf("type" to "message", "conversationId" to conversationId)
            )
        } catch (e: Exception) {
            // Best-effort only.
        }
    }
}
