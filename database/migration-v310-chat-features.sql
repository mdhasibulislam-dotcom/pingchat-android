-- PingChat Android v3.1.0 — chat features: edit, forward, pin, mute, archive.
-- Run in the Supabase Dashboard > SQL Editor for project
-- tjsalnhtuqqigyhvgpnb ("hasibulialam's Project"). IDEMPOTENT — safe to
-- run again. Run AFTER migration-v310-groups.sql (either order works —
-- the two files touch different columns, but groups.sql is the v3.1.0
-- base for group conversations).
--
-- WHAT THIS DOES:
-- 1. pingchat_messages: edited_at (edit label), forwarded_from (the
--    original sender of a forwarded message — "Forwarded" label),
--    is_pinned + pinned_at + pinned_by (one pinned message per chat,
--    visible to every participant).
-- 2. pingchat_conversation_participants (per-user, per-chat flags — the
--    v3 participants RLS already lets a user update ONLY their own row,
--    so these need no new policy): is_pinned + pinned_at (chat pin —
--    pinned chats sort first), is_muted + muted_at (muted chats send no
--    notification), is_archived + archived_at (archived chats hide from
--    the list; a new message does NOT unarchive them).
-- 3. RPCs (SECURITY DEFINER, same pattern as the v3 delete RPCs):
--    pingchat_edit_message, pingchat_forward_message,
--    pingchat_set_message_pinned. Edits and pins deliberately go through
--    RPCs, NOT a general messages UPDATE policy: the v3 table has no
--    UPDATE policy except "keep", and adding a permissive participant
--    UPDATE policy for pinning would also let any participant rewrite
--    anyone else's message body (Postgres ORs permissive policies).
--    - Edit: sender only, text messages only, sets body + edited_at.
--    - Forward: copies a message into another conversation the caller
--      belongs to, as a new row with forwarded_from = original sender.
--      View-once messages cannot be forwarded. Blocked users are
--      refused the same way the v3 insert policy refuses them.
--    - Pin: any participant pins/unpins; pinning a message unpins the
--      previous pinned message in that conversation (one pinned message
--      per chat, WhatsApp-style).
-- 4. No document/file sending, no video messages, no channels/polls —
--    nothing else is added here.

-- ============================================================
-- 1. COLUMNS
-- ============================================================

ALTER TABLE public.pingchat_messages
  ADD COLUMN IF NOT EXISTS edited_at timestamptz,
  ADD COLUMN IF NOT EXISTS forwarded_from uuid REFERENCES auth.users (id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS is_pinned boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS pinned_at timestamptz,
  ADD COLUMN IF NOT EXISTS pinned_by uuid REFERENCES auth.users (id) ON DELETE SET NULL;

ALTER TABLE public.pingchat_conversation_participants
  ADD COLUMN IF NOT EXISTS is_pinned boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS pinned_at timestamptz,
  ADD COLUMN IF NOT EXISTS is_muted boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS muted_at timestamptz,
  ADD COLUMN IF NOT EXISTS is_archived boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS archived_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_pingchat_messages_pinned
  ON public.pingchat_messages (conversation_id, is_pinned)
  WHERE is_pinned IS TRUE;

-- ============================================================
-- 2. RPCs
-- ============================================================

-- Edit my own TEXT message. Returns true when the row was updated.
-- Only the sender may edit, only kind = 'text' (photo/voice captions
-- are not editable), and the new body must not be blank. Sets edited_at
-- so the app can show the "edited" label.
CREATE OR REPLACE FUNCTION public.pingchat_edit_message(
  p_message_id uuid,
  p_body text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_sender uuid;
  v_kind text;
  v_conv uuid;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF p_body IS NULL OR btrim(p_body) = '' THEN
    RAISE EXCEPTION 'message cannot be empty';
  END IF;
  SELECT sender_id, kind, conversation_id INTO v_sender, v_kind, v_conv
  FROM public.pingchat_messages WHERE id = p_message_id;
  IF NOT FOUND THEN RETURN false; END IF;
  IF v_sender <> auth.uid() THEN
    RAISE EXCEPTION 'only the sender can edit this message';
  END IF;
  IF v_kind <> 'text' THEN
    RAISE EXCEPTION 'only text messages can be edited';
  END IF;
  IF NOT public.pingchat_is_conversation_participant(v_conv) THEN
    RAISE EXCEPTION 'not a participant of this conversation';
  END IF;
  UPDATE public.pingchat_messages
  SET body = btrim(p_body), edited_at = now()
  WHERE id = p_message_id;
  RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_edit_message(uuid, text) TO authenticated;

-- Forward a message into another conversation I belong to. Copies body /
-- kind / media, marks forwarded_from = the ORIGINAL sender, and refreshes
-- the target conversation's preview (same columns the app updates on a
-- normal send). Returns the new message id. View-once messages cannot be
-- forwarded; neither can messages from a conversation I am not in, nor
-- into a conversation I am not in, nor across a block (either direction,
-- checked against the target's participants like the v3 insert policy).
CREATE OR REPLACE FUNCTION public.pingchat_forward_message(
  p_message_id uuid,
  p_target_conversation_id uuid
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_src public.pingchat_messages%ROWTYPE;
  v_new_id uuid;
  v_preview text;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  SELECT * INTO v_src FROM public.pingchat_messages WHERE id = p_message_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'message not found'; END IF;
  IF v_src.view_once IS TRUE THEN
    RAISE EXCEPTION 'view-once messages cannot be forwarded';
  END IF;
  IF NOT public.pingchat_is_conversation_participant(v_src.conversation_id) THEN
    RAISE EXCEPTION 'not a participant of the source conversation';
  END IF;
  IF NOT public.pingchat_is_conversation_participant(p_target_conversation_id) THEN
    RAISE EXCEPTION 'not a participant of the target conversation';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.pingchat_blocks b
    JOIN public.pingchat_conversation_participants p ON p.user_id = b.blocker_id
    WHERE p.conversation_id = p_target_conversation_id
      AND p.user_id <> auth.uid()
      AND b.blocked_id = auth.uid()
  ) OR EXISTS (
    SELECT 1 FROM public.pingchat_blocks b
    JOIN public.pingchat_conversation_participants p ON p.user_id = b.blocked_id
    WHERE p.conversation_id = p_target_conversation_id
      AND p.user_id <> auth.uid()
      AND b.blocker_id = auth.uid()
  ) THEN
    RAISE EXCEPTION 'cannot forward to this chat';
  END IF;

  INSERT INTO public.pingchat_messages
    (conversation_id, sender_id, body, kind, media_url, media_path, forwarded_from)
  VALUES
    (p_target_conversation_id, auth.uid(), v_src.body, v_src.kind,
     v_src.media_url, v_src.media_path, v_src.sender_id)
  RETURNING id INTO v_new_id;

  v_preview := CASE v_src.kind
    WHEN 'photo' THEN 'Photo'
    WHEN 'voice' THEN 'Voice message'
    ELSE v_src.body
  END;
  UPDATE public.pingchat_conversations
  SET last_message = v_preview,
      last_message_at = now(),
      last_message_kind = v_src.kind,
      last_message_id = v_new_id,
      last_sender_id = auth.uid()
  WHERE id = p_target_conversation_id;

  RETURN v_new_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_forward_message(uuid, uuid) TO authenticated;

-- Pin (p_pin = true) or unpin (p_pin = false) a message. Any participant
-- may do it; pinning unpins whatever was pinned before in that chat, so
-- there is at most one pinned message per conversation. Returns true
-- when the row was updated.
CREATE OR REPLACE FUNCTION public.pingchat_set_message_pinned(
  p_message_id uuid,
  p_pin boolean
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_conv uuid;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  SELECT conversation_id INTO v_conv FROM public.pingchat_messages WHERE id = p_message_id;
  IF NOT FOUND THEN RETURN false; END IF;
  IF NOT public.pingchat_is_conversation_participant(v_conv) THEN
    RAISE EXCEPTION 'not a participant of this conversation';
  END IF;
  IF p_pin THEN
    UPDATE public.pingchat_messages
    SET is_pinned = false, pinned_at = NULL, pinned_by = NULL
    WHERE conversation_id = v_conv AND is_pinned IS TRUE;
    UPDATE public.pingchat_messages
    SET is_pinned = true, pinned_at = now(), pinned_by = auth.uid()
    WHERE id = p_message_id;
  ELSE
    UPDATE public.pingchat_messages
    SET is_pinned = false, pinned_at = NULL, pinned_by = NULL
    WHERE id = p_message_id;
  END IF;
  RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_set_message_pinned(uuid, boolean) TO authenticated;

-- ============================================================
-- DONE. Verify in the dashboard:
--   pingchat_messages has edited_at / forwarded_from / is_pinned /
--   pinned_at / pinned_by; pingchat_conversation_participants has
--   is_pinned / pinned_at / is_muted / muted_at / is_archived /
--   archived_at. Database > Functions lists pingchat_edit_message,
--   pingchat_forward_message, pingchat_set_message_pinned.
-- App-side notes (v3.1.0): chat pin/mute/archive are plain PostgREST
-- updates of the caller's OWN participant row (the v3 update-self RLS
-- policy already allows that); muted chats are also cached in the
-- app's Prefs so PingChatMessagingService can suppress their push
-- notifications on-device. A new message NEVER clears is_archived.
-- ============================================================
