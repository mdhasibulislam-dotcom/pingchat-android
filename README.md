# PingChat — native Android app (v2.0.0, Firebase)

A real native Android app for PingChat, written in **Kotlin + Jetpack Compose
(Material 3)**. Since v2.0.0 the Android app runs on **Firebase** (Auth +
Firestore + Storage + FCM) — it is a **SEPARATE system from the PingChat web
app**, which stays on Supabase. Same package name, so v2.0.0 installs over
v1.0.5, but **users must register fresh accounts** (Firebase Auth is
separate; old Supabase users, friends and chats do NOT carry over).

- Firebase project: `ping-chat-fcda6`
- Package: `com.mdhasibulislam.pingchat`
- minSdk 24, targetSdk 37, AGP 9.1.1, Gradle 9.3.1, JVM 17

## What works in v2.0.0

- **Login / register with email** (username availability check, forgot
  password) and **Continue with Google** (see below — needs the Google
  provider enabled in the Firebase console first).
- **Chats list** — conversations with the other person, last message,
  Messenger-style time (`10:24 PM` / `Yesterday` / `Mon` / `Nov 16`), unread
  badges, **Chat / Active tabs** under the pill search bar.
- **1-to-1 text messaging** — realtime delivery, replies, reactions (6 emojis),
  copy, delete for me / for everyone, clear chat, long-press menu
  (press and hold a message ~half a second).
- **View-once photo + voice** — photos (gallery only, no video sending) and
  voice messages (max 120s) are deleted from the database + Storage after
  the recipient views/listens once.
- **Read ticks, 3 states** — sent = single tick, delivered = double tick
  (sticky once the other person is online), seen = tick inside a round badge
  (only when they actually look at the chat, not from a backgrounded open
  chat). Ticks sit inside the bubble, bottom-right (WhatsApp style).
- **Typing indicator** — the other person's small (28px) profile photo
  followed by three waving dots, no name. Time labels are Messenger-style
  centred separators (`Today at 10:24 PM`, `Yesterday at …`, date + time at
  day boundaries, plain time after a 20+ minute pause).
- **Friends** — People directory with pill search, friend requests (send /
  accept / decline / cancel), profile preview dialog (private profiles show
  full info only to friends or while a request is pending).
- **Profile** — photo upload to Firebase Storage (`avatars/{uid}/...`), edit
  name / bio / public-private, member-since date, friends & chats counts.
  The top-bar avatar opens Profile; the bottom-nav tab is **Settings**.
- **Settings** — dark theme, public profile switch, ringtone switch,
  notifications info (FCM), change password, log out (log out lives only
  here, like the web app).
- **Real audio / video calls (WebRTC)** — same OpenRelay TURN relay as the
  web app, so calls work between two phones on mobile data. Signaling runs
  over Firestore (`calls/{callId}` + candidates subcollection). 30s
  no-answer → missed, busy → auto-decline, mute / speaker / camera / flip.
  Audio mode shows the big avatar + name + timer; video shows remote
  fullscreen + local preview corner.

## Firebase console setup — do this BEFORE the build will work live

All of this is in the Firebase console for project **ping-chat-fcda6**
(free Spark plan — no card needed):

1. **Authentication → Sign-in method → enable Email/Password.**
   (Optional, for Google sign-in: also enable **Google** — see below.)
2. **Firestore Database → Create database** (production mode, any region —
   e.g. `asia-south1`). Then **Firestore Database → Rules** → paste the
   contents of `firestore.rules` (repo root) → **Publish**.
3. **Storage → Get started** (default bucket
   `ping-chat-fcda6.firebasestorage.app`). Then **Storage → Rules** → paste
   the contents of `storage.rules` (repo root) → **Publish**.
4. That's it — no keys to paste anywhere. `app/google-services.json` is
   already in the repo; it is public client config and is committed
   **on purpose**. Never add a service-account key or any secret here.

## Google sign-in setup

`Continue with Google` needs the Google provider enabled in the Firebase
console — the `oauth_client` list in the current `google-services.json` is
EMPTY, so the button shows a friendly "not turned on yet" message and email
login works fine. To turn it on:

1. Firebase console → **Authentication → Sign-in method → Google → Enable**.
2. Re-download `google-services.json` (Project settings → Your apps →
   Android app → google-services.json) and replace `app/google-services.json`
   in the repo (the new file will contain an `oauth_client` entry).
3. Rebuild. The app reads the Web client ID from the file at runtime
   (`default_web_client_id`); no code change and no client ID pasted anywhere.

## Push notifications (FCM, direct — no OneSignal)

v2.0.0 removed OneSignal entirely. On sign-in the app saves its FCM token
to `users/{uid}.fcmToken`, and `PingChatMessagingService` shows incoming
messages/calls as notifications. **Sending still needs a server side:**
a Cloud Function (or the Firebase console) must watch new messages/calls
and send an FCM data message `{title, body, type, conversationId}` to the
recipient's token. Until that sender exists, the app works fine — it just
never receives a push. (The old `docs/onesignal-android-setup.md` guide is
obsolete for v2.0.0 — do not follow it.)

## Building

Requirements: JDK 17, Android SDK with platform 37 + build-tools 36.0.0.

```bash
export JAVA_HOME=<path to JDK 17>
export ANDROID_HOME=<path to Android SDK>
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push to GitHub also builds the debug APK automatically — see
`.github/workflows/android.yml` (artifact name `pingchat-debug-apk`).
All debug builds share the committed `app/debug.keystore`, so every CI
build is signed with the SAME key and installs as an update over the
previous debug APK. (Debug key only — never use it for a Play Store release.)

## Project layout

```
app/src/main/java/com/mdhasibulislam/pingchat/
  MainActivity.kt          Navigation (auth / main / settings / chat), Google sign-in result
  PingChatApp.kt           Application (Firebase auto-inits from google-services.json)
  data/
    FirebaseConfig.kt      Firebase instances + googleWebClientId() + millis helpers
    Models.kt              Profile, Conversation, ChatMessage, Friendship, …
    TimeFormat.kt          Messenger-style time labels (robust OffsetDateTime parse)
    Prefs.kt               SharedPreferences (theme, ringtone)
    AuthRepository.kt      sign in/up, Google ID-token, profile ensure, FCM token save
    SessionManager.kt      AuthStateListener, presence, online users, messages feed
    ChatRepository.kt      conversations, messages, ticks, typing, view-once, reactions
    FriendsRepository.kt   directory, friend requests, statuses
    ProfileRepository.kt   profile load/save, avatar upload (Firebase Storage)
    CallRepository.kt      calls collection + Firestore signaling (offer/answer/ICE)
    CallModels.kt          call signaling payloads
    CallManager.kt         WebRTC peer connection + call state (one call at a time)
    PingChatMessagingService.kt  FCM push receiver (shows message/call notifications)
  ui/
    theme/                 Colours (emerald #059669), typography, PingChatTheme
    components/            Avatar (initials + online dot), TickIcon, TypingIndicator
    viewmodel/             Auth, Chats, Chat, People, Profile, Call
    screens/               AuthScreens, ChatsScreen, ChatScreen, PeopleScreen,
                           ProfileScreen, SettingsScreen, CallScreen, MainScreen
firestore.rules            Firestore security rules — publish in the console
storage.rules              Storage security rules — publish in the console
docs/
  webapp-spec.md           947-line spec of the web app (v1.0.5-era reference)
  onesignal-android-setup.md  OBSOLETE for v2.0.0 (FCM is direct now)
```

## Not in v2.0.0

- Push **sending** (needs a Cloud Function — see above).
- Block-check on incoming calls (the web app auto-declines blocked callers;
  the native app doesn't check blocks yet).
- Call history in the Calls tab (still a placeholder).
- Release signing / Play Store listing.
- Settings contents beyond theme / privacy / ringtone / password / log out
  (to be specified later — nothing invented).
