-- PingChat Android v3 — view-once messages with a Keep option.
-- Run once in the Supabase Dashboard > SQL Editor for project
-- tjsalnhtuqqigyhvgpnb ("hasibulialam's Project"). IDEMPOTENT — safe to
-- run again.
--
-- WHY THIS EXISTS: view-once photo/voice messages are deleted after the
-- recipient's first full view/listen. The default stays exactly that,
-- but the recipient can now tap Keep while viewing/listening and the
-- message then stays in the chat like a normal message (it is NOT saved
-- to the device — it just is not deleted).
--
-- WHAT THIS DOES:
-- 1. Adds pingchat_messages.kept (boolean, default false).
-- 2. Adds an RLS UPDATE policy so ONLY the recipient of a view-once
--    message can set kept = true on it (the sender, and anyone else,
--    still cannot update message rows at all — there was previously NO
--    UPDATE policy on pingchat_messages, so a plain PostgREST update
--    would have been refused). The WITH CHECK side requires the new row
--    to still be a view-once message of which the caller is the
--    recipient, with kept = true.
-- 3. Replaces pingchat_consume_voice_message with the same function
--    (same signature, same SECURITY DEFINER, same behaviour) PLUS a
--    guard: it returns false and deletes nothing when the row is
--    already kept. This is the server-side backstop for the client's
--    kept check in ChatViewModel.consumeViewOnce.
ALTER TABLE public.pingchat_messages
  ADD COLUMN IF NOT EXISTS kept boolean NOT NULL DEFAULT false;

DROP POLICY IF EXISTS "pingchat_messages_update_keep" ON public.pingchat_messages;
CREATE POLICY "pingchat_messages_update_keep" ON public.pingchat_messages
  FOR UPDATE TO authenticated
  USING (
    public.pingchat_is_conversation_participant(pingchat_messages.conversation_id)
    AND pingchat_messages.sender_id <> auth.uid()
    AND pingchat_messages.view_once IS TRUE
  )
  WITH CHECK (
    public.pingchat_is_conversation_participant(pingchat_messages.conversation_id)
    AND pingchat_messages.sender_id <> auth.uid()
    AND pingchat_messages.view_once IS TRUE
    AND pingchat_messages.kept IS TRUE
  );

-- View-once photo/voice: the RECIPIENT's full playback (voice) or
-- closing the viewer (photo) deletes the message from the database —
-- UNLESS the recipient kept it, in which case nothing is deleted.
-- Returns true only when the message is a view-once voice or photo
-- message, consumed, and the caller is a participant. The client then
-- removes the media file from Storage as well.
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
  v_kept boolean;
BEGIN
  SELECT kind, view_once, sender_id, consumed_at, conversation_id, kept
  INTO v_kind, v_once, v_sender, v_consumed, v_conv, v_kept
  FROM public.pingchat_messages WHERE id = p_message_id;
  IF NOT FOUND THEN RETURN false; END IF;
  IF v_kind NOT IN ('voice', 'photo') OR v_once IS NOT TRUE THEN RETURN false; END IF;
  IF v_sender = auth.uid() THEN RETURN false; END IF;
  IF v_consumed IS NOT NULL THEN RETURN false; END IF;
  IF v_kept IS TRUE THEN RETURN false; END IF;
  IF NOT public.pingchat_is_conversation_participant(v_conv) THEN RETURN false; END IF;
  DELETE FROM public.pingchat_messages WHERE id = p_message_id;
  RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_consume_voice_message(uuid) TO authenticated;
