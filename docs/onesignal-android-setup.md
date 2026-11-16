# PingChat Android — OneSignal push setup (FCM)

Same OneSignal app as the web app. App ID: `da3c98f6-d518-41d1-807e-0e8b77c702d6`
(reused — do NOT create a new OneSignal app). The Supabase trigger
(`database/notify-push.sql` in the web repo) already sends pushes by
Supabase user id, so it keeps working once the Android device subscribes
with the same external user id (the app calls `OneSignal.login(userId)`).

## What Hasibul must do (his Google account — Maya cannot do this for him)

### 1. Firebase project (free)
1. Go to https://console.firebase.google.com → Add project → name it
   `PingChat` → create (Google Analytics off is fine).
2. In the project, Add app → Android. Package name exactly:
   `com.mdhasibulislam.pingchat`. App nickname `PingChat`.
3. Download `google-services.json` and put it in the `app/` folder of this
   repo (next to `app/build.gradle.kts`). Commit it — it is a public client
   config, not a secret.

### 2. FCM server key → OneSignal
1. Firebase Console → Project settings → Cloud Messaging.
2. Under "Cloud Messaging API (Legacy)", copy the **Server key**. If it says
   the API is disabled, enable it from the three-dot menu / Google Cloud
   Console link shown there, then copy the key.
   (OneSignal also supports the newer FCM HTTP v1 API with a service-account
   JSON — the legacy Server key is the simpler path.)
3. OneSignal dashboard → open the PingChat app → Settings → Platforms →
   Google Android (FCM) → paste the Server key → Save.
   (The web/Chrome platform already configured there stays untouched.)

### 3. Test
1. Install the APK on the phone, log in, allow notifications.
2. From another account, send a chat message to this user.
3. Expected: a push arrives with the sender's name. If nothing arrives,
   check OneSignal → Delivery → the notification's status, and confirm
   `google-services.json` is in `app/` and the Server key matches.

## Notes
- No new OneSignal REST key is needed — the Supabase trigger uses the
  existing key stored in `public.onesignal_config`.
- If the app is rebuilt with a different package name, repeat step 1 with
  that package name and re-download `google-services.json`.
