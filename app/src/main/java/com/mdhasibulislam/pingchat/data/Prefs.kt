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
}
