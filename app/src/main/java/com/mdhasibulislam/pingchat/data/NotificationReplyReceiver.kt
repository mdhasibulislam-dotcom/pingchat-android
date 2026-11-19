package com.mdhasibulislam.pingchat.data

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Inline reply from the notification bar (v2.0.8): the user types a
 * reply in the notification shade, this receiver sends it to Supabase
 * exactly like ChatRepository.sendMessage (same message row + chat-list
 * preview), notifies the peer the same way ChatViewModel does, and
 * dismisses the notification. If sending fails, the notification is
 * left alone so the user can retry from the app.
 *
 * Runs in a cold process too: the Supabase client restores the saved
 * GoTrue session from storage, but that load is asynchronous — so this
 * receiver waits for the session (up to 8s) before sending.
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
                if (!Supabase.awaitSignedIn()) return@launch
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
            val client = Supabase.client
            val me = client.auth.currentUserOrNull()?.id ?: return
            val parts = client.from("pingchat_conversation_participants").select {
                filter { eq("conversation_id", conversationId) }
            }.decodeList<Participant>()
            val peerId = parts.firstOrNull { it.userId != me }?.userId ?: return
            val peer = client.from("pingchat_profiles").select {
                filter { eq("id", peerId) }
            }.decodeSingleOrNull<Profile>()
            val token = peer?.fcmToken ?: return
            val myProfile = client.from("pingchat_profiles").select {
                filter { eq("id", me) }
            }.decodeSingleOrNull<Profile>()
            val myName = myProfile?.fullName?.ifBlank { null }
                ?: myProfile?.username?.ifBlank { null }
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
