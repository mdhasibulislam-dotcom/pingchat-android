# OBSOLETE — do not follow this guide (v2.0.0)

This guide described OneSignal push for the Supabase-based v1.x Android app.
**v2.0.0 removed OneSignal entirely.** The Android app now uses Firebase
Cloud Messaging directly:

- On sign-in the app saves its FCM token to `users/{uid}.fcmToken`
  (`AuthRepository.kt` → `PushHelper`).
- `data/PingChatMessagingService.kt` shows incoming message/call pushes.
- Sending still needs a server side (a Cloud Function watching new messages
  and calls) — see the "Push notifications" section in `README.md`.

No OneSignal app, dashboard key, or FCM server key in OneSignal is needed
anymore. This file is kept only so the old instructions are not mistaken
for current ones.
