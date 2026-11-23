package com.mdhasibulislam.pingchat.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small app preferences, kept in SharedPreferences (theme, ringtone, push prompt). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pingchat", Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = sp.getBoolean("theme_dark", false)
        set(value) { sp.edit().putBoolean("theme_dark", value).apply() }

    /**
     * Incoming-call ringtone ON/OFF (Notifications, "Call ringtone").
     * Default ON. Read by CallManager only: it gates the incoming-call
     * ringtone and NOTHING else — no message sounds, no vibration
     * (no vibration code exists in the app). Do not label it as
     * "Sound & Vibration" anywhere.
     */
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
     *
     * v3.2.2: backed by Compose MutableState, initialised from
     * SharedPreferences and updated on set — MainActivity reads this
     * inside its PingChatTheme call, so changing the accent in
     * AppearanceScreen recomposes the app theme with NO restart.
     */
    private var accentState by mutableStateOf(sp.getString("accent_color", "green") ?: "green")
    var accentColor: String
        get() = accentState
        set(value) {
            accentState = value
            sp.edit().putString("accent_color", value).apply()
        }

    /**
     * Font size scale (Appearance, v3.2.0). 1.0f = Normal, 1.15f = Large,
     * 1.3f = Extra Large. Applied as the fontScale of LocalDensity.
     *
     * v3.2.2: backed by Compose MutableState like accentColor above, so
     * a font-size change also applies live with NO restart.
     */
    private var fontSizeState by mutableFloatStateOf(sp.getFloat("font_size", 1.0f))
    var fontSize: Float
        get() = fontSizeState
        set(value) {
            fontSizeState = value
            sp.edit().putFloat("font_size", value).apply()
        }

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
     * Private-profile explainer (Edit Profile, v3.2.4). Set once the
     * "Private Profile — What happens when it's turned on?" bottom
     * sheet has been shown, so turning Private Profile on does not nag
     * the user every time — only the first time. Plain boolean flag
     * (no Compose state): EditProfileScreen reads it when the toggle
     * changes and MainActivity never needs to observe it.
     */
    var privateProfileExplainerShown: Boolean
        get() = sp.getBoolean("private_profile_explainer_shown", false)
        set(value) { sp.edit().putBoolean("private_profile_explainer_shown", value).apply() }

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
