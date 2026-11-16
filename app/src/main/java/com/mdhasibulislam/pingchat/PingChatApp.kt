package com.mdhasibulislam.pingchat

import android.app.Application
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

/**
 * Application class: initialises the OneSignal Android SDK with the same App ID
 * the PingChat web app uses (da3c98f6-d518-41d1-807e-0e8b77c702d6).
 *
 * IMPORTANT: push delivery still needs Firebase Cloud Messaging (FCM) to be
 * configured in the OneSignal dashboard, plus a google-services.json file in
 * the app module. Until then, OneSignal.login() links the device to the user
 * but no notification can actually arrive. See README.md, “Push notifications”.
 */
class PingChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        OneSignal.initWithContext(this, "da3c98f6-d518-41d1-807e-0e8b77c702d6")
        OneSignal.Debug.logLevel = LogLevel.NONE
    }
}
