plugins {
    id("com.android.application")
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
        versionCode = 4
        versionName = "1.0.3"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Supabase (Auth + Database + Realtime + Storage) — same project as the web app.
    // Ktor needs a real HTTP engine on Android: without ktor-client-okhttp the
    // Supabase client crashes at startup ("Failed to find HTTP client engine").
    // Version must match the Ktor version supabase-kt 3.9.0 uses (3.6.0).
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.9.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.9.0")
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.9.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.9.0")

    // OneSignal push — App ID only. Delivery needs FCM (google-services.json),
    // see README.md "Push notifications" before expecting pushes to arrive.
    implementation("com.onesignal:OneSignal:5.11.0")

    // Google Sign-In (ID token -> Supabase signInWithIdToken).
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Avatar images.
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
