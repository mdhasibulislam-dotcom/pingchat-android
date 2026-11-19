package com.mdhasibulislam.pingchat.ui.screens

/**
 * Privacy Policy text shown in the app (PrivacyPolicyScreen).
 *
 * This must stay identical to PRIVACY_POLICY.md at the repo root — the
 * same policy, in plain text instead of Markdown. If you change one,
 * change the other.
 */
object PrivacyPolicyText {
    const val TEXT = """
PingChat Privacy Policy

Last updated: November 19, 2026
Version 3.1.0

1. What PingChat is

PingChat is a private messaging app. You chat, call, and share photos and voice messages with your friends. This policy explains what information we collect, how long we keep it, and the controls you have over it.

2. Information we collect

We collect only what PingChat needs to work:

- Account and profile: your name, username, bio, profile photo, and your login email address. You sign in with an email address or a username and password, or with Google.
- Messages: the text messages, photos, and voice messages you send and receive.
- Stories: the photos or videos you post as a story.
- Calls: your call history (who you called, when, and how long the call lasted).
- Device information: the devices you are signed in on, so you can see them and sign out remotely. We also store a push notification token for each device so we can send you message and call notifications.
- Blocked users: the list of people you have blocked.

We do not collect your phone number. PingChat never asks for it.

3. Stories disappear after 12 hours

A story lasts 12 hours only. After 12 hours, it is fully deleted from the database — the story itself and its photo or video file. Nothing is kept, and there is no permanent copy.

4. Photos and voice messages are deleted after 7 days

Photos and voice messages you send in a chat are permanently deleted 7 days after they were sent. After that, they cannot be recovered — not by you, not by the other person, and not by us.

5. Auto-Delete Messages

You can turn on Auto-Delete for your messages. Choose one of these:

- Off — messages stay until you delete them.
- 24 hours — messages are deleted 24 hours after they were sent.
- 7 days — messages are deleted 7 days after they were sent.
- 30 days — messages are deleted 30 days after they were sent.

When a message is auto-deleted, it is deleted for everyone in the chat.

6. Who can see your information

You decide who can see your activity:

- Last Seen & Online: choose Everybody, Friends, or Nobody.
- Bio: choose Everybody, Friends, or Nobody.
- Profile photo: your profile photo is public — everybody can see it.

You can change these choices any time in Settings > Privacy & Security.

7. Blocking

If you block someone, they cannot message you, call you, or see your profile details. They are not told that you blocked them. You can unblock them any time from Settings > Privacy & Security > Blocked Users.

8. Who can contact you

You control who can reach you:

- Messages: choose who can send you messages.
- Calls: choose who can call you.
- Voice messages: choose who can send you voice messages.

You can change these in Settings > Privacy & Security.

9. Forwarding

You can control whether your messages can be forwarded by others. If you turn forwarding off, other people cannot forward your messages to someone else.

10. Your devices

Settings > Privacy & Security > Devices shows every device you are signed in on. If you see a device you do not recognise, you can sign it out from there. Signing out a device remotely ends its session at once.

11. Inactive accounts are deleted

If you do not open PingChat for a long time, your account is deleted automatically. You choose the period in Settings > Privacy & Security:

- 3 months, 6 months, 12 months, or 24 months without using the app.

The default is 12 months. When an inactive account is deleted, your profile, messages, photos, voice messages, and stories are deleted too. Deleting your account yourself has the same effect — everything is removed.

12. Where your data is stored

Your data is stored on Supabase, our database and file storage provider. Messages, profiles, and settings are stored in its database; photos, voice messages, and story files are stored in its file storage. Your data is protected by your account password and by Supabase's access rules, so only you and the people you chat with can read it.

13. What we never do

- We show no advertisements in PingChat.
- We do not sell your data to anyone, and we do not share it with third parties for advertising.
- We do not collect your phone number, your contacts, or your location.

14. Changes to this policy

If we change this policy, we will update the date at the top and show the new version in the app. Big changes will be announced in the app before they take effect.

15. Contact us

If you have a question about your privacy or your data, contact us from inside the app: Settings > Privacy Policy, or through the app's Settings screen.
"""
}
