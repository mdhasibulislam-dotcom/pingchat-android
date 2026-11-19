// Top-level build file for the PingChat native Android app.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // Firebase: the google-services plugin reads app/google-services.json
    // (project ping-chat-fcda6) — FCM push receiving ONLY in v3.0.0.
    // The data layer is Supabase; no Firebase Auth/Firestore/Storage.
    id("com.google.gms.google-services") version "4.6.0" apply false
}
