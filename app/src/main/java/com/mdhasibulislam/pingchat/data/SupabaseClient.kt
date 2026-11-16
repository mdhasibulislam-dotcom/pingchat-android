package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * The Supabase project shared with the PingChat web app — same database,
 * same users, same chats. Only the PUBLISHABLE (anon) key lives here; it is
 * safe in client code because every table is protected by Row Level Security.
 * NEVER put a secret / service_role key in this project.
 */
object Supabase {
    const val URL = "https://fonkaiopgphesajcweej.supabase.co"
    const val ANON_KEY = "sb_publishable_-NlHyEWiyYOtTaPw8nJNhw_Pv4ak6CU"

    /** OneSignal App ID (public-safe). Used only to link the device to the user. */
    const val ONESIGNAL_APP_ID = "da3c98f6-d518-41d1-807e-0e8b77c702d6"

    /**
     * Google Sign-In needs the OAuth *Web* client ID from Google Cloud Console —
     * the same OAuth client already configured in Supabase for the web app
     * (Google Cloud Console → APIs & Services → Credentials → the Web
     * application client). Replace this placeholder with that client ID
     * before "Continue with Google" will work. Email login works without it.
     */
    const val GOOGLE_WEB_CLIENT_ID = "YOUR_GOOGLE_WEB_CLIENT_ID"

    val client by lazy {
        createSupabaseClient(URL, ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
