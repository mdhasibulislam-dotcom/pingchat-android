plugins {
    id("com.android.application")
    // FCM ONLY in v3.0.0: the google-services plugin reads
    // app/google-services.json (project ping-chat-fcda6) so FirebaseApp can
    // initialise for push receiving. No other Firebase service is used —
    // the data layer is Supabase (see below).
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mdhasibulislam.pingchat"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mdhasibulislam.pingchat"
        minSdk = 24
        targetSdk = 37
        // v3.0.0: full backend move from Firebase to the NEW Supabase
        // project (tjsalnhtuqqigyhvgpnb). Same package name, so this
        // installs over v2.0.9 — but Supabase Auth is a separate system,
        // so users must register fresh accounts.
        versionCode = 27
        versionName = "3.2.5"
    }

    // All debug builds share the committed app/debug.keystore, so every CI
    // build is signed with the SAME key and installs as an update over the
    // previous debug APK. (Without this, each GitHub runner generates its own
    // debug key and Android refuses to install the new build over the old one.)
    // This is a DEBUG key only — never use it for a Play Store release.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        // v3.2.2: BuildConfig is read for the real version string
        // (SettingsScreen About row/dialog) instead of a hardcoded one.
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // App foreground/background for presence (online / lastSeen).
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    // Task.await() for FirebaseMessaging.token (push token save).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Supabase (Auth + Database + Realtime + Storage) — NEW project
    // tjsalnhtuqqigyhvgpnb ("hasibulialam's Project"). Ktor needs a real
    // HTTP engine on Android: without ktor-client-okhttp the Supabase
    // client crashes at startup ("Failed to find HTTP client engine").
    // Version must match the Ktor version supabase-kt 3.9.0 uses (3.6.0).
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.9.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.9.0")
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.9.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.9.0")

    // Firebase Cloud Messaging ONLY (push receiving). Config comes from
    // app/google-services.json (project ping-chat-fcda6); that file is
    // public client config and is committed on purpose. No Firebase Auth,
    // Firestore or Storage in v3.0.0. The Firebase BoM below only pins
    // the firebase-messaging version — it adds no other Firebase service.
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Google Sign-In (ID token -> Supabase signInWith(IDToken)).
    // Works once the Google provider is enabled in the Supabase console
    // for the new project — the Web client ID lives in SupabaseClient.kt.
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Avatar images.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // QR codes on the public profile (Share profile, v3.1.0). ZXing core
    // is free (Apache 2.0), pure Java, no Android dependency — only the
    // QRCodeWriter is used, rendered into a Bitmap by hand.
    implementation("com.google.zxing:core:3.5.3")

    // WebRTC audio/video calls (kept from v1.0.5).
    // NOTE: org.webrtc:google-webrtc is NO LONGER on Maven Central (Google
    // deprecated the JCenter distribution) — verified 404 on 2026-11-17.
    // The official replacement is Google's resumed distribution at
    // github.com/webrtc-sdk/android, published as io.github.webrtc-sdk:android
    // on Maven Central (POM + AAR verified HTTP 200 for 144.7559.09).
    // Same org.webrtc.* API, so all standard WebRTC Android code works.
    implementation("io.github.webrtc-sdk:android:144.7559.09")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
