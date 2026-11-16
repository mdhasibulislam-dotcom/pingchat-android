# PingChat Web App — Porting Spec for Native Android (Kotlin + Jetpack Compose)

> **Purpose:** a faithful, behaviour-level specification of the PingChat v2 web app, written so another agent can rebuild it as a native Android app (Kotlin + Jetpack Compose) without re-reading the web source. **No Android code is written here — this is study/documentation only.**
>
> **Sources read (all under `~/workspace/pingchat-v2/`):** `index.html` (390 lines), `css/app.css` (584 lines), `js/config.js`, `js/app.js`, `js/auth.js`, `js/chats.js`, `js/calls.js`, `js/friends.js`, `js/people.js`, `js/profile.js`, `js/settings.js`, `js/push.js`, `js/install.js`, `js/call-config.js`, `js/ui.js`, `database/schema.sql` (953 lines), `database/notify-push.sql`, `database/fix-reactions-constraint.sql`, `database/fix-voice-reactions.sql`, `manifest.webmanifest`, `sw.js`, `README.md`.
>
> **Conventions in this spec:** exact identifiers (channel names, RPC names, column names, colours, literal strings) are copied verbatim in `code` formatting. Where the web code is ambiguous or a behaviour could not be determined from source, it is marked **UNCLEAR** rather than guessed.
>
> **Live site / deployment:** GitHub Pages at `https://mdhasibulislam-dotcom.github.io/pingchat/` (base path `/pingchat/`). Root URL opens login when signed out, chats when signed in. **There is deliberately NO landing page.**
>
> **Tech baseline of the web app:** no build step; one `index.html` shell, ES modules, hash routing, Supabase (Auth + Postgres + Realtime + Storage), WebRTC peer-to-peer calls, OneSignal Web SDK v16 for push. Supabase JS v2 loaded from `https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm`.

---

## 0. Source map (web file → Android concern)

| Web file | What it owns |
|---|---|
| `index.html` | All screens/views in one shell; element IDs; auth views; app layout; call overlay; modals; toasts container |
| `css/app.css` | Design tokens, light/dark themes, bubbles, avatars, tab bar, call overlay, menus |
| `js/config.js` | Supabase client init, shared state `PC`, toasts, `friendlyError`, time formatters, avatar/icon helpers |
| `js/app.js` | Bootstrap, hash router, auth guard, presence, theme, service-worker registration |
| `js/auth.js` | Login / register / forgot / reset / Google OAuth / logout / `ensureProfile` |
| `js/chats.js` | Conversation list, messages, typing, read ticks, reactions, replies, deletes, view-once voice |
| `js/calls.js` | WebRTC engine, signalling, call UI, call history, "Call friends" |
| `js/friends.js` | Friend system, blocks, reports, profile-preview modal |
| `js/people.js` | People directory + Requests tab |
| `js/profile.js` | Own profile hero, photo upload, edit form, stats |
| `js/settings.js` | Settings view (theme, privacy, ringtone, push, change password, logout, install) |
| `js/push.js` | OneSignal Web SDK v16 init / login / logout / state / prompt / opt-out |
| `js/install.js` | `beforeinstallprompt` capture (PWA install) — web-only; see §10 |
| `js/call-config.js` | ICE servers (STUN/TURN), `CALL_TIMEOUT_MS` |
| `js/ui.js` | `esc()`, `debounce()`, `formatDuration()`, `showMsg()` |
| `database/schema.sql` | All tables, RLS, RPCs, triggers, storage buckets |
| `database/notify-push.sql` | Server-side push triggers (Postgres + pg_net → OneSignal REST API) |
| `manifest.webmanifest` | PWA manifest (name, icons, colours) |
| `sw.js` | Service worker: app-shell cache `pingchat-v11` + merged OneSignal worker |

---

## 1. App structure: screens, routes, navigation, header

### 1.1 Routing — hash router, single-page app

Routes are hash routes (`location.hash`). `parseHash()` in `js/app.js` strips a leading `#/` and splits on `/`; a hash containing `access_token` (Supabase auth tokens) is treated as empty route. `route()` runs on boot and on every `hashchange`.

| Route | View | Auth required |
|---|---|---|
| `#/login` | Login | No (signed-out) |
| `#/register` | Register | No |
| `#/reset` | Forgot password / new password (two panels, see §2) | No |
| `#/chats` | Chats list | Yes |
| `#/chat/<conversation-uuid>` | Chat window (one conversation) | Yes |
| `#/calls` | Calls | Yes |
| `#/people` | People directory + Requests | Yes |
| `#/profile` | My profile | Yes |
| `#/settings` | Settings | Yes |
| `#/` or empty | Redirect: login if signed out, `#/chats` if signed in | — |
| Unknown route | Redirect to `#/chats` (signed in) or `#/login` (signed out) | — |
| Any `#/login\|register\|reset` while signed in | Redirect to `#/chats` | — |
| Any app route while signed out | Redirect to `#/login` | — |

Special case: if the URL hash contains `type=recovery`, `route()` forces the reset view regardless of session state (see §2.5).

### 1.2 View containers (from `index.html`)

Two top-level roots, exactly one visible at a time:

- `#authRoot` (`.auth-root`) — holds `#view-login`, `#view-register`, `#view-reset`.
- `#appLayout` (`.layout`) — holds a desktop sidebar (`<aside class="sidebar">`) and `<main class="main">` with views `#view-chats`, `#view-chat`, `#view-calls`, `#view-people`, `#view-profile`, `#view-settings`, plus the mobile bottom tab bar (`.tabbar`).

Views are shown/hidden with the `hidden` attribute; `showAppView()` also toggles `document.body.classList` `in-chat` when the route is `chat` (used by CSS to hide the tab bar/top bar in a chat on mobile).

### 1.3 Bottom tabs (mobile) and sidebar tabs (desktop)

Mobile tab bar (`.tabbar`, visible ≤820 px viewport width only) — four buttons, each `data-nav`:

| Tab | `data-nav` | Icon (see §8.5) | Label |
|---|---|---|---|
| Chats | `chats` | `chat` | Chats |
| Calls | `calls` | `phone` | Calls |
| People | `people` | `users` | People |
| Profile | `profile` | `user` | Profile |

Desktop sidebar (`.sidebar`, 330 px wide, hidden ≤820 px) has only three tabs (`Chats`, `Calls`, `People` — no Profile tab; profile is reached via the "me card" at the bottom of the sidebar or the top avatar). The active tab is highlighted: the tab whose `data-nav` equals the current view, or `chats` when the view is `chat`.

**Android mapping:** bottom navigation with 4 destinations (Chats, Calls, People, Profile). Chat detail (`#/chat/<id>`) is a separate destination that hides the bottom nav (web hides `.tabbar` when `body.in-chat`). Desktop sidebar has no direct Android equivalent — do not port it as a separate navigation mode; the mobile layout is the reference for Android.

### 1.4 Header / top bar

- **Mobile top bar** (`.topbar`, 56 px, visible ≤820 px, hidden inside a chat): brand logo + "PingChat" wordmark on the left, a theme-toggle button (`#themeBtnMobile`, sun/moon icon), and the signed-in user's avatar (`#topAvatar`, tapping it goes to `#/profile`).
- **Desktop sidebar header** (`.side-head`): brand + a green pill tag with the literal text `Calls ready`, and a search box (`#convSearch`, placeholder `Search chats`).
- **Chat header** (`.chat-head`): back button (`#backBtn`, → `#/chats`), peer avatar (`#chatAvatar`), peer name (`#chatName`) + status line (`#chatStatus`, `Online` in green or `Offline`), audio-call button (`#audioCallBtn`), video-call button (`#videoCallBtn`). Tapping the name/status area (`#chatWho`) opens the profile preview modal (§6.5).
- **Section headers** (`.view-head`): title + subtitle, e.g. Calls: `Calls` / `Call your friends or check recent calls.`; People: `People` / `Everyone on PingChat. Add friends to start chatting.`; Profile: `My profile` / `How other people see you on PingChat.`; Settings header includes a back button (`#settingsBackBtn` → `#/profile`) + `Settings` / `Appearance, privacy, sound, notifications and security.`; Chats (mobile list): `Chats` / `Your conversations.`
- **Empty states:** Chats list empty: icon + `No conversations yet` + `Find someone in People and send your first message.` Calls "Call friends" empty: `No friends yet` / `Add friends from People to call them here.` Call history empty: `No calls yet` / `Call someone from a chat or from People.` People search no match: `No people found` / `Try a different search.` Requests empty: `No incoming requests` / `When someone sends you a friend request, it will show up here.` and `No outgoing requests` / `Requests you send will show up here until they are accepted.`

### 1.5 Global overlays / modals (all in `index.html`, `hidden` by default)

- `#callOverlay` (`.call-overlay`) — full-screen in-call UI (§3.5 / §5.7).
- `#incomingModal` — incoming-call dialog: avatar, name, `Incoming audio call` / `Incoming video call`, `Decline` / `Accept` buttons.
- `#profileModal` — profile preview (§6.5). Contains `#pvClose` (X), `#pvAvatar`, `#pvName`, `#pvUsername`, `#pvStatus`, `#pvActions`, `#pvDetails`, `#pvNote` (hidden), `#pvExtra`.
- `#reportModal` — report dialog (§6.6): heading `Report <name>`, text `Choose a reason for your report.`, 5 radio reasons, details textarea (`#reportDetails`, maxlength 500, placeholder `Details (optional)`), `Cancel` / `Send report`.
- `#msgMenu` (`.msg-menu`) — floating long-press message menu (§5.6), positioned with JS near the pressed bubble.
- `#toasts` — toast container, bottom-centred (§10.1).

### 1.6 Boot sequence (`js/app.js` `boot()`)

1. `initTheme()` — apply saved theme from `localStorage` key `pingchat-theme` (`light`/`dark`, default `light`); also an inline `<script>` in `<head>` applies it before first paint to avoid a flash.
2. `registerServiceWorker()` — registers `sw.js` (best-effort; failure only logs a warning).
3. `initPush()` — OneSignal init (best-effort, never blocks boot).
4. Static icons injected into shell buttons (search, back, phone, video, send, mic, tab icons).
5. Navigation wiring: all `[data-nav]` buttons set `location.hash`; `#meCard` and `#topAvatar` → `#/profile`; `#backBtn` → `#/chats`.
6. `initAuth()` — bind auth forms (guarded to bind once).
7. `sb.auth.getSession()` — if a session exists, `setupUser(session.user)`; otherwise show login. On failure, toast `Could not reach the server. Check your connection.` (error).
8. `sb.auth.onAuthStateChange`: `SIGNED_IN` → `setupUser()` + route to `#/chats` if on an auth route; `SIGNED_OUT` → clear state, `endCallIfActive()`, route to `#/login`.
9. `window hashchange` → `route()`; initial `route()`.
10. Boot failure fallback: show login view + toast `Something went wrong while starting PingChat. Please reload.` (error).

`setupUser(user)` (called on boot with a restored session AND on `SIGNED_IN` — hence every `init*` is guarded to bind listeners only once):
1. `PC.state.me = user`; `PC.state.profile = await ensureProfile(user)`.
2. `setPushUser(user.id, profile.username)` — link OneSignal (§9).
3. `maybePromptPushOnce()` — one-time push prompt (see §9.3).
4. `renderMeCard()` — sidebar me-card + top avatar.
5. `initPresence(user)` — presence channel (§3.3).
6. `initCalls(user)`, `initChats(user)`, `initPeople(user)` (which also inits friends), `initProfile(user)`, `initSettings(user)`.
7. `refreshConversations()`.

---

## 2. Auth

All auth uses Supabase Auth (`sb.auth`). Session persistence is via the Supabase JS client options (§3.1): `persistSession: true, autoRefreshToken: true, detectSessionInUrl: true`.

### 2.1 Validation rules (shared)

- Email regex: `/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/`
- Username regex: `/^[a-z0-9_]{3,20}$/` (lowercase letters, digits, underscore; 3–20 chars). Hint text under the field: `3–20 characters: letters, numbers, underscore.`
- Password: minimum 8 characters (register, reset, change-password).
- Username availability is checked live (350 ms debounce after typing) via RPC `is_username_available` (§4.3); hint becomes `@<name> is available` (green) or `@<name> is already taken` (red), or `Could not check username right now.` on error. Invalid format hint: `Use 3–20 letters, numbers or underscores.`

### 2.2 Login (`#/login`, `#view-login`)

Fields: `#loginEmail` (placeholder `you@example.com`), `#loginPassword` (placeholder `Your password`), show/hide password toggle (eye / eyeOff icon). Link `Forgot password?` → `#/reset`. Submit button `Log in` (busy label `Logging in…`).

Flow: `sb.auth.signInWithPassword({ email, password })`.
- Invalid email → inline message `Enter a valid email address.`
- Empty password → `Enter your password.`
- Error → `friendlyError()` mapping (§10.1), e.g. `Incorrect email or password.`
- Success → `location.hash = '#/chats'` (the `SIGNED_IN` handler also runs `setupUser`).

Brand block: logo + `PingChat`, heading `Welcome back`, sub `Log in to continue to PingChat.`, divider `or`, Google button `Continue with Google`, footer `New to PingChat? Create an account`, footnote `Audio & video calls, instant messages, live presence.`

### 2.3 Register (`#/register`, `#view-register`)

Fields: `#regName` (Full name, maxlength 60, placeholder `Your full name`), `#regUsername` (maxlength 20, placeholder `your_username`, `autocapitalize="none"`), `#regEmail`, `#regPassword` (placeholder `At least 8 characters`), `#regPassword2` (placeholder `Repeat your password`). Submit `Create account` (busy `Creating account…`).

Flow:
1. Client validation in order: name ≥ 2 chars → `Enter your full name.`; username regex → `Username must be 3–20 letters, numbers or underscores.`; email regex → `Enter a valid email address.`; password ≥ 8 → `Password must be at least 8 characters.`; passwords match → `Passwords do not match.`
2. Re-check availability: `sb.rpc('is_username_available', { p_username: uname })`; if not available → `That username is already taken.`
3. `sb.auth.signUp({ email, password, options: { data: { full_name: name, username: uname }, emailRedirectTo: <base> + '#/login' } })` where `<base>` = `location.origin + location.pathname` with the final path segment stripped (i.e. the app root URL, e.g. `https://mdhasibulislam-dotcom.github.io/pingchat/`).
4. If `r.data.user.identities` is an empty array → `This email is already registered. Try logging in.` (Supabase returns empty identities for an already-registered email).
5. If `r.data.session` exists (email confirmation disabled / auto-confirmed) → go to `#/chats`.
6. Otherwise: form reset + success message `Account created! Check your email and click the confirmation link, then log in.`

Heading: `Create your account`, sub `Simple. Fast. Connected.`, footer `Already have an account? Log in`.

### 2.4 Google OAuth (`Continue with Google`, both login and register views)

`sb.auth.signInWithOAuth({ provider: 'google', options: { redirectTo: 'https://mdhasibulislam-dotcom.github.io/pingchat/' } })`. Busy label: `Redirecting to Google…`. On success the browser is redirected to Google; the return lands on the app root and the client (`detectSessionInUrl: true`) picks up the session, then routes to `#/chats`.

**Android note:** the native equivalent is Supabase `signInWithOAuth` with a deep-link redirect (e.g. a custom scheme registered in the app + added to Supabase Redirect URLs), or native Google Sign-In feeding `signInWithIdToken`. The web redirect URL above is Pages-specific and must NOT be reused verbatim on Android.

**Known live issue (from project memory, not from code):** Google sign-in once failed with `Error 400: redirect_uri_mismatch` because the Supabase callback URL `https://fonkaiopgphesajcweej.supabase.co/auth/v1/callback` was not in the Google Cloud OAuth client's Authorized redirect URIs. The porter should verify the equivalent native redirect configuration rather than assume it works.

### 2.5 Forgot / reset password (`#/reset`, `#view-reset`)

Two panels inside one view:
- `#forgotWrap` (default): heading `Reset your password`, sub `Enter your email and we will send you a reset link.`, field `#forgotEmail`, button `Send reset link` (busy `Sending…`), link `Back to log in`.
  - `sb.auth.resetPasswordForEmail(email, { redirectTo: <base> + '#/login' })` (same `<base>` as §2.3).
  - Success message (always, even if the email does not exist): `If an account exists for that email, a reset link is on its way.` (success style).
- `#newPassWrap` (hidden unless recovery): heading `Choose a new password`, sub `Use at least 8 characters.`, fields `#newPassword`, `#newPassword2`, button `Update password` (busy `Saving…`).
  - Shown when the URL hash contains `type=recovery` (`isRecoveryUrl()` checks `location.hash.indexOf('type=recovery') !== -1`) or the auth event `PASSWORD_RECOVERY` fires. Recovery links land with `#access_token=…&type=recovery`.
  - `sb.auth.updateUser({ password: p1 })`; validation: ≥ 8 chars, both match (same messages as register). Success: `Password updated. Taking you to PingChat…` then navigate to `#/chats` after 900 ms.

### 2.6 Profile creation on sign-up — `ensureProfile(user)` (`js/auth.js`)

The SQL trigger `handle_new_user()` normally creates the profile row (see §4.4); `ensureProfile` is the client fallback:

1. `SELECT * FROM profiles WHERE id = user.id` (`maybeSingle()`).
2. If a row exists: if it has no `avatar_url` and Google metadata has one (`user_metadata.avatar_url` or `user_metadata.picture`), backfill it with an `UPDATE` and return the updated row. Otherwise return the row as-is.
3. If no row: build `full_name` from `meta.full_name || meta.name || <email local part>`; build a username via `usernameForNewUser()` (below); `INSERT INTO profiles (id, username, full_name, avatar_url)` and return the inserted row.
   - On insert error (e.g. username race with the trigger): retry once with a generated username `user_<first 10 hex chars of uuid without dashes>`; if that also fails, re-select the row (the trigger may have created it meanwhile) and return it, or `null`.
4. `usernameForNewUser(user, meta)`: if `meta.username` passes the username regex, use it (lowercased). Otherwise derive from the email prefix: lowercase, replace non-`[a-z0-9_]` with `_`, collapse runs of `_`, trim leading/trailing `_`, truncate to 16 chars. If ≥ 3 chars, check `is_username_available`; if free use it, else try `<base>_<4 random base36 chars>` (≤ 20 chars) if available. Final fallback: `user_<first 10 chars of uuid without dashes>`.
   - Email users always carry `meta.username` (set at sign-up), so this derivation only runs for OAuth users.

**Android note:** replicate this fallback exactly — do not assume the trigger always ran. The Google avatar backfill (`avatar_url`/`picture` from user metadata) matters for Google sign-ins.

### 2.7 Session persistence and logout

- Persistence: Supabase JS client `persistSession: true, autoRefreshToken: true` (§3.1). On Android, use the equivalent Supabase-Kotlin storage (e.g. EncryptedSharedPreferences / DataStore-backed session storage) — do not roll a custom token store.
- Logout (`logout()` in `js/auth.js`): `await sb.auth.signOut()` (errors ignored), `clearPushUser()` (OneSignal logout, §9), clear `PC.state.me` / `PC.state.profile`, navigate to `#/login`.
- **Logout lives ONLY in Settings** (`#settingsLogoutBtn`, `Log out` button in the Account card). There is no logout button anywhere else in the app — including no logout on the Profile view. Port this exactly.
- On `SIGNED_OUT` the app also calls `endCallIfActive()` (tears down any active call locally).

---

## 3. Supabase usage

### 3.1 Project, keys, client init (`js/config.js`)

```js
export const SUPABASE_URL = 'https://fonkaiopgphesajcweej.supabase.co';
export const SUPABASE_KEY = 'sb_publishable_-NlHyEWiyYOtTaPw8nJNhw_Pv4ak6CU';
export const sb = createClient(SUPABASE_URL, SUPABASE_KEY, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
  realtime: { params: { eventsPerSecond: 10 } }
});
```

- Only the **publishable (anon) key** is used. It is safe in client code because every table is protected by RLS. **NEVER put a `service_role` / secret key in the app.**
- Supabase JS version: v2 (`@supabase/supabase-js@2` via jsDelivr ESM CDN). Android porter: use the Supabase-Kotlin client against the same project URL and anon key.
- Shared client-side state object `PC` (`js/config.js`): `PC.state = { me, profile, online: Set<userId>, activeConversationId }`, plus `PC.presenceListeners` with `onPresenceChange(fn)` / `emitPresenceChange()` callbacks (re-render conversation list, people, chat header, ticks when the online set changes).

### 3.2 Direct table access patterns (PostgREST)

The app queries tables directly (no Edge Functions). Recurring select column lists (copy these — do not `SELECT *` in the port except where the web code does):

- Profiles (directory / peers / requests): `id, username, full_name, bio, avatar_url, is_public, created_at` (sometimes without `bio`/`is_public`, e.g. call history: `id, username, full_name, avatar_url`).
- Own profile: `select('*')` in `ensureProfile` only.
- Conversations list: `conversation_participants` → `select('conversation_id, last_read_at').eq('user_id', me.id)`; then `conversation_participants` → `select('conversation_id, user_id, last_read_at').in('conversation_id', convIds)`; `conversations` → `select('id, created_at, last_message_at').in('id', convIds)`; per conversation: latest message `select('id, conversation_id, sender_id, body, created_at, kind').eq('conversation_id', cid).order('created_at', {ascending:false}).limit(1)` and unread count `select('id', {count:'exact', head:true}).eq('conversation_id', cid).neq('sender_id', me.id)` plus `.gt('created_at', readAt)` when `last_read_at` exists.
- Messages in a chat: `select('id, conversation_id, sender_id, body, created_at, reply_to_id, kind, media_url, media_path, view_once').eq('conversation_id', id).order('created_at', {ascending:true}).limit(200)`.
- Reply targets: `select('id, sender_id, body, kind').in('id', missingReplyIds)` / `.eq('id', id).maybeSingle()`.
- Reactions for loaded messages: `select('message_id, user_id, emoji').in('message_id', ids)`.
- Calls history: `select('id, conversation_id, caller_id, callee_id, kind, status, created_at, started_at, ended_at, duration_seconds').or('caller_id.eq.<me>,callee_id.eq.<me>').order('created_at', {ascending:false}).limit(50)`.
- People directory: `select('id, username, full_name, bio, avatar_url, is_public, created_at').neq('id', me.id).order('created_at', {ascending:false}).limit(200)`.
- Friendships: `select('requester_id, addressee_id, status').or('requester_id.eq.<me>,addressee_id.eq.<me>')`; requests: same + `.eq('status','pending')` and `select('requester_id, addressee_id, status, created_at')`.
- Blocks: `select('blocker_id, blocked_id').or('blocker_id.eq.<me>,blocked_id.eq.<me>')`.
- Profile stats: friendships count `select('id', {count:'exact', head:true}).or(...).eq('status','accepted')`; chats count `select('conversation_id', {count:'exact', head:true}).eq('user_id', me.id)`.

### 3.3 Realtime channels — exact names, events, payload handling

All channels are created with `sb.channel(<name>)`. **Channel names are part of the protocol — copy them exactly.**

| Channel name | Created in | Subscriptions | Purpose / handling |
|---|---|---|---|
| `online` | `initPresence()` (`js/app.js`) | `presence` event `sync`; `config: { presence: { key: user.id } }` | **Presence (who is online).** On `sync`, `presenceState()` keys become `PC.state.online` (a `Set` of user ids); `emitPresenceChange()` re-renders lists/header/ticks. On `SUBSCRIBED`, `track({ user_id: user.id, name: profile?.full_name \|\| '' })`. Channel is unsubscribed and re-created on re-login. |
| `messages-feed` | `initChats()` (`js/chats.js`), created once | `postgres_changes` `INSERT` on `public.messages`; `UPDATE` on `public.messages`; `DELETE` on `public.messages` | **Global message feed.** INSERT: if `payload.new.conversation_id === activeId`, append the message (scroll to bottom); always `refreshConversations()`. UPDATE: if the row's `conversation_id` is the active chat AND `deleted_by` includes my id, remove that message element; always `refreshConversations()`. DELETE: mark quotes of that id as deleted, remove the message element; always `refreshConversations()`. |
| `reactions-feed` | `initChats()`, once | `postgres_changes` `INSERT` / `DELETE` on `public.message_reactions` | Live reactions: INSERT → upsert into `reactionsByMsg` (replace same-user row) + re-render chips; DELETE → remove that user's reaction + re-render. (No UPDATE subscription — reaction changes arrive as upsert via the client, see §5.5.) |
| `room:<conversationId>` | `subscribeRoom(id)` on opening a chat; unsubscribed on `closeChat()` | `postgres_changes` `UPDATE` on `public.conversation_participants` with `filter: 'conversation_id=eq.<id>'` | **Peer's read position.** If `payload.new.user_id !== me.id`, set `otherLastReadAt = payload.new.last_read_at` and re-render message ticks (§5.4). |
| `typing:<conversationId>` | `subscribeRoom(id)` | `broadcast` event `typing`; `broadcast` event `typing-stop` | Typing indicator (§5.5). Payloads: `{ user_id, name }` / `{ user_id }`. Events from my own id are ignored. |
| `calls:<userId>` (own id) | `initCalls()` (`js/calls.js`), once | `broadcast` events `call-offer`, `call-answer`, `ice-candidate`, `call-decline`, `call-end`, `call-cancel` | **Incoming call signalling** (my inbox). Handlers: `onOffer`, `onAnswer`, `onCandidate`, `onDecline`, `onRemoteEnd`, `onCancel` (§3.5). |
| `calls:<peerId>` (per peer, cached in `peerChannels` Map) | `peerChannel(peerId)` on demand | (sender side; no `.on` handlers — only `.send()`) | **Outgoing signalling.** See the subscribe-then-send rule below. |
| `friendships-feed` | `initFriends()` (`js/friends.js`), once | `postgres_changes` event `*` on `public.friendships` | On any change: `loadFriendships()` then notify all `onFriendshipsChange` listeners (People re-renders / reloads). |

**Critical signalling rule (`js/calls.js` `peerChannel`):** `await ch.subscribe()` in supabase-js v2 does NOT wait for `SUBSCRIBED` — it resolves immediately, so the first signals sent on a fresh channel were silently lost. The web code therefore waits for the `SUBSCRIBED` status (10 s timeout; `CHANNEL_ERROR`/`TIMED_OUT`/`CLOSED` reject), caches the channel only after success, and never caches a failed channel. `sendSignal()` retries once on a fresh channel if the first attempt fails (dropping the cached channel first), and only then toasts `Could not reach the other side. Check your connection and try again.`

**Realtime publication (SQL):** `supabase_realtime` publication includes `messages`, `calls`, `conversation_participants`, `friendships`, `message_reactions` (added idempotently in `schema.sql` §5/§7/§9). Presence and Broadcast do not need publication entries.

### 3.4 Presence usage

- One presence channel (`online`, key = my user id). The online set is `Object.keys(presenceState())`.
- Used for: avatar online dot (§8.4), chat header `Online`/`Offline` (§1.4), `· Online` suffix in People/Calls lists, and the **delivered** tick state (§5.4).
- Presence changes re-render: conversation lists, chat header, message ticks, People grid (via `onPresenceChange` listeners registered in `app.js`, `chats.js`, `people.js`).
- **UNCLEAR from code:** presence heartbeat / timeout behaviour is Supabase-default; no explicit heartbeat config exists in the app.

### 3.5 Calls — signalling protocol and WebRTC (`js/calls.js`, `js/call-config.js`)

Transport: Supabase Realtime **Broadcast** on per-user channels `calls:<userId>` (NOT postgres_changes; call rows are only the history record). Media: peer-to-peer WebRTC (`RTCPeerConnection`).

**Signalling events (broadcast `event` names — copy exactly):**

| Event | Sender → receiver | Payload fields |
|---|---|---|
| `call-offer` | Caller → callee's `calls:<calleeId>` channel | `{ callId, conversationId, from: { id, name }, kind, sdp }` (`sdp` = `RTCSessionDescription`, the offer) |
| `call-answer` | Callee → caller's channel | `{ callId, sdp }` (answer) |
| `ice-candidate` | Either → other's channel | `{ callId, candidate }` (`candidate` = `RTCIceCandidate.toJSON()`) |
| `call-decline` | Callee (or busy auto-decline) → caller | `{ callId }` |
| `call-end` | Either → other (only if the call was answered/active) | `{ callId }` |
| `call-cancel` | Either → other (if not yet answered) | `{ callId }` |

**Outgoing call flow (`startCall(conversationId, peer, kind)`):**
1. Refuse if already in a call → toast `You are already in a call.`; refuse if no peer → `Cannot call this person right now.`; block check via `checkBlockDirection(peer.id)` (§6.6): `i_blocked` → `You blocked this user.`, `blocked_me` → `You cannot call this user.`
2. Generate `callId` (`crypto.randomUUID()` or fallback `call-<ts>-<random>`). Insert a `calls` row: `{ id: callId, conversation_id, caller_id: me.id, callee_id: peer.id, kind, status: 'ringing' }`. Insert failure → toast `Could not start call: <message>`.
3. `getUserMedia({ audio: true, video: kind === 'video' ? { width: {ideal:1280}, height: {ideal:720} } : false })`. Failure → toast `Microphone/camera unavailable: <message>` and mark the call row `status: 'ended', ended_at: now`.
4. Create `RTCPeerConnection({ iceServers: ICE_SERVERS })`, add local tracks, `createOffer()` + `setLocalDescription`, show the call overlay with status `Calling…`, show local video only for video calls.
5. `sendSignal(peer.id, 'call-offer', {...})`.
6. 30 s timeout (`CALL_TIMEOUT_MS = 30000`): if still `calling`, update row to `status: 'missed'`, end locally, toast `No answer — call marked as missed.` (error), refresh history.

**Incoming call flow (`onOffer`):**
1. Ignore if `!payload || !payload.callId`. If already in a call → auto-decline: send `call-decline` to the caller (busy).
2. Block check: if `checkBlockDirection(payload.from.id) !== 'none'` → auto-decline silently + update call row to `status: 'declined'`.
3. Otherwise set `current` (direction `incoming`, status `ringing`, store the offer SDP), resolve the caller's profile (`SELECT id, username, full_name, avatar_url WHERE id = from.id`) for avatar/name, show `#incomingModal` (`Incoming audio call` / `Incoming video call`), start the ringtone (§3.6).
4. Same 30 s timeout: if still ringing → stop ringtone, hide modal, update row to `missed`, clear `current`, refresh history.

**Accept (`acceptCall`):** clear timeout, stop ringtone, hide modal; `getUserMedia` (same constraints as outgoing; failure → toast, send `call-decline`, update row `declined`, clear `current`); create peer connection, add tracks, `setRemoteDescription(offer)`, `createAnswer()`, `setLocalDescription`; status `active`, `startedAt = now`; send `call-answer`; update row `{ status: 'accepted', started_at: now }`; flush queued ICE candidates; show overlay `Connected`; start the call timer.
**Decline (`declineCall`):** clear timeout, stop ringtone, hide modal, send `call-decline`, update row `declined`, clear `current`, refresh history.
**`onAnswer`:** only if `current.callId === payload.callId` and status is `calling`; clear timeout, `setRemoteDescription(answer)`, status `active`, `startedAt`, flush queued candidates, overlay `Connected`, start timer. Failure → toast `Call failed: <message>` + local end.
**`onCandidate`:** only for the current call id; if `pc.remoteDescription` exists, `addIceCandidate`, else queue in `pendingCandidates` (flushed on answer/accept).
**`onDecline`:** clear timeout, update row `declined`, local end, toast `Call declined.` (error), refresh history.
**`onRemoteEnd`:** local end, toast `Call ended.`, refresh history.
**`onCancel`:** clear timeout, stop ringtone, hide incoming modal, clear `current`, toast `Call cancelled.`
**Hang up (`hangUp`):** if answered → send `call-end`, else send `call-cancel`; then update row `{ status: 'ended', ended_at: now, duration_seconds: <seconds since startedAt if answered> }`; local teardown; refresh history.
**`beforeunload` (web-only):** if closing the tab while `status === 'calling'`, send `call-cancel` and mark the row `missed`. (Android equivalent: handle app/process kill best-effort; do not rely on it.)
**`endCallLocal()`:** clear timeout/timer, close the peer connection, stop local tracks, stop ringtone, hide overlay + incoming modal, null out remote/local video/audio sources.

**Peer connection details (`makePeerConnection`):**
- `onicecandidate` → send `ice-candidate` for each non-null candidate.
- `ontrack`: collect incoming tracks into one persistent `MediaStream` (`current.remoteStream`) — do NOT trust `e.streams[0]` (tracks arrive in separate events; some browsers fire `ontrack` with no streams). **Audio always goes through a dedicated `<audio>` element (`#remoteAudio`)**, not the `<video>` element — a hidden video element outputs no audio in Chrome, which was the original "calls connect but no audio" bug. Remote video (`#remoteVideo`) is `muted` on purpose (audio comes from the audio element, so video must not double it; muted autoplay is never blocked). For audio calls the video stays hidden and a fallback avatar view shows; for video calls the fallback hides and a status bar (`#callStatusBar`, peer name + `#callTimer`) shows. If remote media arrived before the overlay was shown, `showCallOverlay()` re-attaches and re-plays the stored remote stream.
- `onconnectionstatechange`: `disconnected` → toast once `Connection lost — trying to reconnect…` (do not hang up; ICE may recover); `failed` → toast `Connection failed. The other side may be unreachable (a TURN server may be needed).` (error) + `hangUp()`.

**ICE servers (`js/call-config.js` — copy exactly):**

```js
export const ICE_SERVERS = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
  { urls: 'stun:stun2.l.google.com:19302' },
  { urls: 'stun:stun3.l.google.com:19302' },
  { urls: 'stun:stun4.l.google.com:19302' },
  { urls: 'stun:openrelay.metered.ca:80' },
  { urls: 'turn:openrelay.metered.ca:80', username: 'openrelayproject', credential: 'openrelayproject' },
  { urls: 'turn:openrelay.metered.ca:80?transport=tcp', username: 'openrelayproject', credential: 'openrelayproject' },
  { urls: 'turn:openrelay.metered.ca:443', username: 'openrelayproject', credential: 'openrelayproject' },
  { urls: 'turns:openrelay.metered.ca:443?transport=tcp', username: 'openrelayproject', credential: 'openrelayproject' }
];
export const CALL_TIMEOUT_MS = 30000;
```

Note the comment in the file: OpenRelay is a free shared TURN service (best-effort, may be slow at peak); TURN credentials can be abused, so keep them short-lived and rotate if leaked. The user may later replace TURN with Metered.ca free tier / Twilio / Cloudflare Calls / self-hosted coturn.

**In-call UI (`#callOverlay`):** dark background `#0B1220`; remote video fullscreen (`object-fit: cover`); local video picture-in-picture bottom-right (120×170 px desktop, 92×130 px mobile; 14 px radius; 2 px white border); fallback view: peer avatar 110 px + name (22 px) + status text; controls bottom-centred: mute (58 px circle, `rgba(255,255,255,.18)`), camera (same; hidden for audio calls), end (66 px, `#DC2626`). Mute toggles the local audio track's `enabled` (icon `mic` ↔ `micOff`, button gets `.off` = white background); camera toggles the local video track (icon `video` ↔ `videoOff`). Timer format `mm:ss` (§10.3) shown in both the status line and `#callTimer`.

**Call history (`renderCallHistory`, Calls view "Recent calls"):**
- Query §3.2; rows sorted newest first, limit 50.
- Row: status icon (phone/video icon in a 40 px circle, `primary-soft` background; missed → `danger-soft` background + `phoneMissed` icon), peer avatar, peer name, subtitle: `(Outgoing|Incoming) (audio|video) call — <Status> [· mm:ss] · <timeAgo>`, `Call back` button (re-opens/creates the conversation via `getOrCreateConversation` then `startCall` with the same kind).
- Status labels: `ringing → Ringing`, `accepted → Accepted`, `declined → Declined`, `missed → Missed`, `ended → Ended`.
- **Stale-ringing rule:** a `ringing` row older than 60 s (`STALE_RINGING_MS = 60000`) is displayed as `missed` (caller closed the tab / network dropped / timeout never fired).
- Duration shown only when `duration_seconds` is set, formatted `mm:ss`.

**"Call friends" section (`renderCallFriends`):** union of (a) accepted-friend profiles (after refreshing the friendship cache via `loadFriendships()`) and (b) people with an existing conversation with me (via `conversation_participants`); missing profiles are fetched; blocked users (`isBlockedEither`) are excluded; sorted alphabetically by `full_name || username`. Each row: avatar, name, `@username [· Online]`, audio-call + video-call buttons. Call buttons first `getOrCreateConversation(peerId)` then `startCall(convId, peer, kind)`.

### 3.6 Ringtone

No audio file. WebAudio oscillator: sine wave, 880 Hz, gain pulses 0.05 ↔ 0.0001 every 450 ms. Respects the Settings ringtone toggle (`localStorage` key `pingchat-ringtone`, `off` = disabled; default on). Started on incoming call, stopped on accept/decline/timeout/cancel/end. **Android:** reproduce with a generated tone (or a system ringtone) gated by the same setting; there is no audio asset to port.

---

## 4. Data model

All tables are in schema `public`. `auth.users` is Supabase Auth's table (referenced by FK, not defined here).

### 4.1 Tables

#### `profiles`
| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK, FK `auth.users(id)` `ON DELETE CASCADE` | = auth user id |
| `username` | `text` NOT NULL UNIQUE | lowercase, regex `^[a-z0-9_]{3,20}$` enforced in app + trigger |
| `full_name` | `text` NOT NULL DEFAULT `''` | |
| `bio` | `text` (nullable) | max 120 chars in the edit form |
| `avatar_url` | `text` (nullable) | public https URL (Supabase Storage public URL or Google photo URL) |
| `is_public` | `boolean` NOT NULL DEFAULT `true` | profile visibility (added via `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`) |
| `created_at` | `timestamptz` NOT NULL DEFAULT `now()` | |
| `updated_at` | `timestamptz` NOT NULL DEFAULT `now()` | kept fresh by trigger `profiles_updated_at` → `set_updated_at()` |

#### `conversations`
| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK DEFAULT `gen_random_uuid()` | |
| `created_at` | `timestamptz` NOT NULL DEFAULT `now()` | |
| `last_message_at` | `timestamptz` (nullable) | kept fresh by trigger `messages_last_message_at` → `on_message_created()` on every message INSERT. Legacy v1 column `pair_key` may exist on the live DB (nullable since migration). |

Only 1-to-1 conversations exist in the app (enforced by `get_or_create_conversation` requiring exactly 2 participants).

#### `conversation_participants`
| Column | Type | Notes |
|---|---|---|
| `conversation_id` | `uuid` NOT NULL, FK `conversations(id)` `ON DELETE CASCADE` | part of composite PK |
| `user_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | part of composite PK |
| `last_read_at` | `timestamptz` (nullable) | **drives read receipts (§5.4)**; updated by `markRead()` |
| PK | `(conversation_id, user_id)` | |

#### `messages`
| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK DEFAULT `gen_random_uuid()` | |
| `conversation_id` | `uuid` NOT NULL, FK `conversations(id)` `ON DELETE CASCADE` | |
| `sender_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | |
| `body` | `text` NOT NULL | text content; empty string `''` for voice messages. (Legacy v1 column `content` may exist on the live DB; trigger `messages_sync_body` keeps `body`/`content` in sync, defaulting nulls to `''`.) |
| `created_at` | `timestamptz` NOT NULL DEFAULT `now()` | |
| `reply_to_id` | `uuid` (nullable), FK `messages(id)` `ON DELETE SET NULL` | quoted message |
| `kind` | `text` NOT NULL DEFAULT `'text'` CHECK `kind IN ('text','voice')` | |
| `media_url` | `text` (nullable) | public URL of the voice file |
| `media_path` | `text` (nullable) | Storage path `<userId>/<uuid>.<ext>` in bucket `voice-messages` |
| `view_once` | `boolean` NOT NULL DEFAULT `false` | voice messages are always `true` in the app |
| `consumed_at` | `timestamptz` (nullable) | column exists; **the app never sets it** — `consume_voice_message` deletes the row instead of stamping this column. Do not rely on it. |
| `deleted_by` | `uuid[]` NOT NULL DEFAULT `'{}'` | user ids who deleted the message for themselves; the SELECT policy hides rows containing the viewer's id |

Indexes: `idx_messages_conversation_created ON messages (conversation_id, created_at)`; `idx_participants_user ON conversation_participants (user_id)`.

#### `calls`
| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK DEFAULT `gen_random_uuid()` | client-generated (`crypto.randomUUID()`) on outgoing calls so the id is known before signalling |
| `conversation_id` | `uuid` (nullable), FK `conversations(id)` `ON DELETE SET NULL` | |
| `caller_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | |
| `callee_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | |
| `kind` | `text` NOT NULL DEFAULT `'audio'` CHECK `kind IN ('audio','video')` | |
| `status` | `text` NOT NULL DEFAULT `'ringing'` CHECK `status IN ('ringing','accepted','declined','missed','ended')` | |
| `started_at` | `timestamptz` (nullable) | set when answered |
| `ended_at` | `timestamptz` (nullable) | set on hang-up / media failure |
| `duration_seconds` | `integer` (nullable) | set on hang-up only if the call was answered |
| `created_at` | `timestamptz` NOT NULL DEFAULT `now()` | |

Indexes: `idx_calls_caller_created`, `idx_calls_callee_created` (each `(caller_id|callee_id, created_at DESC)`).

#### `friendships`
| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK DEFAULT `gen_random_uuid()` | |
| `requester_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | who sent the request |
| `addressee_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | who receives it |
| `status` | `text` NOT NULL DEFAULT `'pending'` CHECK `status IN ('pending','accepted','declined')` | |
| `created_at` | `timestamptz` NOT NULL DEFAULT `now()` | |
| `updated_at` | `timestamptz` NOT NULL DEFAULT `now()` | trigger `friendships_updated_at` → `set_updated_at()` |
| CHECK | `requester_id <> addressee_id` | |
| Unique index | `idx_friendships_pair ON ((LEAST(requester_id, addressee_id)), (GREATEST(requester_id, addressee_id)))` | one row per pair in either direction |

#### `message_reactions`
| Column | Type | Notes |
|---|---|---|
| `message_id` | `uuid` NOT NULL, FK `messages(id)` `ON DELETE CASCADE` | part of composite PK |
| `user_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | part of composite PK |
| `emoji` | `text` NOT NULL | one of the 6 in `REACTION_EMOJIS` (§5.5) |
| `created_at` | `timestamptz` NOT NULL DEFAULT `now()` | |
| PK | `(message_id, user_id)` | **one reaction per user per message — enforced by this PK.** The live DB was once missing this PK, which broke the app's upsert (`ON CONFLICT` error); fix file `database/fix-reactions-constraint.sql` adds it (deleting duplicate rows first). Index `idx_reactions_message ON (message_id)`. |

#### `blocks`
| Column | Type | Notes |
|---|---|---|
| `blocker_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | part of composite PK |
| `blocked_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | part of composite PK |
| `created_at` | `timestamptz` NOT NULL DEFAULT `now()` | |
| PK | `(blocker_id, blocked_id)`; CHECK `blocker_id <> blocked_id` | Indexes on each column. |

#### `reports`
| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK DEFAULT `gen_random_uuid()` | |
| `reporter_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | |
| `reported_id` | `uuid` NOT NULL, FK `auth.users(id)` `ON DELETE CASCADE` | |
| `reason` | `text` NOT NULL | one of `Spam`, `Harassment`, `Inappropriate content`, `Fake account`, `Other` |
| `details` | `text` (nullable) | optional, maxlength 500 in the form |
| `created_at` | `timestamptz` NOT NULL DEFAULT `now()` | |

Index `idx_reports_reported ON (reported_id)`. **There is no admin review UI in the app** — reports are stored only.

#### `onesignal_config` (from `database/notify-push.sql`, not `schema.sql`)
| Column | Type | Notes |
|---|---|---|
| `api_key` | `text` | OneSignal REST API key (`os_v2_app_...`). RLS enabled, **no policies** — unreadable via the app API; only the `SECURITY DEFINER` push functions read it. Never commit a real key. |

### 4.2 RLS summary (policies — the port must not bypass these; the same backend is reused)

- Grants: `authenticated` gets `SELECT, INSERT, UPDATE, DELETE` on `profiles`, `conversations`, `conversation_participants`, `messages`, `calls`, `friendships`, `message_reactions`; `SELECT, INSERT, DELETE` on `blocks`; `SELECT, INSERT` on `reports`; plus `USAGE, SELECT` on sequences and default privileges for future tables. (Without these grants, RLS policies alone are not enough — writes fail with `permission denied for table <name>` / error 42501.)
- `profiles`: any authenticated user can SELECT (all rows); INSERT/UPDATE only self (`auth.uid() = id`).
- `conversations`: SELECT only via helper `is_conversation_participant(id)`; INSERT any authenticated user.
- `conversation_participants`: SELECT only via the same helper (a direct `EXISTS` on the same table inside its own policy caused infinite recursion, error 42P17 — hence the `SECURITY DEFINER` helper `is_conversation_participant(p_conversation_id uuid) RETURNS boolean`); INSERT only self; UPDATE only self.
- `messages`: SELECT only participants AND NOT (`auth.uid() = ANY(deleted_by)`); INSERT only if `sender_id = auth.uid()` AND participant AND **no block exists in either direction** between me and the other participant (two `NOT EXISTS` subqueries against `blocks`).
- `calls`: SELECT/UPDATE only caller or callee; INSERT only caller (`caller_id = auth.uid()`).
- `friendships`: SELECT either party; INSERT only requester as self; UPDATE only addressee on a `pending` row; DELETE only requester on a `pending` row (cancel).
- `message_reactions`: SELECT/INSERT only if participant of the message's conversation; INSERT/UPDATE/DELETE only own rows (`user_id = auth.uid()`). (The UPDATE policy exists because PostgREST upsert needs it when changing emoji.)
- `blocks`: SELECT either party; INSERT/DELETE only self as blocker.
- `reports`: INSERT only self as reporter; SELECT only own reports.
- Storage policies: see §10.2.

### 4.3 RPC functions — exact names, parameters, returns

All are `SECURITY DEFINER`, `SET search_path = public` (push functions also `public, extensions`). All are called via `sb.rpc('<name>', { <params> })`.

| Function | Parameters | Returns | Behaviour / errors |
|---|---|---|---|
| `get_or_create_conversation` | `p_user_id uuid` | `uuid` (conversation id) | Raises `not authenticated` if no user; `cannot start a conversation with yourself`; `You blocked this user` / `You cannot message this user` if a block exists either way; `not friends yet — send a friend request first` if no `accepted` friendship. Finds an existing 1-to-1 conversation (exactly 2 participants, both present) or creates one + both participant rows. **Note:** an older parameter name was `p_other`; the current name is `p_user_id` (the old function is dropped first because Postgres forbids renaming a parameter with `CREATE OR REPLACE`). |
| `is_username_available` | `p_username text` | `boolean` | `NOT EXISTS (SELECT 1 FROM profiles WHERE username = lower(p_username))`. `SECURITY DEFINER` so it works before signup (anonymous). |
| `send_friend_request` | `p_user_id uuid` | `void` | Raises `not authenticated`; `cannot send a friend request to yourself`; `a friend request already exists for this person` (if any non-declined row exists either way). Deletes any prior `declined` row between the pair first, then inserts `pending`. |
| `respond_friend_request` | `p_requester_id uuid`, `p_accept boolean` | `void` | Updates the `pending` row where `requester_id = p_requester_id AND addressee_id = me` to `accepted`/`declined`; raises `no pending friend request from this person` if none found. Only the addressee can call it. |
| `friendship_status` | `p_user_id uuid` | `text` | `'none'` \| `'pending_out'` \| `'pending_in'` \| `'friends'` \| `'declined'` from the caller's point of view. |
| `delete_message_for_me` | `p_message_id uuid` | `boolean` | Returns `false` if the message does not exist; raises `not a participant of this conversation` otherwise. Appends `auth.uid()` to `messages.deleted_by` (if not already present). Returns `true`. |
| `delete_message_for_everyone` | `p_message_id uuid` | `text` (media_path or null) | Raises `message not found`; raises `only the sender can delete this message for everyone` if caller is not the sender. Deletes the row; returns its `media_path` so the client can delete the voice file from Storage. |
| `clear_chat_for_me` | `p_conversation_id uuid` | `integer` (count) | Raises `not a participant of this conversation`. Appends `auth.uid()` to `deleted_by` on every message of the conversation; returns the number of rows updated. |
| `consume_voice_message` | `p_message_id uuid` | `boolean` | Returns `false` if: message not found; `kind <> 'voice'`; `view_once` is not true; caller is the sender; already consumed (`consumed_at IS NOT NULL`); caller is not a participant. Otherwise **deletes the row** and returns `true`. (The client then deletes the Storage file.) |
| `is_conversation_participant` | `p_conversation_id uuid` | `boolean` | SQL-language `SECURITY DEFINER` helper used by RLS policies (see §4.2). Not called by the app UI directly (except indirectly through RPCs above). |
| `onesignal_send_push` | `p_external_user_id text`, `p_heading text`, `p_body text`, `p_data jsonb`, `p_web_url text`, `p_icon text DEFAULT NULL`, `p_buttons jsonb DEFAULT NULL` | `void` | DB-side only (see §9.4). Reads the API key from `onesignal_config`; no-op if unconfigured; swallows all errors. |

Triggers: `profiles_updated_at` (BEFORE UPDATE on `profiles` → `set_updated_at()`), `messages_last_message_at` (AFTER INSERT on `messages` → `on_message_created()` updates `conversations.last_message_at`), `on_auth_user_created` (AFTER INSERT on `auth.users` → `handle_new_user()` auto-creates a profile; falls back to `user_<10 hex>` on bad/missing username, and to a generated name on unique violation), `friendships_updated_at`, `messages_sync_body` (BEFORE INSERT OR UPDATE on `messages`, v1 migration only), `messages_notify_push` + `calls_notify_push` (from `notify-push.sql`, §9.4).

### 4.4 How conversations are created and listed

- **Creation:** only via RPC `get_or_create_conversation(p_user_id)` — never by inserting into `conversations`/`conversation_participants` directly from the client. Entry points: People → Message button; profile preview → Message; Calls → Call friends / Call back; chat header call buttons use the already-open conversation id. After the RPC returns, the app calls `refreshConversations()` and navigates to `#/chat/<id>`.
- **Listing (`refreshConversations`):** as in §3.2 — my participant rows → all participant rows + conversation rows → per-conversation latest message + unread count → peer profile → sort by `lastAt` descending (`lastMessage.created_at`, else `conversations.last_message_at`, else `created_at`). The per-conversation query pattern (one latest-message + one count query per conversation) is deliberate: an earlier implementation fetched 500 messages across all conversations, so quiet chats lost their last message once the cap was hit.
- **Finding a conversation with a peer (`getConversationIdWith`):** first check the in-memory list; otherwise query my participant rows, then participant rows for those conversation ids filtered by the peer.
- **Unread count:** messages in the conversation with `sender_id != me` and `created_at > my last_read_at` (all such messages if `last_read_at` is null). Shown as a badge on the conversation row; cleared by `markRead()` when the chat is opened / becomes visible (§5.4).
- **Conversation row rendering:** peer avatar (with online dot), peer name, last-message preview (`You: <body>` if I sent it; `Voice message` if `kind === 'voice'`; `No messages yet — say hello.` if none), time via `fmtListTime` (§5.6), unread badge. Click → `#/chat/<id>`. Search (`#convSearch`, 200 ms debounce) filters by peer `full_name`/`username` substring, case-insensitive.
- **Non-participant guard:** opening `#/chat/<id>` loads the participant rows; if the query succeeds but contains no row for me, toast `You are not part of this conversation.` (error), close the chat, go back to `#/chats`.

---

## 5. Messaging behaviour

### 5.1 Sending text

- Composer: `#composerForm` with `#msgInput` (maxlength 2000, placeholder `Type a message…`), mic button (`#micBtn`), send button (`#sendBtn`, 44 px circle, primary green).
- `onSend`: trim the input; ignore empty input or no active conversation. Clear the input immediately, send `typing-stop` (§5.5). Insert via `sb.from('messages').insert({ conversation_id: activeId, sender_id: me.id, body })` plus `reply_to_id` if replying (§5.3); `.select().single()`. On error: toast `Could not send message. Check your connection.` (network) or `Could not send message: <message>`. On success: clear the reply bar, append the returned message locally (the realtime feed would also deliver it, but `renderedMsgIds` dedupes), `markRead()`, `refreshConversations()`.
- Messages are rendered in ascending `created_at` order, limit 200 per open. The realtime `messages-feed` INSERT handler appends live messages for the active conversation (with scroll-to-bottom) and refreshes the list otherwise.
- Dedup: `renderedMsgIds` Set — `appendMessage` returns early if the id was already rendered.

### 5.2 Message rendering

- **Bubbles:** fit-content width, max-width 75% (85% on mobile ≤820 px). Own messages right-aligned, emerald bubble (`--bubble-me`), white text, no border; others left-aligned, white/`--bubble-them` bubble with 1 px border + shadow. Padding 9 px 13 px, radius 16 px, `word-break: break-word`, `white-space: pre-wrap`. `user-select: none` on bubbles so long-press works cleanly on mobile.
- **No per-message timestamp:** time is NOT shown on the message itself. Time appears only as Messenger-style separators (§5.6) and as ticks inside own bubbles (§5.4).
- **Voice bubbles:** min-width 200 px, flex column: play button (34 px circle), progress bar (clickable to seek), duration `m:ss`, optional `View once` label (11 px, 75% opacity), ticks foot aligned to the end.
- **Deleted messages:** a hard-deleted message (delete-for-everyone) disappears entirely (realtime DELETE removes the element). Quotes pointing at it become `Message deleted` (§5.3).

### 5.3 Replies (quote)

- `startReply(msgId)`: shows `#replyBar` above the composer: left green border, `Replying to <You|peer name>` (12 px, primary) + quoted text (13 px, ellipsis; `Voice message` for voice). Focuses the input. Cancel via `#replyBarCancel` (X) or after sending.
- Sending with a reply sets `reply_to_id` on the insert (§5.1).
- Rendering: a `.quote` block above the bubble (left 3 px primary border, `card-2` background, radius 8 px, min-width 120 px): sender name (`You` / peer name) + quoted text ellipsis. Clicking a quote scrolls the original into view (centred, smooth) and flashes it (`.flash` animation, 1.2 s, primary-soft outline); if the original is not rendered, toast `Original message is no longer visible.` (error).
- Quote resolution: targets are resolved before first render (`replyTargets` map, fetched in bulk). Unknown targets render as `Loading…` then fill in via `ensureReplyTarget` (re-fetches and swaps the quote HTML). If the target is deleted / not visible, the quote shows `Message deleted` (empty name line). A long-press on a quote opens the message menu instead of jumping (guard: if the menu was opened by long-press < 600 ms ago, the click is ignored).

### 5.4 Read receipts — exact 3-state tick logic

Ticks appear **only on my own messages**, inside the bubble, small at the bottom-right (WhatsApp style, `float: right`, `margin: 7px 0 0 8px`, 11–12 px).

Three states (`tickState(m)` in `js/chats.js`):

1. **Seen** — `isRead(m)` is true: `otherLastReadAt` exists AND `new Date(otherLastReadAt) >= new Date(m.created_at)`. Rendered as a **round badge**: 14 px circle, primary-green background (white background with green tick inside own emerald bubble), containing `✓`. CSS class `seen-badge` inside `.ticks.read`.
2. **Delivered** — not seen, but either (a) the message id is in the in-memory `deliveredIds` Set (sticky within the session: once delivered, it stays double-ticked even if the peer goes offline again), or (b) the peer is currently online (`PC.state.online.has(activeOther.id)` — in which case the id is added to `deliveredIds`). Rendered as `✓✓` (plain double tick).
3. **Sent** — otherwise (peer offline, never delivered). Rendered as a single `✓`.

Important semantics:
- **Delivered = "reached the peer's phone"**, approximated as "the peer is online (app connected)". There is no per-message delivery acknowledgement from the server. Offline = sent only.
- **Seen counts only when the peer actually looks at the message**, not merely because the chat was open. `markRead()` runs: on opening a conversation, on receiving a message while the chat is visible (`document.visibilityState === 'visible'`), and on `visibilitychange` back to visible while a chat is active. An open-but-backgrounded chat does NOT mark messages read.
- `markRead()`: `UPDATE conversation_participants SET last_read_at = now() WHERE conversation_id = activeId AND user_id = me.id` (errors swallowed as non-fatal); also zeroes the conversation's unread count locally and re-renders the list.
- Peer's read position arrives live via the `room:<id>` channel (§3.3): on a participant UPDATE from the other user, `otherLastReadAt` is updated and all own-message ticks re-render (`renderMessagesTicks()`). Presence changes also re-render ticks (online → delivered transitions).
- **UNCLEAR from code:** `deliveredIds` is in-memory only (lost on reload); the initial tick state after reopening a chat for messages sent while the peer was offline-then-online is therefore `sent` until presence re-marks them, unless `otherLastReadAt` covers them. Port this behaviour as-is (in-memory set), do not invent a persisted delivered flag.

### 5.5 Typing indicator protocol

- Channel: `typing:<conversationId>` (Broadcast), subscribed while a chat is open, unsubscribed on close (§3.3).
- Events: `typing` with payload `{ user_id, name }`; `typing-stop` with payload `{ user_id }`. Events from my own id are ignored.
- **Send side (`onTypingInput`, on every `#msgInput` input event):** debounced 300 ms after the last keystroke, and throttled to at most once per 2.5 s while typing continues (`typingLastSentAt`). `stopTypingBroadcast()` (on send, and on chat close) clears the timers, resets `typingLastSentAt = 0`, and sends `typing-stop`. Send failures are swallowed (`.catch(() => {})`).
- **Receive side:** `showTyping(name)` renders into `#typingInd` (below the message list): **the peer's small avatar (`xs`, 28 px) + three waving dots — no name text.** (The `name` payload is received but deliberately not displayed.) Dots: 7 px circles in a pill (`card-2` background, 1 px border, radius 999 px, padding 9 px 11 px), waving animation 1.2 s infinite (`typing-wave`: translateY −4 px at 30%, opacity 0.5→1, staggered delays 0 / 0.15 s / 0.3 s). Auto-hide after 3 s of silence (`setTimeout(hideTyping, 3000)`, reset on each new event). The container is empty (`display: none` via `:empty`) when nobody is typing.
- **Android note:** the avatar shown is the *peer's* avatar (from `activeOther`), not the typer's name/photo from the payload — in a 1-to-1 chat these are the same person; keep the "avatar + dots, no name" UI.

### 5.6 Time labels

All formatters are in `js/config.js` (`fmtTime`, `fmtListTime`, `fmtDate`, `timeAgo`); `formatDuration` is in `js/ui.js` (§10.3). Times use the device locale (`toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })` → e.g. `10:24 PM`).

- **Day separators (`.day-sep`, pill) between messages when the calendar day changes:** `Today at 10:24 PM` (if the message's day is today), `Yesterday at 10:24 PM` (if yesterday), otherwise `<Weekday, Month Day> at <time>` via `fmtDate` (e.g. `Saturday, November 14 at 10:24 PM` — `toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })` + ` at ` + `fmtTime`).
- **20-minute gap labels (`.time-sep`, plain centred text, no pill):** same day, but the gap since the previous message exceeds 20 minutes (`gap > 20 * 60 * 1000` ms) → a centred label with just the time, e.g. `10:24 PM`.
- **Chat-list time (`fmtListTime`):** today → clock time (`10:24 PM`); yesterday → the literal string `Yesterday`; within the last 7 days → weekday short (`Mon`); older → short date (`Nov 16`, `{ month: 'short', day: 'numeric' }`).
- **`timeAgo(iso)`** (used for call history + anywhere relative): < 45 s → `just now`; < 1 h → `<n>m ago`; < 24 h → `<n>h ago`; < 48 h → `yesterday`; older → short date (`Nov 16`).
- **`fmtDate(iso)`:** `{ weekday: 'long', month: 'long', day: 'numeric' }`.

### 5.7 Long-press menu (message menu)

- **Trigger:** press-and-hold a message for **500 ms** (pointerdown timer; moving > 10 px cancels — scrolling cancels the press; pointerup/leave/cancel also cancel). Right-click on desktop also opens it. There is **no three-dot button** on messages (an earlier version had one; it was removed). Taps on the voice-play button or reaction chips do not start a long-press.
- **Menu (`#msgMenu`):** fixed-position floating card near the anchor bubble (clamped to the viewport: left `max(8, min(r.left, innerWidth - width - 8))`, below the bubble + 6 px, or above it if it would overflow the bottom). Items (in order):
  1. `React` — replaces the menu content with the emoji picker row (§5.8).
  2. `Reply` — starts a reply (§5.3).
  3. `Copy` — only for non-voice messages with a body; copies `body` to the clipboard; toast `Copied` (or `Could not copy` on failure).
  4. `Delete for me` — only on my own messages.
  5. `Delete for everyone` — only on my own messages, styled danger (red).
- Closing: outside click (with a guard — the click ending the same long-press, within 600 ms of the menu opening, must not close it), `Escape`, or choosing an item. Item clicks `stopPropagation()` so the document-level outside-click handler does not close the menu in the same tap (this matters for `React`, whose click swaps the menu content).
- **Delete for me:** RPC `delete_message_for_me(p_message_id)`; on success remove the element locally + `refreshConversations()`; on error toast `Could not delete message. Check your connection.` / `Could not delete message: <message>`.
- **Delete for everyone:** RPC `delete_message_for_everyone(p_message_id)` (sender-only, server-enforced); returns `media_path` — if non-null, also delete that file from the `voice-messages` bucket (best-effort); remove locally + refresh list. Same error toasts.
- **Clear chat** (not in this menu — in the profile preview, §6.5): RPC `clear_chat_for_me(p_conversation_id)` after a `confirm('Clear all messages in this chat for you?')`; toast `Chat cleared.` (success); refresh the conversation list, close the modal, and if that chat is currently open, reload it so messages disappear at once.

### 5.8 Reactions

- Emoji set (`REACTION_EMOJIS`, in this order): `['❤️', '😂', '😮', '😢', '😡', '👍']` — six emojis. (Project memory notes the user wants five rather than six, with the choice of which to drop undecided — **the web code still has six; port six unless told otherwise.**)
- **Toggle:** tapping an emoji in the picker, or tapping an existing reaction chip, calls `toggleReaction(msgId, emoji)`: if I already reacted with that emoji → `DELETE FROM message_reactions WHERE message_id AND user_id = me AND emoji`; otherwise `upsert({ message_id, user_id: me.id, emoji }, { onConflict: 'message_id,user_id' })`. Errors → toast `Could not react: <message>`. (The upsert requires the PK from §4.1; see the fix file note there.)
- **Rendering:** chips (`.react-chip`) under the bubble, grouped by emoji with a count when > 1 (e.g. `❤️ 2`); my own reaction's chip is highlighted (primary border + `primary-soft` background, class `mine`). Own-message chips are right-aligned. Chips are clickable (toggle that emoji).
- **Live updates:** via `reactions-feed` (§3.3). INSERT replaces any existing row from the same user for that message; DELETE removes that user's reaction; then re-render that message's chips. Reactions for the initially loaded 200 messages are fetched in one query (§3.2).
- Reactions are best-effort: a failed fetch on open is swallowed silently.

### 5.9 Voice messages (view-once)

- **Recording:** mic button (`#micBtn`) in the composer. Guards: if a preview is pending → toast `Finish or cancel the current voice clip first.`; if already recording → toast `Recording already in progress — tap Stop to finish.` Unsupported browser (`MediaRecorder`/`getUserMedia` missing) → toast `Voice recording is not supported in this browser.` (error). Mic denied → toast `Microphone is blocked. Tap the lock icon in the address bar → Site settings → Microphone → Allow, then tap the mic again.` (error); other mic errors → `Microphone unavailable: <message>`.
- **Constraints:** max **120 s** (`MAX_VOICE_SECONDS = 120`) — auto-stop at the limit. Preferred MIME types in order: `audio/webm;codecs=opus`, `audio/webm`, `audio/mp4`, `audio/ogg` (first supported, else `''`).
- **Recording UI:** `#voiceRecBar` above the composer: red pulsing dot (12 px, `recpulse` animation), timer `mm:ss` (updates every 500 ms), hint text `Recording voice message…`, `Stop` button (danger, small).
- **Preview:** after stop, `#voicePreviewBar`: play/pause button, duration `mm:ss`, progress bar, `Send` (primary, small) and `Cancel` (ghost, small). If the user left the chat while recording, the clip is discarded (the recording captures `convId` at start and compares it to `activeId` on stop).
- **Send (`sendVoicePreview`):** upload to Storage bucket `voice-messages` at path `<myUserId>/<randomUUID>.<ext>` (ext from MIME: `.m4a` for mp4, `.ogg` for ogg, else `.webm`; `contentType` = base MIME with the codec parameter stripped, e.g. `audio/webm` — the bucket allowlist rejects `audio/webm;codecs=opus`); `upsert: false`. Then `getPublicUrl(path)` and insert a message: `{ conversation_id, sender_id: me.id, body: '', kind: 'voice', media_url: publicUrl, media_path: path, view_once: true }`. On error → toast `Could not send voice message: <message>`. On success: close preview, append locally, `markRead()`, refresh list.
- **Playback:** tap the play button in a voice bubble. One clip plays at a time (`playingAudio`); starting another pauses the current. Progress bar fills during playback; clicking the bar seeks. Duration label fills in from `loadedmetadata` (`formatDuration`). Play failure → toast `Could not play voice message.` (error).
- **View-once consume (`onVoiceEnded`):** only when playback reaches the natural `ended` event — pausing or stopping early never counts — AND the message is `view_once`, AND I am not the sender, AND it was not already consumed this session (`consumedVoiceIds` Set). Then RPC `consume_voice_message(p_message_id)`; only after the server returns `true` is the id marked consumed, the Storage file deleted (best-effort), the message element removed, and the conversation list refreshed. A failed consume can be retried by listening again.
- **Android note:** use `MediaRecorder` (Android) with a supported MIME/container the bucket allowlist accepts (`audio/webm`, `audio/mp4`, `audio/mpeg`, `audio/ogg`, `audio/wav`, `audio/x-m4a`; see §10.2). The 120 s cap, preview-before-send, and "full listen by the recipient only" consume rule must all be preserved.

---

## 6. Friend system

Core rule (stated in `js/friends.js` and enforced in SQL): **nobody can message or call anyone until a friend request is sent and accepted.** The People directory lists everyone, but Message / Call buttons appear only for friends.

### 6.1 States

Client cache (`Map<otherUserId, state>`) values: `'none'` | `'pending_out'` | `'pending_in'` | `'friends'` | `'declined'`. Derived from `friendships` rows: `accepted` → `friends`; `pending` + I am requester → `pending_out`; `pending` + I am addressee → `pending_in`; `declined` → `declined`; no row → `none`. `getFriendshipStatus(userId)` returns the cached value if present, else calls RPC `friendship_status` (§4.3) and caches it. The cache is reloaded on any `friendships-feed` realtime event (§3.3) and listeners notified.

### 6.2 Send / accept / decline / cancel

- **Send:** RPC `send_friend_request(p_user_id)`; cache → `pending_out`; toast `Friend request sent.` (success). Errors: `Could not send request. Check your connection.` / `Could not send request: <message>`. Server rejects self-requests and duplicates; a prior `declined` row is deleted first so a fresh request is allowed.
- **Respond:** RPC `respond_friend_request(p_requester_id, p_accept)`; cache → `friends`/`declined`; toast `Friend request accepted.` / `Friend request declined.` (success). Errors: `Could not respond. Check your connection.` / `Could not respond: <message>`.
- **Cancel (outgoing):** direct delete `DELETE FROM friendships WHERE requester_id = me AND addressee_id = <id> AND status = 'pending'`; cache → `none`; toast `Friend request cancelled.` Errors: `Could not cancel request: <message>`.

### 6.3 People tab (`#/people`, `#view-people`)

Two sub-tabs (`.sub-tabs`): `All users` (`#peopleTabAll`) and `Requests` (`#peopleTabRequests`) with a red badge (`#requestsBadge`, `#dc2626`, count of incoming requests, hidden when 0). Search box (`#peopleSearch`, placeholder `Search by name or username`, 200 ms debounce) filters the All tab by name/username substring.

**All users tab:** grid of person cards (`.person-card`, min 240 px columns): avatar (`lg`, clickable → preview), name (clickable → preview), `@username` + ` · Online` (green) when online, bio **only if privacy allows** (§6.4; otherwise an empty bio slot), action buttons per state (§6.3.1). Directory query: all profiles except me, newest first, limit 200.

**Requests tab:** two sections — `Incoming requests` (`#incomingList`) and `Outgoing requests` (`#outgoingList`). Rows (`.req-row`): avatar (`sm`, clickable → preview), name (clickable → preview), `@username [· Online]`, actions. Empty texts per §1.4.

#### 6.3.1 Action buttons by friendship state (People cards)

| State | Buttons shown |
|---|---|
| Blocked (either direction) | Red `Blocked` label; `Unblock` button only if I blocked them |
| `friends` | Green `✓ Friends` badge (`userCheck` icon) + `Message` (primary) + audio-call icon button + video-call icon button |
| `pending_out` | Disabled `Request sent` + `Cancel` |
| `pending_in` | `Accept` (primary) + `Decline` |
| `none` / `declined` | `Add Friend` (primary) |

`Message` → `getOrCreateConversation` then `#/chat/<id>`; call buttons → `getOrCreateConversation` then `startCall`.

### 6.4 Profile privacy (`is_public`)

Rule (`canSeeDetails(profile, status)`): full profile details are visible when **any** of: `profile.is_public !== false` (public, the default), status is `friends`, or a request is pending either way (`pending_in` / `pending_out` — so a recipient can inspect the sender before deciding). Otherwise (private + stranger) only avatar, name and username are shown.

- **People directory:** always shows avatar + name + username (so people can be identified); bio only when `canSeeDetails` is true.
- **Profile preview modal (§6.5):** when details are hidden, the details area is empty and `#pvNote` shows `This profile is private — only friends can see the details.`
- **Privacy toggle:** Settings → Privacy card (§7). Text: `Profile visibility — Public: anyone can see your full profile info. Private: only your friends can see it; strangers see only your name and photo.` Toggle updates `profiles.is_public`; toasts `Your profile is now public.` / `Your profile is now private.` (success).
- **UNCLEAR from code:** the directory SELECT policy allows any authenticated user to read all profile rows (including `bio`); privacy is enforced only in the app's rendering, not in SQL. A native port using the same backend inherits this; do not claim server-side privacy enforcement.

### 6.5 Profile preview modal (`openProfilePreview`, `#profileModal`)

Opens by tapping any avatar or name in People / Requests / chat header / conversation flows. If the profile is my own → navigate to `#/profile` instead.

**Order, top to bottom (exact):**
1. Close button (X, top-right).
2. Avatar (`lg`, 88 px).
3. Full name (`#pvName`), `@username` (`#pvUsername`).
4. Status line (`#pvStatus`): `Online` (green) / `Offline`.
5. Action buttons (`#pvActions`) — depends on state (see below).
6. Details (`#pvDetails`): bio (`.pv-bio`, muted), `Member since <Mon D, YYYY>` (e.g. `Member since Nov 15, 2026`), and a status line: `You are friends` (green) / `Friend request sent — waiting for a reply` / `This person wants to be your friend` — or the private-profile note (`#pvNote`) instead when details are hidden.
7. Extra rows (`#pvExtra`, top border, left-aligned): `Clear chat` (only if a conversation with this person exists), `Block` / `Unblock` (danger/red), `Report` (danger/red).

**Action buttons by state:**
| State | Buttons |
|---|---|
| Blocked (either) | Red `Blocked` label; `Unblock` (ghost) only if I blocked them. No Message/Call buttons. |
| `friends` | `Message` (primary), `Audio call` (ghost), `Video call` (ghost) |
| `pending_out` | Disabled `Request sent` + `Cancel` |
| `pending_in` | `Accept` (primary) + `Decline` |
| `none`/`declined` | `Add Friend` (primary) |

After an action the modal re-opens refreshed (`openProfilePreview(profile)` again) or closes (e.g. after Decline / Cancel / opening a chat). `Message` closes the modal and navigates to the chat; call buttons close the modal and start the call.
- **Clear chat:** `confirm('Clear all messages in this chat for you?')` → RPC `clear_chat_for_me` (§5.7).
- **Block / Unblock:** see §6.6.
- **Report:** opens `#reportModal` (§6.6).

### 6.6 Blocks and reports

- **Block (`blockUser`):** `INSERT INTO blocks (blocker_id: me.id, blocked_id: userId)`; local cache add; toast `User blocked.`; notify listeners. Errors: `Could not block user: <message>`.
- **Unblock (`unblockUser`):** `DELETE FROM blocks WHERE blocker_id = me AND blocked_id = userId`; toast `User unblocked.` Errors: `Could not unblock user: <message>`.
- **Direction check (`checkBlockDirection(peerId)`):** direct query (not cache) so it is safe before the cache is warm: returns `'i_blocked'` if I blocked them, `'blocked_me'` if they blocked me, else `'none'`. Used by calls (outgoing + incoming auto-decline) and by `get_or_create_conversation` (server-side, §4.3).
- **Effects of a block:** no contact either way — message INSERT is refused by RLS (§4.2), `get_or_create_conversation` raises, outgoing calls are refused with toasts (§3.5), incoming calls from a blocked user auto-decline silently. Blocked users show `Blocked` in People/preview and are excluded from "Call friends". Note: blocking does **not** delete the conversation or its messages.
- **Report (`submitReport`):** modal with 5 radio reasons — `Spam`, `Harassment`, `Inappropriate content`, `Fake account`, `Other` — plus optional details (maxlength 500). No reason selected → toast `Choose a reason for the report.` (error). Insert into `reports`: `{ reporter_id: me.id, reported_id, reason, details: <trimmed or null> }`. Success: close modal, toast `Thanks, your report was sent.` (success). Errors: `Could not send report: <message>`. No admin review UI exists.

---

## 7. Profile and Settings

### 7.1 My profile (`#/profile`, `#view-profile`)

Hero card (`.profile-hero`, centred): avatar (`lg`, rendered 104 px in the wrap) with a camera upload button (36 px circle, primary green, white border, bottom-right of the avatar); full name (22 px); `@username` (hint grey); bio (or `No bio yet.`); `Member since <Month D, YYYY>` (e.g. `Member since November 15, 2026`); stats row: **Friends** count + **Chats** count (counts via §3.2; failure leaves 0); `Signed in as <email>`; `Edit profile` button; a `Settings` button (primary, full width) at the bottom → `#/settings`.

**Edit form** (`#profileEditForm`, toggled by `Edit profile` / `Cancel`): Full name (`#pfName`, maxlength 60), Username (`#pfUsername`, maxlength 20, live availability check with the same hints as register — but unchanged current username shows the default hint, not an error), Bio (`#pfBio`, maxlength 120, placeholder `A short line about you`). `Save changes` / `Cancel`.
- Save: `UPDATE profiles SET full_name, username, bio (or null if empty) WHERE id = me.id`, `.select().single()`; update local `PC.state.profile`, re-render profile + me-card, hide the form, show `Profile saved.` (success message + toast). Validation: name ≥ 2 → `Enter your full name.`; username regex → `Username must be 3–20 letters, numbers or underscores.` Errors: `Could not save: <message>`. Busy label `Saving…`.
- **Photo upload (`onPhotoChosen`):** file input `accept="image/*"` (input value reset after each pick so the same file can be picked again). Validation: must be an image → `Please choose an image file.` (error); size ≤ **2 MB** (`MAX_PHOTO_BYTES = 2 * 1024 * 1024`) → `Photo must be smaller than 2 MB.` (error). Path: `<myUserId>/<Date.now()>.<ext>` where ext is `png`/`webp`/`gif` by MIME, else `jpg`. Upload to bucket `avatars` (`cacheControl: '3600'`, `upsert: false`); then `getPublicUrl(path)`; then `UPDATE profiles SET avatar_url = url`. Upload failure → `Upload failed: <message>`; URL failure → `Upload failed: could not get the photo URL.`; profile-save failure → `Photo uploaded but could not save it: <message>`. Success: update local profile, re-render, toast `Profile photo updated.` (success). The upload button is disabled while uploading.

### 7.2 Settings (`#/settings`, `#view-settings`)

Cards in order (each `.card`):

1. **Appearance** — row button (`#settingsThemeRow`): sun/moon icon + label `Switch to dark theme` / `Switch to light theme` (depending on current theme). Toggles `data-theme` on `<html>` + persists to `localStorage` key `pingchat-theme`. The top-bar theme button (`#themeBtnMobile`) does the same.
2. **Privacy** — description text (see §6.4) + a row: label `Public profile` / `Private profile` (`#privacyStateLabel`) + switch (`#privacyToggle`, `role="switch"`). See §6.4 for behaviour.
3. **Sound** — row: `Incoming call ringtone` + switch (`#ringtoneToggle`). Stored in `localStorage` key `pingchat-ringtone` (`on`/`off`; default on — `ringtoneEnabled()` returns true unless the stored value is exactly `'off'`). Toasts `Ringtone on.` / `Ringtone off.`
4. **Notifications** — description `Get a notification on this phone when someone sends you a message or calls you.` Row: `Push notifications` + state label (`#pushStateLabel`): `Checking…` / `Not available` / `Blocked` / `On` / `Off`. Hint (`#pushStateHint`) when unavailable/blocked (see §9.3). Button (`#pushToggleBtn`): `Turn on notifications` / `Turn off notifications`. This card is driven by `syncPushRow()` every time Settings opens.
5. **Install app** (`#installCard`) — **only shown when the browser offers installation** (`beforeinstallprompt` fired; hidden on desktop/iOS). Text: `Add PingChat to your phone's home screen for a full-screen app experience.` Button `Install PingChat on your phone`. Success toast `PingChat installed. Look for it on your home screen.` (success); dismissed → `Install dismissed. You can try again later.` **Android note:** this PWA-install card has no native equivalent — do not port it. (The native app *is* the installed app.)
6. **Security** — change-password form (`#changePassForm`): New password (`#setPass1`), Confirm new password (`#setPass2`), button `Change password` (busy `Saving…`). Validation: ≥ 8 → `Password must be at least 8 characters.`; match → `Passwords do not match.` Calls `sb.auth.updateUser({ password: p1 })`. Success: form reset + `Password changed.` (success message + toast). Errors: `Could not change password: <message>`.
7. **Account** — `Log out` button (`#settingsLogoutBtn`, danger, full width). **This is the only logout in the app** (§2.7).

Switch component (`.switch`): 46×26 px pill, knob 20 px, slides left 3 px → 23 px; `.on` = primary-green background.

---

## 8. Design tokens

Source: `css/app.css` `:root` and `[data-theme="dark"]`. Copy the hex values exactly.

### 8.1 Colours

| Token | Light | Dark |
|---|---|---|
| `--bg` (app background) | `#F6F7F9` | `#0F172A` |
| `--card` | `#FFFFFF` | `#1E293B` |
| `--card-2` (secondary surface / input) | `#F1F5F4` | `#172033` |
| `--text` | `#0F172A` | `#F1F5F9` |
| `--text-2` | `#334155` | `#CBD5E1` |
| `--muted` | `#64748B` | `#94A3B8` |
| `--border` | `#E2E8F0` | `#334155` |
| `--primary` (emerald) | `#059669` | `#10B981` |
| `--primary-hover` | `#047857` | `#059669` |
| `--primary-soft` | `#D1FAE5` | `#064E3B` |
| `--primary-text` (text on primary) | `#FFFFFF` | (unchanged — still `#FFFFFF`) |
| `--danger` | `#DC2626` | `#F87171` |
| `--danger-soft` | `#FEE2E2` | `#450A0A` |
| `--input-bg` | `#FFFFFF` | `#0F172A` |
| `--bubble-me` (own bubble) | `#059669` | `#059669` |
| `--bubble-me-text` | `#FFFFFF` | (unchanged) |
| `--bubble-them` | `#FFFFFF` | `#1E293B` |
| `--shadow` | `0 1px 3px rgba(15,23,42,.08), 0 4px 14px rgba(15,23,42,.05)` | `0 1px 3px rgba(0,0,0,.35), 0 4px 14px rgba(0,0,0,.25)` |
| `--radius` | `16px` | — |
| `--radius-sm` | `10px` | — |
| `--topbar-h` | `56px` | — |
| `--tabbar-h` | `60px` | — |
| `theme-color` (meta + manifest) | `#059669` | — |
| Manifest `background_color` | `#F6F7F9` | — |
| Call overlay background | `#0B1220` (both themes) | — |
| Online dot green | `#22C55E` | — |
| Offline dot grey | `#94A3B8` | — |
| Toast background | `#0F172A` (light) / `#F1F5F9` (dark), text inverted | — |
| Toast error background | `var(--danger)` | — |
| Toast success background | `var(--primary)` | — |
| Requests badge background | `#dc2626` (hard-coded, both themes) | — |
| Auth background gradient | `linear-gradient(160deg, var(--primary-soft) 0%, var(--bg) 55%)` | — |

**Avatar fallback colours** (`colorFor(str)` in `js/config.js`): hash the id/name (`n = (n*31 + charCode) >>> 0`) and pick from:
`['#059669', '#0D9488', '#0891B2', '#0284C7', '#4F46E5', '#7C3AED', '#DB2777', '#DC2626', '#D97706', '#65A30D']`.
Avatar text is white, bold; initials are first + last name initials, uppercased (single word → first letter only; empty → `?`).

### 8.2 Typography

- Font stack: `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif`.
- Base size 15 px, line-height 1.45, `-webkit-font-smoothing: antialiased`.
- Auth card `h1`: 22 px; brand wordmark: 20 px, weight 800; section `h2`: 20 px; card `h3`: 16 px; buttons: 14 px, weight 600; field labels: 13 px, weight 600; hints: 12 px; message foot/ticks: 11–12 px; tab-bar labels: 11 px, weight 600.
- **Android note:** there is no custom font — use the system sans-serif (Roboto on Android) at equivalent sizes. Do not invent a brand font.

### 8.3 Spacing / radii / sizes

- Buttons (`.btn`): pill radius 999 px, padding 10 px 18 px; `.btn-sm`: 7 px 12 px, 13 px; icon buttons: 40 px circle; send button: 44 px circle.
- Inputs (`.input`): padding 11 px 14 px, radius `--radius-sm` (10 px), 1 px `--border`; focus: border primary + `0 0 0 3px var(--primary-soft)` ring.
- Auth card: max-width 420 px, radius 20 px, padding 28 px 26 px, border + shadow.
- Cards (`.card`): radius `--radius` (16 px), padding 20 px, margin-bottom 16 px, border + shadow.
- Conversation rows: padding 11 px 10 px, radius 12 px; active row background `var(--primary-soft)`.
- Chat body padding: 18 px 16 px, message gap 6 px.
- Composer: padding 10 px 14 px; composer input is a pill (radius 999 px) on `card-2` background.
- Chat header: padding 10 px 14 px.
- Bottom tab bar height 60 px (+ safe-area inset); top bar 56 px.
- Modal: max-width 360 px (profile modal 380 px), radius 20 px, padding 28 px 24 px; backdrop `rgba(15,23,42,.55)`.
- Toast: pill (radius 999 px), padding 11 px 16 px, bottom 76 px, centred, max-width 420 px.
- Message menu: min-width 190 px, max-width 260 px, radius 12 px, padding 6 px; items padding 9 px 12 px, radius 8 px.
- Voice bubble min-width 200 px; voice play button 34 px; progress bar height 5 px.
- Call overlay control buttons: 58 px circles, end button 66 px, gap 14 px, bottom 28 px; local video 120×170 px (92×130 px mobile), radius 14 px.

### 8.4 Avatars and online dot

Sizes (`.avatar`): default 46 px (font 16 px); `.sm` 36 px (13 px); `.xs` 28 px (11 px); `.lg` 84 px (30 px). Profile hero wrap renders 104 px (font 36 px); call overlay fallback avatar 110 px (40 px); incoming modal / profile modal avatar 88 px (32 px). All are circles (`border-radius: 50%`); photos use `object-fit: cover`. Only an `https://` `avatar_url` is rendered as an image (regex `/^https:\/\//i`); otherwise initials on the hash colour. A broken image removes itself (`onerror="this.remove()"`, falling back to initials).

**Online dot (Messenger style):** absolutely positioned at the avatar's bottom-right, straddling the edge — `right: -3px; bottom: -3px` (default 46 px: dot 12 px, border 2 px solid `var(--card)`); `.sm`: 11 px, −2 px; `.xs`: 9 px, −2 px, 1.5 px border; `.lg`: 16 px, −4 px, 3 px border. Grey `#94A3B8` when offline, green `#22C55E` (class `on`) when online. **The avatar must NOT have `overflow: hidden`, or the outside part of the dot is cut off.** The dot is rendered on every avatar that has a user id (`avatarHtml` includes it whenever `p.id` exists); in the call overlay / modals the avatar is rendered the same way.

### 8.5 Icons

**No emoji anywhere in the UI** (reactions are the only exception — they are user content, not UI chrome). All icons are inline SVG (Feather-style, `viewBox 0 0 24 24`, `fill: none`, `stroke: currentColor`, `stroke-width: 2`, round caps/joins), defined as path data in `js/config.js` (`P` map + `icon(name, size)`). Icon names used: `chat`, `phone`, `video`, `videoOff`, `phoneOff`, `users`, `user`, `search`, `send`, `mic`, `play`, `pause`, `more`, `micOff`, `back`, `logout`, `sun`, `moon`, `check`, `checkDouble`, `clock`, `x`, `edit`, `eye`, `eyeOff`, `phoneIn`, `phoneMissed`, `settings`, `camera`, `userPlus`, `userCheck`, `shield`, `bell`. Default icon size 20 px (`svg.icon { width: 20px; height: 20px }`); the Google "G" logo is a separate 4-colour SVG (18 px) embedded in `index.html`.
**Note:** several defined icons are unused in the current UI (`more`, `check`, `checkDouble`, `clock`, `edit`, `logout`, `phoneIn`, `settings`, `userPlus`, `shield`, `bell`). The tick marks in bubbles are **text characters** (`✓`, `✓✓`), not the `check`/`checkDouble` icons. Port the icons actually rendered; unused ones need not be ported.
**Android note:** reproduce these as vector drawables (or Material icons with the same meaning) — do not substitute emoji.

### 8.6 Theme behaviour

- Light is the default (`<html data-theme="light">`). Dark overrides the tokens in §8.1 only; layout is unchanged.
- Theme is stored in `localStorage` key `pingchat-theme` (`light`/`dark`) and applied before first paint (inline head script) to avoid a flash.
- Toggles: top-bar button (icon `moon` in light mode, `sun` in dark mode) and Settings → Appearance row (label flips `Switch to dark theme` ↔ `Switch to light theme`).
- **Android note:** map to a light/dark theme setting (persisted locally); the exact token values above are the source of truth for both themes.

---

## 9. OneSignal web integration

### 9.1 App ID and SDK setup (`js/push.js`, `index.html`, `sw.js`)

- **App ID:** `da3c98f6-d518-41d1-807e-0e8b77c702d6` (public; lives in client code).
- SDK: OneSignal Web SDK **v16**, loaded via `<script src="https://cdn.onesignal.com/sdks/web/v16/OneSignalSDK.page.js" defer>` in `index.html`, used through the `window.OneSignalDeferred` queue (`runOneSignal(fn)` helper, 15 s timeout, never throws).
- `OneSignal.init` options:
  ```js
  {
    appId: 'da3c98f6-d518-41d1-807e-0e8b77c702d6',
    serviceWorkerOverrideForTypical: true,
    path: '/pingchat',
    serviceWorkerPath: 'sw.js',
    serviceWorkerParam: { scope: '/pingchat/' }
  }
  ```
- Service worker: OneSignal's worker code is merged into the app's own `sw.js` via `importScripts('https://cdn.onesignal.com/sdks/web/v16/OneSignalSDK.sw.js')` (guarded — a failed import only disables push), because two workers on the same `/pingchat/` scope would replace each other. Resolved worker URL: `https://mdhasibulislam-dotcom.github.io/pingchat/sw.js`.
- **Android note:** the Web SDK, service-worker path/scope, and `serviceWorkerOverrideForTypical` are web-only. On Android, use the **OneSignal Android SDK** with the same App ID, and set the external ID as below. Do not attempt to reuse the web service-worker setup.

### 9.2 Login / external ID

- On every login / session restore (`setupUser`): `OneSignal.login(String(userId))` (Supabase user id as the **external ID**) + `OneSignal.User.addTag('username', username)` (if a username exists). Logging in with the same id is a server-side no-op.
- On logout (`logout()`): `OneSignal.logout()` (via `clearPushUser()`).
- The DB push functions target users with `include_aliases.external_id = [<supabase user id>]` (§9.4) — this is why the external ID must be the Supabase user id.
- **REST API key:** `os_v2_app_...` — stored in the `onesignal_config` table (§4.1), **never in client code or the repo**. The native app must not embed it either; server-side sending stays in Postgres (§9.4).

### 9.3 Notification state, prompt, and Settings UI

- `getPushState()` returns `{ supported, permission ('default'|'granted'|'denied'), optedIn, subscribedId, initError }`. `supported: false` when init failed (with `initError`, e.g. `OneSignal SDK did not load within 15 seconds.`). Permission is read from `OneSignal.Notifications.permission` / `permissionNative` with `Notification.permission` as fallback (in SDK build 160801 `Notifications.permission` is a boolean, `permissionNative` is the string).
- **One-time prompt (`maybePromptPushOnce`, `js/app.js`):** on the first login after this shipped, if push is supported, permission is `default`, and the user is not opted in, ask once via `promptPush()`. The `localStorage` flag `pingchat-push-prompted` is set **before** asking, whether they allow or dismiss, so it never nags. Runs in the background; never blocks boot.
- **`promptPush()`:** tries `OneSignal.Slidedown.promptPush()` first; falls back to `OneSignal.User.PushSubscription.optIn()`. If the slidedown resolves in under 1 s with no state change (suppressed — never actually shown), it falls back to the native `optIn()` prompt. Returns `true` when the prompt was shown or the user is already subscribed.
- **`optOut()`:** `OneSignal.User.PushSubscription.optOut()`.
- **Settings → Notifications card (`syncPushRow` / `onPushToggle`, §7.2):** label `Checking…` → `Not available` (hint: `Push is unavailable: <initError>` or `Try Chrome — some browsers block push notifications.`) / `Blocked` (hint: `Allow notifications for this site in your browser settings.`) / `On` / `Off`; button `Turn on notifications` / `Turn off notifications` (hidden when not available/blocked). Toggle outcomes: opt-out → toast `Notifications turned off.`; opt-in success → `Notifications are on.` (success); denied → `Notifications are blocked for this site. Allow them in your browser settings.` (error); prompt not shown → `Could not turn on notifications. Try again from Settings.` (error); still off → `Notifications are still off.`
- All push calls are best-effort and never throw; if the OneSignal CDN fails to load, init resolves `false` and the app works normally without push.

### 9.4 Server-side sending (`database/notify-push.sql` — no client changes needed)

Push is sent **from the database itself** (Postgres triggers + `pg_net`), so it works even when the recipient's app is closed. No app server is needed. The native app reuses this unchanged — it only needs the same external-ID login (§9.2) and notification-tap handling (§9.5).

- Extension: `pg_net` (`CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions`). Check health: `SELECT * FROM net._http_response ORDER BY created DESC LIMIT 5;` — 200 + a OneSignal notification id = worked; 401 = wrong API key; 400 = bad request body.
- `onesignal_send_push(p_external_user_id, p_heading, p_body, p_data, p_web_url, p_icon, p_buttons)` → `POST https://api.onesignal.com/notifications?c=push`, header `Authorization: Key <key>` for `os_v2_...` keys (or `Basic <key>` for legacy keys), body: `app_id`, `include_aliases: { external_id: [userId] }`, `target_channel: "push"`, `name: "PingChat"`, `headings: { en }`, `contents: { en }`, `data`, optional `web_url`, optional `chrome_web_icon` (sender photo, https only), optional `web_buttons`. Async via `net.http_post` (never waits); all errors swallowed.
- **New message** (`notify_message_push`, trigger `messages_notify_push`, AFTER INSERT on `messages`): for every other participant: heading = sender display name (`full_name` else `username` else `Someone`); body = first 100 chars of `body`, or `Voice message` for voice, or `New message` if empty; `data = { type: 'message', conversation_id, sender_id }`; `web_url = https://mdhasibulislam-dotcom.github.io/pingchat/#/chat/<conversationId>`; icon = sender's `avatar_url` (https only); buttons: `Reply` (url `https://mdhasibulislam-dotcom.github.io/pingchat/?reply=1#/chat/<id>`) and `Open chat` (url = `web_url`).
- **New call** (`notify_call_push`, trigger `calls_notify_push`, AFTER INSERT on `calls`): only when `NEW.status = 'ringing'`; heading = caller name; body = `Incoming audio call from <name>` / `Incoming video call from <name>`; `data = { type: 'call', call_id, kind }`; `web_url = https://mdhasibulislam-dotcom.github.io/pingchat/#/calls`; icon = caller's photo; no buttons.
- To disable: drop the two triggers and the three functions (`messages_notify_push`, `calls_notify_push`, `notify_message_push()`, `notify_call_push()`, `onesignal_send_push(...)`) — see the header of `notify-push.sql`.
- **Notification styles chosen by the user (project memory):** style 1 (sender photo as icon, `chrome_web_icon`) + style 3 (action buttons Reply + Open chat, `web_buttons`); NOT style 2 (no big image, `chrome_web_image`).

### 9.5 Notification click behaviour

- Tapping a message notification opens `web_url` — the chat URL. Tapping the **Reply** button opens `https://mdhasibulislam-dotcom.github.io/pingchat/?reply=1#/chat/<id>`; the app detects `?reply=1` on open (`openConversation`), focuses the composer (`#msgInput`), then strips the parameter with `history.replaceState` (leaving path + hash).
- Tapping a call notification opens `#/calls`.
- **Android note:** reproduce with deep links carrying the conversation id and a `reply=1` flag that focuses the composer on open. The `data` payloads (`type: 'message'|'call'`, `conversation_id`/`sender_id`/`call_id`/`kind`) are the stable contract — use them, not the web URLs.

---

## 10. Anything else a porter needs

### 10.1 Toasts and error messages (`js/config.js`, `js/ui.js`)

- `toast(msg, type)`: appended to `#toasts`; auto-removed after 3.5 s, or 5 s for `type === 'error'`. Types: default (dark pill), `'error'` (red), `'success'` (green). `showMsg(el, text, type)` sets an inline form message (`.form-msg`; error = red `danger-soft` background; `success` = green `primary-soft` background; hidden when text is empty).
- `friendlyError(err)` maps Supabase/auth error text to user-facing strings (checked in order):
  - `/Invalid login credentials/i` → `Incorrect email or password.`
  - `/Email not confirmed/i` → `Please confirm your email first — check your inbox.`
  - `/already registered|already been registered/i` → `This email is already registered. Try logging in.`
  - `/rate limit|too many/i` → `Too many attempts. Please wait a minute and try again.`
  - `/Failed to fetch|NetworkError/i` → `Network problem. Check your connection and try again.`
  - `/duplicate key|23505|profiles_username_key/i` → `That username is already taken.`
  - `/provider (is )?not enabled|unsupported provider/i` → `Google sign-in is not turned on for this app yet. Please log in with email instead.`
  - `/Database error saving new user/i` → `Could not create your account. The username may already be taken.`
  - otherwise the raw message, or `Something went wrong. Please try again.`
- Other literal strings used as toasts/messages (grep the list below when porting; the full set lives in the JS files):
  - `Could not reach the server. Check your connection.` / `Something went wrong while starting PingChat. Please reload.` / `Could not send message. Check your connection.` / `Could not send message: <message>` / `Could not react: <message>` / `Could not delete message. Check your connection.` / `Could not delete message: <message>` / `Could not clear chat. Check your connection.` / `Could not clear chat: <message>` / `Chat cleared.` / `Could not open chat. Check your connection.` / `Could not open chat: <message>` / `You are not part of this conversation.` / `Copied` / `Could not copy` / `Original message is no longer visible.` / `You are already in a call.` / `Cannot call this person right now.` / `You blocked this user.` / `You cannot call this user.` / `Could not start call: <message>` / `Microphone/camera unavailable: <message>` / `Could not start call: <message> (offer failed)` / `No answer — call marked as missed.` / `Call declined.` / `Call ended.` / `Call cancelled.` / `Call failed: <message>` / `Connection lost — trying to reconnect…` / `Connection failed. The other side may be unreachable (a TURN server may be needed).` / `Could not reach the other side. Check your connection and try again.` / `Friend request sent.` / `Friend request accepted.` / `Friend request declined.` / `Friend request cancelled.` / `Could not send request. Check your connection.` / `Could not respond. Check your connection.` / `Could not cancel request: <message>` / `User blocked.` / `User unblocked.` / `Could not block user: <message>` / `Could not unblock user: <message>` / `Choose a reason for the report.` / `Thanks, your report was sent.` / `Could not send report: <message>` / `Profile photo updated.` / `Photo must be smaller than 2 MB.` / `Please choose an image file.` / `Upload failed: <message>` / `Upload failed: could not get the photo URL.` / `Photo uploaded but could not save it: <message>` / `Profile saved.` / `Could not save: <message>` / `Your profile is now public.` / `Your profile is now private.` / `Could not update privacy: <message>` / `Ringtone on.` / `Ringtone off.` / `Password changed.` / `Could not change password: <message>` / `Notifications turned off.` / `Notifications are on.` / `Notifications are blocked for this site. Allow them in your browser settings.` / `Could not turn on notifications. Try again from Settings.` / `Notifications are still off.` / `PingChat installed. Look for it on your home screen.` / `Install dismissed. You can try again later.` / `Voice recording is not supported in this browser.` / `Microphone is blocked. Tap the lock icon in the address bar → Site settings → Microphone → Allow, then tap the mic again.` / `Microphone unavailable: <message>` / `Recording already in progress — tap Stop to finish.` / `Finish or cancel the current voice clip first.` / `Could not send voice message: <message>` / `Could not play voice message.` / `Could not play preview.`

### 10.2 Storage buckets and policies (`schema.sql` §8/§10)

| Bucket | Public | Size limit | Allowed MIME types | Path pattern | Policies |
|---|---|---|---|---|---|
| `avatars` | yes | 2 MB (2097152) | `image/jpeg`, `image/png`, `image/webp`, `image/gif` | `<userId>/<timestamp>.<ext>` | Anyone can SELECT; INSERT/UPDATE/DELETE only if `(storage.foldername(name))[1] = auth.uid()::text` (own folder) |
| `voice-messages` | yes | 5 MB (5242880) | `audio/webm`, `audio/mp4`, `audio/mpeg`, `audio/ogg`, `audio/wav`, `audio/x-m4a` | `<userId>/<uuid>.<ext>` | Same shape as `avatars` (public read; own-folder write/replace/delete) |

- Files are addressed by public URL (`getPublicUrl(path)`). Avatar URLs are stored on `profiles.avatar_url`; voice URLs/paths on `messages.media_url` / `messages.media_path`.
- Deletion of voice files is client-side and best-effort: after `delete_message_for_everyone` returns a `media_path`, and after a successful `consume_voice_message`, the client calls `sb.storage.from('voice-messages').remove([mediaPath])`. **Note:** only the file *owner* can delete it per the storage policy — if the deleter is not the owner (e.g. the recipient consuming a voice message, or the sender deleting a message whose file they own — the sender is the owner in both delete-for-everyone cases), the remove may fail silently; the app swallows this. **UNCLEAR from code:** whether the recipient's consume-time `remove()` actually succeeds under the own-folder policy when the sender owns the file — the code attempts it and ignores failure, and the DB row deletion is what matters. Port the same attempt-and-ignore behaviour; do not treat a failed file-delete as an error.
- Old avatar files are never deleted when a new photo is uploaded (no cleanup code exists).

### 10.3 Small helpers (`js/ui.js`, `js/config.js`)

- `esc(s)`: HTML-escapes `& < > " '` (used for every user-supplied string rendered into HTML — the native port must not render raw HTML from user content; this is an XSS guard in the web app, not a display feature).
- `debounce(fn, ms)`: used for search inputs (200 ms: conversation search, People search) and username checks (350 ms).
- `formatDuration(totalSeconds)`: `mm:ss`, zero-padded (`String(m).padStart(2,'0') + ':' + String(r).padStart(2,'0')`), floors seconds, clamps negatives to 0. Used for voice durations, call timer, call-history durations.
- `timeAgo` / `fmtTime` / `fmtListTime` / `fmtDate`: see §5.6.

### 10.4 PWA / manifest / service worker (web-only; do not port as-is)

- `manifest.webmanifest`: `{ "name": "PingChat", "short_name": "PingChat", "description": "PingChat — fast, private real-time messaging with audio and video calls.", "start_url": "./", "scope": "./", "display": "standalone", "orientation": "portrait", "background_color": "#F6F7F9", "theme_color": "#059669", icons: 192 any, 512 any, 512 maskable }`. Icon files: `assets/icons/icon-192.png`, `icon-512.png`, `icon-512-maskable.png`, `favicon.svg`, `logo.svg`.
- `sw.js`: cache name `pingchat-v11`; cache-first for the app shell (`./`, `index.html`, `manifest.webmanifest`, `css/app.css`, all `js/*.js`, icons) — bump the cache name on each release; **network-only for everything else** (Supabase API calls are never cached); offline navigation falls back to cached `index.html`. Only same-origin GET requests are handled. OneSignal worker code is merged in via `importScripts` (§9.1).
- `js/install.js`: captures `beforeinstallprompt` (preventDefault, stash the event), exposes `getInstallPrompt()` / `promptInstall()` (one-shot; `userChoice.outcome === 'accepted'`) used by the Settings install card (§7.2).
- **Android:** none of this is needed — the native app replaces the PWA. Keep the icon assets (logo/favicon) as the app icon source if useful.

### 10.5 Local storage keys (web `localStorage` — map to Android preferences)

| Key | Values | Purpose |
|---|---|---|
| `pingchat-theme` | `light` / `dark` | Theme (§8.6) |
| `pingchat-ringtone` | `on` / `off` (default on when absent) | Incoming-call ringtone (§3.6) |
| `pingchat-push-prompted` | `1` | One-time push prompt already asked (§9.3) |

### 10.6 Edge cases and gotchas found in the code

1. **Double-init guards:** `setupUser()` can run twice (boot + `SIGNED_IN`); every `init*` function has an `*Initialized` boolean guard so listeners are bound once. Without this, messages send twice and calls start twice. The Android port must likewise ensure single subscription / single listener registration per session.
2. **Session restore vs. sign-in:** `setupUser` runs for a restored session at boot AND for a fresh sign-in; profile creation, presence, and channel subscriptions must work in both paths.
3. **Auth hash parsing:** Supabase tokens in the URL hash (`#access_token=…&type=recovery`) must be ignored by the router (`parseHash` returns an empty route) and must trigger the new-password panel (§2.5).
4. **Presence-dependent ticks:** delivered state depends on the *peer's* online presence at render time (§5.4); ticks re-render on presence changes.
5. **Message ordering vs. separators:** day/gap separators are computed from the *previously rendered* message element at append time (`prev.dataset.day`, `prev.dataset.createdAt`); a re-rendered list must recompute them in order, not per-message in isolation.
6. **Voice clip ownership on send:** the recording captures `convId` at start; if the user navigates away before stopping, the clip is discarded on stop rather than sent to the wrong chat.
7. **Call overlay race:** remote media can arrive before the overlay is shown; `showCallOverlay()` re-attaches an already-arrived `remoteStream` (§3.5). A native implementation must handle early media the same way.
8. **Busy auto-decline:** an incoming offer while already in a call is auto-declined (caller sees `Call declined.`), and blocked callers are auto-declined silently.
9. **Stale ringing rows:** history displays `ringing` older than 60 s as `missed` (§3.5); the DB row itself is not updated by this display rule.
10. **Username race:** profile creation retries with a generated username if the trigger created a conflicting row (§2.6); `ensureProfile` re-selects before giving up.
11. **Reactions PK:** the live DB once lacked the `message_reactions` PK, breaking upserts — the fix file exists because `CREATE TABLE IF NOT EXISTS` never adds a missing PK (§4.1). If the native port ever runs migrations, apply `fix-reactions-constraint.sql`.
12. **Legacy v1 columns:** the live DB may still carry v1 columns (`messages.content`, `conversations.pair_key`, `conversation_members`, `conversation_reads`) and the `messages_sync_body` trigger keeps `body`/`content` in sync. The port should use `body` only and ignore the legacy columns.
13. **Voice MIME stripping:** the codec parameter must be stripped before upload (`audio/webm;codecs=opus` → `audio/webm`), or the bucket allowlist rejects the upload (§5.9).
14. **Avatar URL validation:** only `https://` avatar URLs are rendered as images; anything else falls back to initials (§8.4). Google profile photos arrive as https URLs and are fine.
15. **`consumed_at` is unused:** the column exists but the consume flow deletes the row instead (§4.1). Do not build UI around it.
16. **No admin UI:** reports are write-only from the app; no review/moderation screens exist.
17. **No group chats:** every conversation is 1-to-1 (exactly 2 participants); `get_or_create_conversation` enforces this. Do not add group-chat UI.
18. **No message editing, no file/image messages:** message `kind` is only `text` or `voice`; there is no image/file sharing, no message editing, and no forwarding.
19. **Message limit:** 200 messages loaded per chat open, 200 people in the directory, 50 calls in history — these caps are in the queries (§3.2). There is no pagination / infinite scroll in the web app.
20. **Call while messaging:** calls are started from a chat header or People/Calls; there is no in-chat call UI beyond the overlay, and no call can be started without a conversation id (`getOrCreateConversation` first).

### 10.7 What was NOT found / is unclear

- **Supabase Auth email templates / confirmation redirect behaviour** beyond `emailRedirectTo` (§2.3) — server-side configuration, not visible in code.
- **OneSignal dashboard settings** (segments, exact notification appearance on Android) — only the API payload contract (§9.4) is in code.
- **Whether `detectSessionInUrl` deep-link handling has a native equivalent configured** in Supabase (Site URL / Redirect URLs) for the Android app — must be set up as part of the port (see §2.4).
- **Push delivery on Android when the app is killed** — the DB trigger sends to OneSignal regardless of app state, but native receipt/display depends on the OneSignal Android SDK + FCM setup, which is outside this spec's sources.
- **Exact WebRTC behaviour on Android** (e.g. audio routing, speakerphone, camera selection) — the web code uses browser defaults; the native port must make its own (sensible) choices and note them.

---

## Appendix A — Element-ID quick reference (from `index.html`)

Auth: `authRoot`, `view-login`, `view-register`, `view-reset`, `loginMsg`, `loginForm`, `loginEmail`, `loginPassword`, `showForgot`, `loginBtn`, `googleLoginBtn`, `googleRegisterBtn`, `regMsg`, `registerForm`, `regName`, `regUsername`, `unameHint`, `regEmail`, `regPassword`, `regPassword2`, `regBtn`, `forgotWrap`, `forgotMsg`, `forgotForm`, `forgotEmail`, `forgotBtn`, `newPassWrap`, `resetMsg`, `resetForm`, `newPassword`, `newPassword2`, `resetBtn`.
App: `appLayout`, `convSearch`, `convList`, `convListMobile`, `meCard`, `meAvatar`, `meName`, `meSub`, `topAvatar`, `themeBtnMobile`, `view-chats`, `view-chat`, `view-calls`, `view-people`, `view-profile`, `view-settings`, `backBtn`, `chatAvatar`, `chatWho`, `chatName`, `chatStatus`, `audioCallBtn`, `videoCallBtn`, `msgList`, `typingInd`, `replyBar`, `replyBarName`, `replyBarText`, `replyBarCancel`, `voiceRecBar`, `voiceRecTimer`, `voiceRecStop`, `voicePreviewBar`, `voicePrevPlay`, `voicePrevDur`, `voicePrevProg`, `voicePrevSend`, `voicePrevCancel`, `composerForm`, `micBtn`, `msgInput`, `sendBtn`, `callFriendsList`, `callsList`, `peopleTabAll`, `peopleTabRequests`, `requestsBadge`, `peopleAllWrap`, `peopleSearch`, `peopleGrid`, `peopleReqWrap`, `incomingList`, `outgoingList`, `profileAvatar`, `avatarUploadBtn`, `avatarFileInput`, `profileHeadName`, `profileHeadUser`, `profileHeadBio`, `profileMemberSince`, `statFriends`, `statChats`, `profileEmail`, `profileEditBtn`, `profileEditForm`, `profileMsg`, `profileForm`, `pfName`, `pfUsername`, `pfUnameHint`, `pfBio`, `pfSaveBtn`, `pfCancelBtn`, `goSettingsBtn`, `settingsBackBtn`, `settingsThemeRow`, `settingsThemeIcon`, `settingsThemeLabel`, `privacyStateLabel`, `privacyToggle`, `ringtoneToggle`, `pushStateLabel`, `pushStateHint`, `pushToggleBtn`, `installCard`, `installAppBtn`, `changePassMsg`, `changePassForm`, `setPass1`, `setPass2`, `changePassBtn`, `settingsLogoutBtn`.
Overlays: `callOverlay`, `remoteVideo`, `remoteAudio`, `remoteFallback`, `callAvatar`, `callPeerName`, `callStatus`, `callStatusBar`, `callPeerName2`, `callTimer`, `localVideo`, `muteBtn`, `cameraBtn`, `endCallBtn`, `incomingModal`, `incomingAvatar`, `incomingName`, `incomingKind`, `declineBtn`, `acceptBtn`, `profileModal`, `pvClose`, `pvAvatar`, `pvName`, `pvUsername`, `pvStatus`, `pvActions`, `pvDetails`, `pvNote`, `pvExtra`, `reportModal`, `reportName`, `reportDetails`, `reportCancel`, `reportSubmit`, `msgMenu`, `toasts`.

## Appendix B — Files to re-read when porting

- `js/config.js` — exact Supabase init, time formatters, avatar/icon helpers (copy the constants, not the DOM code).
- `js/chats.js` — the tick logic, typing protocol, and voice consume flow are the most behaviour-dense parts; re-read before implementing messaging.
- `js/calls.js` — signalling retry logic and the `ontrack` remote-stream handling are the most bug-prone parts; re-read before implementing calls.
- `database/schema.sql` — the authoritative data model; `database/notify-push.sql` — the push contract.
- `css/app.css` — the authoritative design tokens.
