# OBSOLETE — do not follow this guide (v2.0.0 and later)

This guide described OneSignal push for the Supabase-based v1.x Android app.
**v2.0.0 removed OneSignal entirely, and v3.0.0 keeps it removed.** The
Android app now uses Firebase Cloud Messaging directly:

- On sign-in the app saves its FCM token to `pingchat_profiles.fcm_token`
  (`SessionManager.kt` → `saveFcmToken`).
- `data/PingChatMessagingService.kt` shows incoming message/call pushes.
- Sending is the Supabase Edge Function `send-push` (see `PushSender.kt`
  and the "Push notifications" section in `README.md`) — no OneSignal,
  no Cloud Function, no Blaze needed.

No OneSignal app, dashboard key, or FCM server key in OneSignal is needed
anymore. This file is kept only so the old instructions are not mistaken
for current ones.
