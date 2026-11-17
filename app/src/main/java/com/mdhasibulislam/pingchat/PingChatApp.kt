package com.mdhasibulislam.pingchat

import android.app.Application

/**
 * Application class (v2.0.0): Firebase is initialised automatically from
 * app/google-services.json — no code needed here. Push notifications come
 * straight from Firebase Cloud Messaging via PingChatMessagingService
 * (see data/PingChatMessagingService.kt); push is FCM-only in v2.0.0.
 */
class PingChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
