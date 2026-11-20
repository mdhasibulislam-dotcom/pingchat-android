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
     * Accent colour (Appearance, v3.2.0). Stored name ("green", "blue",
     * "purple", "orange", "red", "pink", "orange-red"), default green.
     * See Theme.kt accentColorFor().
     */
    var accentColor: String
        get() = sp.getString("accent_color", "green") ?: "green"
        set(value) { sp.edit().putString("accent_color", value).apply() }

    /**
     * Font size scale (Appearance, v3.2.0). 1.0f = Normal, 1.15f = Large,
     * 1.3f = Extra Large. Applied as the fontScale of LocalDensity.
     */
    var fontSize: Float
        get() = sp.getFloat("font_size", 1.0f)
        set(value) { sp.edit().putFloat("font_size", value).apply() }

    /**
     * Message push notifications (Notifications, v3.2.0). Default ON.
     * OFF hides message pushes (PingChatMessagingService display gate);
     * calls are unaffected.
     */
    var messageNotifications: Boolean
        get() = sp.getBoolean("message_notifications", true)
        set(value) { sp.edit().putBoolean("message_notifications", value).apply() }

    /**
     * Call push notifications (Notifications, v3.2.0). Default ON.
     * OFF hides the call push banner only — the in-app ringing UI
     * (CallManager) is unaffected.
     */
    var callNotifications: Boolean
        get() = sp.getBoolean("call_notifications", true)
        set(value) { sp.edit().putBoolean("call_notifications", value).apply() }

    /**
     * Show message content in notifications (Notifications, v3.2.0).
     * Default ON. OFF shows only the sender name + "New message".
     */
    var notificationPreview: Boolean
        get() = sp.getBoolean("notification_preview", true)
        set(value) { sp.edit().putBoolean("notification_preview", value).apply() }

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

    /**
     * Per-chat theme (v3.2.0, worker F): background choice for one
     * conversation, stored locally under its own key. "default" means
     * the app's normal chat background; anything else is a key from
     * ChatThemeOptions in ChatPeerProfileScreen.kt. Read by ChatScreen
     * for its background; written from the peer profile's Chat Theme row.
     */
    fun chatTheme(conversationId: String): String =
        sp.getString("chat_theme_$conversationId", "default") ?: "default"

    fun setChatTheme(conversationId: String, theme: String) {
        sp.edit().putString("chat_theme_$conversationId", theme).apply()
    }
}
