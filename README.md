# PingChat — native Android app (v3.0.0, Supabase)

A real native Android app for PingChat, written in **Kotlin + Jetpack Compose
(Material 3)**. Since v3.0.0 the Android app runs on **Supabase** (Auth +
Postgres + Realtime + Storage) in the NEW project `tjsalnhtuqqigyhvgpnb`
("hasibulialam's Project") — it is a **SEPARATE system from the PingChat web
app**, which stays on its own Supabase project. Same package name, so v3.0.0
installs over v2.0.9, but **users must register fresh accounts** (Supabase
Auth is separate; old Firebase users, friends and chats do NOT carry over).

- Supabase project: `tjsalnhtuqqigyhvgpnb` — https://tjsalnhtuqqigyhvgpnb.supabase.co
- Push receiving only: Firebase project `ping-chat-fcda6` (FCM, via
  `app/google-services.json` — no other Firebase service)
- Package: `com.mdhasibulislam.pingchat`
- minSdk 24, targetSdk 37, AGP 9.1.1, Gradle 9.3.1, JVM 17

## What works in v3.0.0

- **Login / register with email OR username** (username availability check,
  forgot password) and **Continue with Google** (Google provider enabled in
  the Supabase console — see below). No phone-number login.
- **Chats list** — conversations with the other person, last message,
  Messenger-style time (`10:24 PM` / `Yesterday` / `Mon` / `Nov 16`), unread
  badges, **Chat / Active tabs** under the pill search bar.
- **1-to-1 text messaging** — realtime delivery, replies, reactions (6 emojis),
  copy, delete for me / for everyone, clear chat, long-press menu
  (press and hold a message ~half a second).
- **View-once photo + voice** — photos (gallery only, no video sending) and
  voice messages (max 120s) are deleted from the database + Storage after
  the recipient views/listens once. Photo/voice work on the free tier —
  Supabase Storage needs no card.
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
- **Profile** — photo upload to Supabase Storage (`avatars/{uid}/...`), edit
  name / bio / public-private, member-since date, friends & chats counts.
  The top-bar avatar opens Profile; the bottom-nav tab is **Settings**.
- **Settings** — dark theme, public profile switch, ringtone switch,
  notifications info (FCM), change password, log out (log out lives only
  here, like the web app).
- **Real audio / video calls (WebRTC)** — same OpenRelay TURN relay as the
  web app, so calls work between two phones on mobile data. Signaling runs
  over Realtime Broadcast (channels `calls:<userId>`: call-offer /
  call-answer / ice-candidate / call-decline / call-end). 30s
  no-answer → missed, busy → auto-decline, mute / speaker / camera / flip.
  Audio mode shows the big avatar + name + timer; video shows remote
  fullscreen + local preview corner.

## Supabase setup — do this BEFORE the build will work live

All of this is in the Supabase dashboard for project
**tjsalnhtuqqigyhvgpnb** ("hasibulialam's Project"):

1. **SQL Editor → New query** → paste `database/schema-supabase-v3.sql`
   (repo) → **Run**. It creates the 9 `pingchat_*` tables, RLS policies,
   RPC functions, triggers, Realtime publication and the 3 Storage buckets
   (`avatars`, `voice-messages`, `chat-photos`). It is idempotent — safe to
   run again. (The project is shared with the MCQ exam app; the
   `pingchat_` prefix keeps the two apart. Never touch the exam app's tables.)
2. **SQL Editor → New query** → paste
   `database/migration-username-login.sql` → **Run**. It adds the
   `pingchat_email_for_username` function that username login needs.
3. **Authentication → Sign In / Providers → Email**: turn **Confirm email
   OFF** so new accounts sign in immediately (like v2.0.9 did).
4. **Authentication → Sign In / Providers → Google → Enable**, with the
   OAuth Web client (client ID + secret) from Google Cloud. Add the
   Supabase callback URL
   `https://tjsalnhtuqqigyhvgpnb.supabase.co/auth/v1/callback` to that
   OAuth client's Authorized redirect URIs. The app's Web client ID is the
   constant `GOOGLE_WEB_CLIENT_ID` in `data/SupabaseClient.kt`.
5. That's it — no keys to paste anywhere. The publishable key in
   `data/SupabaseClient.kt` (and `PushSender.kt`) is public client config
   and is committed **on purpose**. Never add a service_role key or any
   secret here.

## Push notifications (FCM via the send-push Edge Function)

On sign-in the app saves its FCM token to `pingchat_profiles.fcm_token`,
and `PingChatMessagingService` shows incoming messages/calls as
notifications (app logo as the image, Reply from the notification bar,
Open chat). **Sending** is the already-deployed Supabase Edge Function
`send-push` (project `tjsalnhtuqqigyhvgpnb`): the sender's phone calls it
via `PushSender.kt`, and it sends a **data-only** FCM message to the
recipient's token. The Firebase service-account key lives as a Supabase
secret — never in the app. Delivery is FCM (project `ping-chat-fcda6`,
`app/google-services.json` is FCM config only in v3.0.0).

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
  PingChatApp.kt           Application (no setup needed — Supabase client is lazy)
  data/
    SupabaseClient.kt      Supabase client + Google Web client ID + realtime channel cache
    Models.kt              Profile, Conversation, ChatMessage, Friendship, …
    TimeFormat.kt          Messenger-style time labels (robust OffsetDateTime parse)
    Prefs.kt               SharedPreferences (theme, ringtone)
    AuthRepository.kt      sign in/up (email/username/Google), profile ensure, FCM token save
    SessionManager.kt      GoTrue sessionStatus, presence, online users, messages feed
    ChatRepository.kt      conversations, messages, ticks, typing, view-once, reactions
    FriendsRepository.kt   directory, friend requests, statuses
    ProfileRepository.kt   profile load/save, avatar upload (Supabase Storage)
    CallRepository.kt      pingchat_calls table + Realtime Broadcast signaling
    CallModels.kt          call signaling payloads
    CallManager.kt         WebRTC peer connection + call state (one call at a time)
    PushSender.kt          send-push Edge Function call (free push sending)
    PingChatMessagingService.kt  FCM push receiver (shows message/call notifications)
  ui/
    theme/                 Colours (emerald #059669), typography, PingChatTheme
    components/            Avatar (initials + online dot), TickIcon, TypingIndicator
    viewmodel/             Auth, Chats, Chat, People, Profile, Call
    screens/               AuthScreens, ChatsScreen, ChatScreen, PeopleScreen,
                           ProfileScreen, SettingsScreen, CallScreen, MainScreen
database/
  schema-supabase-v3.sql   Tables + RLS + RPCs + Realtime + Storage buckets (run once)
  migration-username-login.sql  pingchat_email_for_username (run once)
docs/
  webapp-spec.md           947-line spec of the web app (v1.0.5-era reference)
  onesignal-android-setup.md  OBSOLETE (OneSignal was removed in v2.0.0)
```

## Not in v3.0.0

- Block-check on incoming calls (the web app auto-declines blocked callers;
  the native app doesn't check blocks yet).
- Call history in the Calls tab (still a placeholder).
- Release signing / Play Store listing.
- Settings contents beyond theme / privacy / ringtone / password / log out
  (to be specified later — nothing invented).

## History

- v1.0.0–v1.0.5 (2026-11-17): Supabase (old project) — replaced by v2.
- v2.0.0–v2.0.9 (2026-11-17/19): Firebase (Auth + Firestore + Storage + FCM).
  Photo/voice were blocked — Firebase Storage needs the Blaze plan (card).
- v3.0.0 (2026-11-19): back to Supabase, on the NEW project
  `tjsalnhtuqqigyhvgpnb`. Storage is free there, so photo/voice work;
  calls moved to Realtime Broadcast (fixes the v2 M3 ICE-candidate bug);
  username login added; phone login deliberately not added.
