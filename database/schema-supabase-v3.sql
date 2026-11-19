-- PingChat Android v3 — Supabase schema for the NEW project.
-- Target: https://tjsalnhtuqqigyhvgpnb.supabase.co ("hasibulialam's Project")
-- Run in: Supabase Dashboard > SQL Editor > New query. IDEMPOTENT — safe to run again.
--
-- WHY THE `pingchat_` PREFIX: this project is shared with the MCQ exam app,
-- which already has tables named profiles, questions, subjects, etc. (including
-- a `profiles` table). Every PingChat table is prefixed so nothing collides
-- and nothing of the exam app is touched.
--
-- WHAT THIS CREATES: profiles, conversations (+ participants), messages,
-- message_reactions, friendships, calls, blocks, reports; RLS policies;
-- RPC functions (get-or-create conversation, friends, delete/clear/consume);
-- triggers (updated_at, last_message_at); Realtime publication; Storage
-- buckets avatars / voice-messages / chat-photos (free tier — this is what
-- unblocks photo + voice without Firebase Blaze).
--
-- WHAT THIS DOES NOT DO (on purpose):
-- - No push trigger. Push sending stays client-triggered: the app calls the
--   already-deployed `send-push` Edge Function (PushSender.kt). Do NOT add
--   a database trigger that calls send-push or messages would notify twice.
-- - No auto-create-profile trigger on auth.users. The app creates the
--   profile row on sign-in (ensureProfile). A trigger would also create
--   PingChat profile rows for the exam app's users, who share this project.
-- - No data migration. Fresh start: old Firebase users/data are dropped.
-- - Typing + call signaling use Realtime Broadcast channels (no tables),
--   as the v1 Android app did. That also removes the M3 bug class
--   (ICE candidates lost while the call doc did not exist yet).
--
-- Tables use snake_case columns matching the app's @SerialName values
-- (full_name, avatar_url, fcm_token, created_at, ...).

-- ============================================================
-- 1. TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS public.pingchat_profiles (
  id uuid PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
  username text NOT NULL UNIQUE,
  full_name text NOT NULL DEFAULT '',
  bio text,
  avatar_url text,
  phone text,
  fcm_token text,
  is_public boolean NOT NULL DEFAULT true,
  online boolean NOT NULL DEFAULT false,
  last_seen timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.pingchat_conversations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  last_message_at timestamptz,
  last_message text,
  last_message_kind text CHECK (last_message_kind IN ('text', 'voice', 'photo')),
  last_message_id uuid,
  last_sender_id uuid REFERENCES auth.users (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS public.pingchat_conversation_participants (
  conversation_id uuid NOT NULL REFERENCES public.pingchat_conversations (id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  last_read_at timestamptz,
  PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.pingchat_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES public.pingchat_conversations (id) ON DELETE CASCADE,
  sender_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  body text NOT NULL DEFAULT '',
  reply_to_id uuid REFERENCES public.pingchat_messages (id) ON DELETE SET NULL,
  kind text NOT NULL DEFAULT 'text' CHECK (kind IN ('text', 'voice', 'photo')),
  media_url text,
  media_path text,
  view_once boolean NOT NULL DEFAULT false,
  consumed_at timestamptz,
  deleted_by uuid[] NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.pingchat_message_reactions (
  message_id uuid NOT NULL REFERENCES public.pingchat_messages (id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  emoji text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (message_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.pingchat_friendships (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  addressee_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'accepted', 'declined')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (requester_id <> addressee_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pingchat_friendships_pair
  ON public.pingchat_friendships ((LEAST(requester_id, addressee_id)), (GREATEST(requester_id, addressee_id)));
CREATE INDEX IF NOT EXISTS idx_pingchat_friendships_requester ON public.pingchat_friendships (requester_id);
CREATE INDEX IF NOT EXISTS idx_pingchat_friendships_addressee ON public.pingchat_friendships (addressee_id);

CREATE TABLE IF NOT EXISTS public.pingchat_calls (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid REFERENCES public.pingchat_conversations (id) ON DELETE SET NULL,
  caller_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  callee_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  caller_name text,
  kind text NOT NULL DEFAULT 'audio' CHECK (kind IN ('audio', 'video')),
  status text NOT NULL DEFAULT 'ringing'
    CHECK (status IN ('ringing', 'accepted', 'declined', 'missed', 'ended')),
  offer_sdp jsonb,
  answer_sdp jsonb,
  started_at timestamptz,
  ended_at timestamptz,
  duration_seconds integer,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.pingchat_blocks (
  blocker_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  blocked_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (blocker_id, blocked_id),
  CHECK (blocker_id <> blocked_id)
);

CREATE TABLE IF NOT EXISTS public.pingchat_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  reported_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  reason text NOT NULL,
  details text,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Helpful indexes (idempotent).
CREATE INDEX IF NOT EXISTS idx_pingchat_messages_conversation_created
  ON public.pingchat_messages (conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_pingchat_participants_user
  ON public.pingchat_conversation_participants (user_id);
CREATE INDEX IF NOT EXISTS idx_pingchat_calls_caller_created
  ON public.pingchat_calls (caller_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pingchat_calls_callee_created
  ON public.pingchat_calls (callee_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pingchat_reactions_message
  ON public.pingchat_message_reactions (message_id);
CREATE INDEX IF NOT EXISTS idx_pingchat_blocks_blocker ON public.pingchat_blocks (blocker_id);
CREATE INDEX IF NOT EXISTS idx_pingchat_blocks_blocked ON public.pingchat_blocks (blocked_id);
CREATE INDEX IF NOT EXISTS idx_pingchat_reports_reported ON public.pingchat_reports (reported_id);

-- ============================================================
-- 2. ROW LEVEL SECURITY
-- ============================================================

ALTER TABLE public.pingchat_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pingchat_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pingchat_conversation_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pingchat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pingchat_message_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pingchat_friendships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pingchat_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pingchat_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pingchat_reports ENABLE ROW LEVEL SECURITY;

-- GRANTS — without these, RLS policies alone are not enough.
-- (No ALTER DEFAULT PRIVILEGES here on purpose: this project is shared
-- with the exam app, so grants are scoped to the pingchat_* tables only.)
GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_profiles TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_conversations TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_conversation_participants TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_messages TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_message_reactions TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_friendships TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_calls TO authenticated;
GRANT SELECT, INSERT, DELETE ON public.pingchat_blocks TO authenticated;
GRANT SELECT, INSERT ON public.pingchat_reports TO authenticated;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO authenticated;

-- RLS HELPER — fixes "infinite recursion detected in policy
-- for relation conversation_participants" (error 42P17).
-- A SECURITY DEFINER helper checks membership with RLS bypassed,
-- so no policy ever queries conversation_participants directly.
CREATE OR REPLACE FUNCTION public.pingchat_is_conversation_participant(p_conversation_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.pingchat_conversation_participants
    WHERE conversation_id = p_conversation_id AND user_id = auth.uid()
  );
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_is_conversation_participant(uuid) TO authenticated;

-- pingchat_profiles: readable by any signed-in user; only self can insert/update.
DROP POLICY IF EXISTS "pingchat_profiles_select" ON public.pingchat_profiles;
CREATE POLICY "pingchat_profiles_select" ON public.pingchat_profiles
  FOR SELECT TO authenticated USING (true);
DROP POLICY IF EXISTS "pingchat_profiles_insert_self" ON public.pingchat_profiles;
CREATE POLICY "pingchat_profiles_insert_self" ON public.pingchat_profiles
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = id);
DROP POLICY IF EXISTS "pingchat_profiles_update_self" ON public.pingchat_profiles;
CREATE POLICY "pingchat_profiles_update_self" ON public.pingchat_profiles
  FOR UPDATE TO authenticated USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

-- pingchat_conversations: readable by participants; insertable by any
-- signed-in user (the RPC below is the only way to make a working
-- conversation — a user can only add THEMSELVES as a participant, so a
-- direct insert can never add the peer); updatable by participants
-- (the app refreshes the last-message preview columns on send).
DROP POLICY IF EXISTS "pingchat_conversations_select_participant" ON public.pingchat_conversations;
CREATE POLICY "pingchat_conversations_select_participant" ON public.pingchat_conversations
  FOR SELECT TO authenticated USING (
    public.pingchat_is_conversation_participant(pingchat_conversations.id)
  );
DROP POLICY IF EXISTS "pingchat_conversations_insert_auth" ON public.pingchat_conversations;
CREATE POLICY "pingchat_conversations_insert_auth" ON public.pingchat_conversations
  FOR INSERT TO authenticated WITH CHECK (true);
DROP POLICY IF EXISTS "pingchat_conversations_update_participant" ON public.pingchat_conversations;
CREATE POLICY "pingchat_conversations_update_participant" ON public.pingchat_conversations
  FOR UPDATE TO authenticated USING (
    public.pingchat_is_conversation_participant(pingchat_conversations.id)
  ) WITH CHECK (
    public.pingchat_is_conversation_participant(pingchat_conversations.id)
  );

-- pingchat_conversation_participants: readable by participants (a
-- participant can see the other participant's row — that is how the app
-- finds the chat peer). Insertable/updateable by self only
-- (last_read_at lives here).
DROP POLICY IF EXISTS "pingchat_participants_select" ON public.pingchat_conversation_participants;
CREATE POLICY "pingchat_participants_select" ON public.pingchat_conversation_participants
  FOR SELECT TO authenticated USING (
    public.pingchat_is_conversation_participant(pingchat_conversation_participants.conversation_id)
  );
DROP POLICY IF EXISTS "pingchat_participants_insert_self" ON public.pingchat_conversation_participants;
CREATE POLICY "pingchat_participants_insert_self" ON public.pingchat_conversation_participants
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_participants_update_self" ON public.pingchat_conversation_participants;
CREATE POLICY "pingchat_participants_update_self" ON public.pingchat_conversation_participants
  FOR UPDATE TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

-- pingchat_messages: readable by conversation participants; insertable by
-- participants. A message is hidden from a user who deleted it for
-- themselves (deleted_by contains their id). Insert is refused when
-- either side blocked the other. Message rows are never updated or
-- deleted directly — all mutations go through the RPCs in §3
-- (delete-for-me, delete-for-everyone, clear-chat, view-once consume).
DROP POLICY IF EXISTS "pingchat_messages_select_participant" ON public.pingchat_messages;
CREATE POLICY "pingchat_messages_select_participant" ON public.pingchat_messages
  FOR SELECT TO authenticated USING (
    public.pingchat_is_conversation_participant(pingchat_messages.conversation_id)
    AND NOT (auth.uid() = ANY(COALESCE(pingchat_messages.deleted_by, '{}')))
  );
DROP POLICY IF EXISTS "pingchat_messages_insert_participant" ON public.pingchat_messages;
CREATE POLICY "pingchat_messages_insert_participant" ON public.pingchat_messages
  FOR INSERT TO authenticated WITH CHECK (
    sender_id = auth.uid()
    AND public.pingchat_is_conversation_participant(pingchat_messages.conversation_id)
    AND NOT EXISTS (
      SELECT 1 FROM public.pingchat_blocks b
      JOIN public.pingchat_conversation_participants p ON p.user_id = b.blocker_id
      WHERE p.conversation_id = pingchat_messages.conversation_id
        AND p.user_id <> auth.uid()
        AND b.blocked_id = auth.uid()
    )
    AND NOT EXISTS (
      SELECT 1 FROM public.pingchat_blocks b
      JOIN public.pingchat_conversation_participants p ON p.user_id = b.blocked_id
      WHERE p.conversation_id = pingchat_messages.conversation_id
        AND p.user_id <> auth.uid()
        AND b.blocker_id = auth.uid()
    )
  );

-- pingchat_message_reactions: readable by conversation participants;
-- insert/update/delete only your own row (one reaction per user per
-- message — the PK enforces it; PostgREST upsert = toggle).
DROP POLICY IF EXISTS "pingchat_reactions_select_participant" ON public.pingchat_message_reactions;
CREATE POLICY "pingchat_reactions_select_participant" ON public.pingchat_message_reactions
  FOR SELECT TO authenticated USING (
    EXISTS (SELECT 1 FROM public.pingchat_messages m
            WHERE m.id = pingchat_message_reactions.message_id
              AND public.pingchat_is_conversation_participant(m.conversation_id))
  );
DROP POLICY IF EXISTS "pingchat_reactions_insert_own" ON public.pingchat_message_reactions;
CREATE POLICY "pingchat_reactions_insert_own" ON public.pingchat_message_reactions
  FOR INSERT TO authenticated WITH CHECK (
    user_id = auth.uid()
    AND EXISTS (SELECT 1 FROM public.pingchat_messages m
                WHERE m.id = pingchat_message_reactions.message_id
                  AND public.pingchat_is_conversation_participant(m.conversation_id))
  );
DROP POLICY IF EXISTS "pingchat_reactions_update_own" ON public.pingchat_message_reactions;
CREATE POLICY "pingchat_reactions_update_own" ON public.pingchat_message_reactions
  FOR UPDATE TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_reactions_delete_own" ON public.pingchat_message_reactions;
CREATE POLICY "pingchat_reactions_delete_own" ON public.pingchat_message_reactions
  FOR DELETE TO authenticated USING (user_id = auth.uid());

-- pingchat_friendships: either side can read; only the requester can
-- create; only the addressee can accept/decline a pending row; only the
-- requester can delete (cancel) a pending row.
DROP POLICY IF EXISTS "pingchat_friendships_select_party" ON public.pingchat_friendships;
CREATE POLICY "pingchat_friendships_select_party" ON public.pingchat_friendships
  FOR SELECT TO authenticated USING (requester_id = auth.uid() OR addressee_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_friendships_insert_requester" ON public.pingchat_friendships;
CREATE POLICY "pingchat_friendships_insert_requester" ON public.pingchat_friendships
  FOR INSERT TO authenticated WITH CHECK (requester_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_friendships_update_addressee" ON public.pingchat_friendships;
CREATE POLICY "pingchat_friendships_update_addressee" ON public.pingchat_friendships
  FOR UPDATE TO authenticated
  USING (addressee_id = auth.uid() AND status = 'pending')
  WITH CHECK (addressee_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_friendships_delete_requester" ON public.pingchat_friendships;
CREATE POLICY "pingchat_friendships_delete_requester" ON public.pingchat_friendships
  FOR DELETE TO authenticated USING (requester_id = auth.uid() AND status = 'pending');

-- pingchat_calls: readable / insertable / updatable by caller or callee only.
DROP POLICY IF EXISTS "pingchat_calls_select_party" ON public.pingchat_calls;
CREATE POLICY "pingchat_calls_select_party" ON public.pingchat_calls
  FOR SELECT TO authenticated USING (caller_id = auth.uid() OR callee_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_calls_insert_caller" ON public.pingchat_calls;
CREATE POLICY "pingchat_calls_insert_caller" ON public.pingchat_calls
  FOR INSERT TO authenticated WITH CHECK (caller_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_calls_update_party" ON public.pingchat_calls;
CREATE POLICY "pingchat_calls_update_party" ON public.pingchat_calls
  FOR UPDATE TO authenticated USING (caller_id = auth.uid() OR callee_id = auth.uid())
  WITH CHECK (caller_id = auth.uid() OR callee_id = auth.uid());

-- pingchat_blocks: either side can read; only the blocker can create/delete.
DROP POLICY IF EXISTS "pingchat_blocks_select_party" ON public.pingchat_blocks;
CREATE POLICY "pingchat_blocks_select_party" ON public.pingchat_blocks
  FOR SELECT TO authenticated USING (blocker_id = auth.uid() OR blocked_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_blocks_insert_self" ON public.pingchat_blocks;
CREATE POLICY "pingchat_blocks_insert_self" ON public.pingchat_blocks
  FOR INSERT TO authenticated WITH CHECK (blocker_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_blocks_delete_self" ON public.pingchat_blocks;
CREATE POLICY "pingchat_blocks_delete_self" ON public.pingchat_blocks
  FOR DELETE TO authenticated USING (blocker_id = auth.uid());

-- pingchat_reports: only the reporter can create / read their own reports.
DROP POLICY IF EXISTS "pingchat_reports_insert_self" ON public.pingchat_reports;
CREATE POLICY "pingchat_reports_insert_self" ON public.pingchat_reports
  FOR INSERT TO authenticated WITH CHECK (reporter_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_reports_select_own" ON public.pingchat_reports;
CREATE POLICY "pingchat_reports_select_own" ON public.pingchat_reports
  FOR SELECT TO authenticated USING (reporter_id = auth.uid());

-- ============================================================
-- 3. FUNCTIONS (RPCs the app calls)
-- ============================================================

-- Find the 1-to-1 conversation with another user, or create it.
-- Enforces blocks + the friend system (chat only after an accepted
-- friend request), like the Firebase version's getOrCreateConversation.
DROP FUNCTION IF EXISTS public.pingchat_get_or_create_conversation(uuid);
CREATE OR REPLACE FUNCTION public.pingchat_get_or_create_conversation(p_user_id uuid)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_conv_id uuid;
  v_me uuid := auth.uid();
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF p_user_id = v_me THEN RAISE EXCEPTION 'cannot start a conversation with yourself'; END IF;

  IF EXISTS (SELECT 1 FROM pingchat_blocks WHERE blocker_id = v_me AND blocked_id = p_user_id) THEN
    RAISE EXCEPTION 'You blocked this user';
  END IF;
  IF EXISTS (SELECT 1 FROM pingchat_blocks WHERE blocker_id = p_user_id AND blocked_id = v_me) THEN
    RAISE EXCEPTION 'You cannot message this user';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pingchat_friendships
                 WHERE status = 'accepted'
                   AND ((requester_id = v_me AND addressee_id = p_user_id)
                     OR (requester_id = p_user_id AND addressee_id = v_me))) THEN
    RAISE EXCEPTION 'not friends yet — send a friend request first';
  END IF;

  SELECT c.id INTO v_conv_id
  FROM pingchat_conversations c
  WHERE EXISTS (SELECT 1 FROM pingchat_conversation_participants p WHERE p.conversation_id = c.id AND p.user_id = v_me)
    AND EXISTS (SELECT 1 FROM pingchat_conversation_participants p WHERE p.conversation_id = c.id AND p.user_id = p_user_id)
    AND (SELECT count(*) FROM pingchat_conversation_participants p WHERE p.conversation_id = c.id) = 2
  LIMIT 1;

  IF v_conv_id IS NULL THEN
    INSERT INTO pingchat_conversations DEFAULT VALUES RETURNING id INTO v_conv_id;
    INSERT INTO pingchat_conversation_participants (conversation_id, user_id)
    VALUES (v_conv_id, v_me), (v_conv_id, p_user_id);
  END IF;
  RETURN v_conv_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_get_or_create_conversation(uuid) TO authenticated;

-- Username availability check. SECURITY DEFINER so it also works before
-- signup (when the caller is still anonymous).
CREATE OR REPLACE FUNCTION public.pingchat_is_username_available(p_username text)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT NOT EXISTS (SELECT 1 FROM pingchat_profiles WHERE username = lower(p_username));
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_is_username_available(text) TO anon, authenticated;

-- Send a friend request. Rejects self-requests and duplicates; a
-- previous 'declined' row is removed first so a fresh request is allowed.
CREATE OR REPLACE FUNCTION public.pingchat_send_friend_request(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me uuid := auth.uid();
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF p_user_id = v_me THEN RAISE EXCEPTION 'cannot send a friend request to yourself'; END IF;
  DELETE FROM pingchat_friendships
  WHERE status = 'declined'
    AND ((requester_id = v_me AND addressee_id = p_user_id)
      OR (requester_id = p_user_id AND addressee_id = v_me));
  IF EXISTS (SELECT 1 FROM pingchat_friendships
             WHERE (requester_id = v_me AND addressee_id = p_user_id)
                OR (requester_id = p_user_id AND addressee_id = v_me)) THEN
    RAISE EXCEPTION 'a friend request already exists for this person';
  END IF;
  INSERT INTO pingchat_friendships (requester_id, addressee_id, status)
  VALUES (v_me, p_user_id, 'pending');
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_send_friend_request(uuid) TO authenticated;

-- Accept (p_accept = true) or decline a pending request. Only the
-- addressee of the request can call this, and only on a pending row.
CREATE OR REPLACE FUNCTION public.pingchat_respond_friend_request(p_requester_id uuid, p_accept boolean)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me uuid := auth.uid();
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  UPDATE pingchat_friendships
  SET status = CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END
  WHERE requester_id = p_requester_id AND addressee_id = v_me AND status = 'pending';
  IF NOT FOUND THEN RAISE EXCEPTION 'no pending friend request from this person'; END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_respond_friend_request(uuid, boolean) TO authenticated;

-- 'none' | 'pending_out' | 'pending_in' | 'friends' | 'declined'
-- from the caller's point of view.
CREATE OR REPLACE FUNCTION public.pingchat_friendship_status(p_user_id uuid)
RETURNS text
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE((
    SELECT CASE
      WHEN f.status = 'accepted' THEN 'friends'
      WHEN f.status = 'pending' AND f.requester_id = auth.uid() THEN 'pending_out'
      WHEN f.status = 'pending' AND f.addressee_id = auth.uid() THEN 'pending_in'
      WHEN f.status = 'declined' THEN 'declined'
      ELSE 'none'
    END
    FROM pingchat_friendships f
    WHERE (f.requester_id = auth.uid() AND f.addressee_id = p_user_id)
       OR (f.requester_id = p_user_id AND f.addressee_id = auth.uid())
    LIMIT 1
  ), 'none');
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_friendship_status(uuid) TO authenticated;

-- Delete a message for me only: hide it from my own view by recording
-- my id in deleted_by (the messages SELECT policy hides such rows).
CREATE OR REPLACE FUNCTION public.pingchat_delete_message_for_me(p_message_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_conv uuid;
BEGIN
  SELECT conversation_id INTO v_conv FROM public.pingchat_messages WHERE id = p_message_id;
  IF NOT FOUND THEN RETURN false; END IF;
  IF NOT public.pingchat_is_conversation_participant(v_conv) THEN
    RAISE EXCEPTION 'not a participant of this conversation';
  END IF;
  UPDATE public.pingchat_messages
  SET deleted_by = array_append(deleted_by, auth.uid())
  WHERE id = p_message_id AND NOT (auth.uid() = ANY(deleted_by));
  RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_delete_message_for_me(uuid) TO authenticated;

-- Delete a message for everyone: only the sender may do this. The row is
-- removed entirely; returns the message's media_path (or null) so the
-- client can also remove a photo/voice file from Storage.
CREATE OR REPLACE FUNCTION public.pingchat_delete_message_for_everyone(p_message_id uuid)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_media text;
  v_sender uuid;
BEGIN
  SELECT media_path, sender_id INTO v_media, v_sender
  FROM public.pingchat_messages WHERE id = p_message_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'message not found'; END IF;
  IF v_sender <> auth.uid() THEN
    RAISE EXCEPTION 'only the sender can delete this message for everyone';
  END IF;
  DELETE FROM public.pingchat_messages WHERE id = p_message_id;
  RETURN v_media;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_delete_message_for_everyone(uuid) TO authenticated;

-- Clear a whole chat for me: hide every message of the conversation
-- from my own view.
CREATE OR REPLACE FUNCTION public.pingchat_clear_chat_for_me(p_conversation_id uuid)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_count integer;
BEGIN
  IF NOT public.pingchat_is_conversation_participant(p_conversation_id) THEN
    RAISE EXCEPTION 'not a participant of this conversation';
  END IF;
  UPDATE public.pingchat_messages
  SET deleted_by = array_append(deleted_by, auth.uid())
  WHERE conversation_id = p_conversation_id
    AND NOT (auth.uid() = ANY(deleted_by));
  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_clear_chat_for_me(uuid) TO authenticated;

-- View-once photo/voice: the RECIPIENT's full playback (voice) or
-- opening (photo) deletes the message from the database. Returns
-- true only when the message is a view-once voice or photo message,
-- consumed, and the caller is a participant. The client then removes
-- the media file from Storage as well.
CREATE OR REPLACE FUNCTION public.pingchat_consume_voice_message(p_message_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_kind text;
  v_once boolean;
  v_sender uuid;
  v_consumed timestamptz;
  v_conv uuid;
BEGIN
  SELECT kind, view_once, sender_id, consumed_at, conversation_id
  INTO v_kind, v_once, v_sender, v_consumed, v_conv
  FROM public.pingchat_messages WHERE id = p_message_id;
  IF NOT FOUND THEN RETURN false; END IF;
  IF v_kind NOT IN ('voice', 'photo') OR v_once IS NOT TRUE THEN RETURN false; END IF;
  IF v_sender = auth.uid() THEN RETURN false; END IF;
  IF v_consumed IS NOT NULL THEN RETURN false; END IF;
  IF NOT public.pingchat_is_conversation_participant(v_conv) THEN RETURN false; END IF;
  DELETE FROM public.pingchat_messages WHERE id = p_message_id;
  RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_consume_voice_message(uuid) TO authenticated;

-- ============================================================
-- 4. TRIGGERS
-- ============================================================

-- Keep updated_at fresh on profiles and friendships.
CREATE OR REPLACE FUNCTION public.pingchat_set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS pingchat_profiles_updated_at ON public.pingchat_profiles;
CREATE TRIGGER pingchat_profiles_updated_at
  BEFORE UPDATE ON public.pingchat_profiles
  FOR EACH ROW EXECUTE FUNCTION public.pingchat_set_updated_at();
DROP TRIGGER IF EXISTS pingchat_friendships_updated_at ON public.pingchat_friendships;
CREATE TRIGGER pingchat_friendships_updated_at
  BEFORE UPDATE ON public.pingchat_friendships
  FOR EACH ROW EXECUTE FUNCTION public.pingchat_set_updated_at();

-- Keep conversations.last_message_at fresh on every new message.
CREATE OR REPLACE FUNCTION public.pingchat_on_message_created()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  UPDATE pingchat_conversations SET last_message_at = NEW.created_at WHERE id = NEW.conversation_id;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS pingchat_messages_last_message_at ON public.pingchat_messages;
CREATE TRIGGER pingchat_messages_last_message_at
  AFTER INSERT ON public.pingchat_messages
  FOR EACH ROW EXECUTE FUNCTION public.pingchat_on_message_created();

-- ============================================================
-- 5. REALTIME (guarded: safe if the table is already added)
-- ============================================================

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pingchat_messages'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.pingchat_messages';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pingchat_conversations'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.pingchat_conversations';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pingchat_conversation_participants'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.pingchat_conversation_participants';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pingchat_calls'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.pingchat_calls';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pingchat_friendships'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.pingchat_friendships';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pingchat_message_reactions'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.pingchat_message_reactions';
  END IF;
END $$;

-- ============================================================
-- 6. STORAGE BUCKETS — avatars, voice-messages, chat-photos
-- Public buckets; each user can only write inside their own folder
-- (<userId>/...). Re-running is safe (ON CONFLICT ... DO UPDATE).
-- ============================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('avatars', 'avatars', true, 2097152,
        ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "pingchat_avatars_public_read" ON storage.objects;
CREATE POLICY "pingchat_avatars_public_read" ON storage.objects
  FOR SELECT TO public USING (bucket_id = 'avatars');
DROP POLICY IF EXISTS "pingchat_avatars_insert_own" ON storage.objects;
CREATE POLICY "pingchat_avatars_insert_own" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text
  );
DROP POLICY IF EXISTS "pingchat_avatars_update_own" ON storage.objects;
CREATE POLICY "pingchat_avatars_update_own" ON storage.objects
  FOR UPDATE TO authenticated
  USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text)
  WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);
DROP POLICY IF EXISTS "pingchat_avatars_delete_own" ON storage.objects;
CREATE POLICY "pingchat_avatars_delete_own" ON storage.objects
  FOR DELETE TO authenticated USING (
    bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text
  );

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('voice-messages', 'voice-messages', true, 5242880,
        ARRAY['audio/webm', 'audio/mp4', 'audio/mpeg', 'audio/ogg', 'audio/wav', 'audio/x-m4a'])
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "pingchat_voice_public_read" ON storage.objects;
CREATE POLICY "pingchat_voice_public_read" ON storage.objects
  FOR SELECT TO public USING (bucket_id = 'voice-messages');
DROP POLICY IF EXISTS "pingchat_voice_insert_own" ON storage.objects;
CREATE POLICY "pingchat_voice_insert_own" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'voice-messages' AND (storage.foldername(name))[1] = auth.uid()::text
  );
DROP POLICY IF EXISTS "pingchat_voice_update_own" ON storage.objects;
CREATE POLICY "pingchat_voice_update_own" ON storage.objects
  FOR UPDATE TO authenticated
  USING (bucket_id = 'voice-messages' AND (storage.foldername(name))[1] = auth.uid()::text)
  WITH CHECK (bucket_id = 'voice-messages' AND (storage.foldername(name))[1] = auth.uid()::text);
DROP POLICY IF EXISTS "pingchat_voice_delete_own" ON storage.objects;
CREATE POLICY "pingchat_voice_delete_own" ON storage.objects
  FOR DELETE TO authenticated USING (
    bucket_id = 'voice-messages' AND (storage.foldername(name))[1] = auth.uid()::text
  );

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('chat-photos', 'chat-photos', true, 10485760,
        ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "pingchat_photo_public_read" ON storage.objects;
CREATE POLICY "pingchat_photo_public_read" ON storage.objects
  FOR SELECT TO public USING (bucket_id = 'chat-photos');
DROP POLICY IF EXISTS "pingchat_photo_insert_own" ON storage.objects;
CREATE POLICY "pingchat_photo_insert_own" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'chat-photos' AND (storage.foldername(name))[1] = auth.uid()::text
  );
DROP POLICY IF EXISTS "pingchat_photo_update_own" ON storage.objects;
CREATE POLICY "pingchat_photo_update_own" ON storage.objects
  FOR UPDATE TO authenticated
  USING (bucket_id = 'chat-photos' AND (storage.foldername(name))[1] = auth.uid()::text)
  WITH CHECK (bucket_id = 'chat-photos' AND (storage.foldername(name))[1] = auth.uid()::text);
DROP POLICY IF EXISTS "pingchat_photo_delete_own" ON storage.objects;
CREATE POLICY "pingchat_photo_delete_own" ON storage.objects
  FOR DELETE TO authenticated USING (
    bucket_id = 'chat-photos' AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- ============================================================
-- DONE. Verify in the dashboard:
--   Database > Tables: 9 new pingchat_* tables (plus the exam app's 6).
--   Authentication > Policies: each table shows its policies above.
--   Storage: 3 buckets (avatars, voice-messages, chat-photos).
--   Database > Replication: the 6 pingchat_* tables are listed.
-- The app (v3.0.0) connects with the publishable key already in
-- PushSender.kt and the project URL https://tjsalnhtuqqigyhvgpnb.supabase.co
-- ============================================================
