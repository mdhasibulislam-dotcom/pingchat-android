# PingChat — native Android app (Phase 1)

A real native Android app for PingChat, written in **Kotlin + Jetpack Compose
(Material 3)**. It talks to the **same Supabase project** as the PingChat web
app, so the same users, friends and chats work on both — no separate backend,
no data migration.

- Supabase project: `https://fonkaiopgphesajcweej.supabase.co`
- Web app: https://mdhasibulislam-dotcom.github.io/pingchat/
- Package: `com.mdhasibulislam.pingchat`
- minSdk 24, targetSdk 34

## What works in Phase 1

- **Login / register with email** (with username availability check, forgot
  password, email confirmation flow) and **Continue with Google** (see below).
- **Chats list** — conversations with the other person, last message,
  Messenger-style time (`10:24 PM` / `Yesterday` / `Mon` / `Nov 16`), unread
  badges.
- **1-to-1 text messaging** — realtime delivery, replies, reactions (6 emojis),
  copy, delete for me / for everyone, clear chat, long-press menu
  (press and hold a message ~half a second, like the web app).
- **Read ticks, 3 states** — sent = single tick, delivered = double tick
  (sticky once the other person is online), seen = tick inside a round badge
  (only when they actually look at the chat, not from a backgrounded open
  chat). Ticks sit inside the bubble, bottom-right (WhatsApp style).
- **Typing indicator** — the other person's small profile photo followed by
  three waving dots, no name. Time labels are Messenger-style centred
  separators (`Today at 10:24 PM`, `Yesterday at …`, date + time at day
  boundaries, plain time after a 20+ minute pause).
- **Friends** — People directory with search, friend requests (send / accept /
  decline / cancel), profile preview dialog (private profiles show full info
  only to friends or while a request is pending).
- **Profile** — photo upload to the `avatars` bucket, edit name / bio /
  public-private, member-since date, friends & chats counts.
- **Settings** — dark theme, public profile switch, ringtone switch,
  change password, log out (log out lives only here, like the web app).
- **OneSignal linking** — the OneSignal Android SDK is integrated with the
  same App ID as the web app (`da3c98f6-d518-41d1-807e-0e8b77c702d6`) and
  `OneSignal.login(supabaseUserId)` runs on sign-in, so the device is linked
  to the user. **Push delivery itself does NOT work yet — see below.**

## What is NOT in Phase 1

- Voice messages (record / playback / view-once) — shown as a placeholder
  ("Playback comes in the next update").
- Audio / video calls — the Calls tab shows a placeholder.
- Push notification **delivery** — see the next section.
- Release signing / Play Store listing.

## Push notifications — read this before expecting a notification

OneSignal on Android delivers notifications through **Firebase Cloud
Messaging (FCM)**. The app links the device to the user, but no notification
can actually arrive until these two things exist:

1. **A Firebase project** with an Android app registered for package
   `com.mdhasibulislam.pingchat`, and its `google-services.json` file added to
   the `app/` module of this project.
2. **The FCM server key** from that Firebase project entered in the OneSignal
   dashboard (Settings → Platforms → Google Android (FCM)) for the same
   OneSignal app (`da3c98f6-d518-41d1-807e-0e8b77c702d6`).

Do **not** invent a `google-services.json` or an FCM key — they have to come
from the real Firebase project. The web app's push works through the web
SDK; the Android app needs the steps above.

## Google sign-in setup

`Continue with Google` needs the OAuth **Web** client ID — the same OAuth
client already configured in Supabase for the web app:

1. Google Cloud Console → APIs & Services → Credentials → open the
   **Web application** OAuth client used for PingChat.
2. Copy its **Client ID**.
3. In `app/src/main/java/com/mdhasibulislam/pingchat/data/SupabaseClient.kt`,
   replace `GOOGLE_WEB_CLIENT_ID = "YOUR_GOOGLE_WEB_CLIENT_ID"` with that
   Client ID.

Until then, the Google button shows a message and **email login works fine**.
The Supabase callback URL
`https://fonkaiopgphesajcweej.supabase.co/auth/v1/callback` must be in that
client's Authorised redirect URIs (same requirement as the web app).

## Building

Requirements: JDK 17, Android SDK with platform 34 + build-tools 34.0.0.

```bash
export JAVA_HOME=<path to JDK 17>
export ANDROID_HOME=<path to Android SDK>
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push to GitHub also builds the debug APK automatically — see
`.github/workflows/android.yml` (artifact name `pingchat-debug-apk`).

Only the **publishable** Supabase key is in this project
(`SupabaseClient.kt`). It is safe in client code because every table is
protected by Row Level Security. **Never** add a service_role key or any
secret here.

## Project layout

```
app/src/main/java/com/mdhasibulislam/pingchat/
  MainActivity.kt          Navigation (auth / main / settings / chat), Google sign-in result
  PingChatApp.kt           Application: OneSignal init
  data/
    SupabaseClient.kt      Supabase client + keys (publishable only) + placeholders
    Models.kt              Profile, Conversation, ChatMessage, Friendship, …
    TimeFormat.kt          Messenger-style time labels
    Prefs.kt               SharedPreferences (theme, ringtone)
    AuthRepository.kt      sign in/up, Google ID-token, profile ensure, OneSignal login
    ChatRepository.kt      conversations, messages, ticks, typing, presence, realtime
    FriendsRepository.kt   directory, friend requests, statuses
    ProfileRepository.kt   profile load/save, avatar upload
    SessionManager.kt      session observer, presence, messages feed, OneSignal login
  ui/
    theme/                 Colours (emerald #059669), typography, PingChatTheme
    components/            Avatar (initials + online dot), TickIcon, TypingIndicator
    viewmodel/             Auth, Chats, Chat, People, Profile
    screens/               AuthScreens, ChatsScreen, ChatScreen, PeopleScreen,
                           ProfileScreen, SettingsScreen, CallsScreen, MainScreen
docs/
  webapp-spec.md           947-line spec of the web app this port is based on
```

## Phase 2 (not started)

Voice messages (record/playback, view-once consume), audio/video calls
(WebRTC), push delivery (FCM: `google-services.json` + FCM key in the
OneSignal dashboard), release signing, Play Store listing.
