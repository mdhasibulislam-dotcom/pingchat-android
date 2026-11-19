package com.mdhasibulislam.pingchat

import android.app.Application

/**
 * Application class (v3.0.0): Supabase needs no Application-level setup
 * (the client is a lazy singleton in data/SupabaseClient.kt). Firebase
 * initialises automatically from app/google-services.json for push
 * receiving only — FCM via PingChatMessagingService
 * (see data/PingChatMessagingService.kt).
 */
class PingChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
