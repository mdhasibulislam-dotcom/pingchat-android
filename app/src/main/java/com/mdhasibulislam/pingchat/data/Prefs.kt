package com.mdhasibulislam.pingchat.data

import android.content.Context

/** Small app preferences, kept in SharedPreferences (theme, ringtone, push prompt). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pingchat", Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = sp.getBoolean("theme_dark", false)
        set(value) { sp.edit().putBoolean("theme_dark", value).apply() }

    var ringtoneEnabled: Boolean
        get() = sp.getBoolean("ringtone", true)
        set(value) { sp.edit().putBoolean("ringtone", value).apply() }

    /** Set once the push-permission prompt has been shown, so it never nags. */
    var pushPrompted: Boolean
        get() = sp.getBoolean("push_prompted", false)
        set(value) { sp.edit().putBoolean("push_prompted", value).apply() }

    /**
     * Push notifications ON/OFF (Settings, v3.0.2). Default ON. OFF stops
     * notifications two ways: PingChatMessagingService will not display
     * them, and the fcm_token is cleared from the profile so senders find
     * no token to push to. Calls are NOT affected (CallManager, not push).
     */
    var notificationsEnabled: Boolean
        get() = sp.getBoolean("notifications_enabled", true)
        set(value) { sp.edit().putBoolean("notifications_enabled", value).apply() }

    /**
     * Muted conversation ids, v3.1.0 (local mirror of the is_muted flag on
     * the caller's participant row). PingChatMessagingService reads this
     * — not the database — because the service runs outside any signed-in
     * Supabase session and must decide synchronously whether to show a
     * push. ChatsViewModel keeps this in sync whenever mute changes.
     */
    var mutedConversationIds: Set<String>
        get() = sp.getStringSet("muted_conversations", emptySet()) ?: emptySet()
        set(value) { sp.edit().putStringSet("muted_conversations", value).apply() }

    fun setConversationMuted(conversationId: String, muted: Boolean) {
        val current = mutedConversationIds.toMutableSet()
        if (muted) current.add(conversationId) else current.remove(conversationId)
        mutedConversationIds = current
    }
}
