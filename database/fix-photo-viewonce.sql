-- ============================================================
-- PingChat fix: view-once PHOTO messages + view-once voice
-- for the native Android app (and the web app).
-- Run this whole file ONCE in the Supabase SQL Editor
-- (Dashboard -> SQL Editor -> New query -> paste -> Run).
--
-- It does three things:
--   1. Widens the messages kind CHECK to allow 'photo'
--      (was 'text' / 'voice' only). Without it, sending a photo
--      fails with a check-constraint error.
--   2. Widens consume_voice_message so it also accepts kind='photo'
--      (same name and signature, so the web app keeps working).
--      The recipient opening a photo / finishing a voice message
--      deletes the row from the database — view-once.
--   3. Creates the 'chat-photos' storage bucket (public, 10 MB,
--      images only) with the same style of policies as the
--      'voice-messages' bucket: anyone can read, each user can only
--      write inside their own <userId>/ folder.
--
-- Safe to re-run: every statement is guarded
-- (DROP CONSTRAINT IF EXISTS / ON CONFLICT ... DO UPDATE /
-- DROP POLICY IF EXISTS / CREATE OR REPLACE).
-- ============================================================

-- ---- 1. Allow kind = 'photo' on messages ----
-- The CHECK was added with the kind column, so Postgres auto-named it
-- messages_kind_check. Drop it by name; the DO block below is a
-- fallback that drops any other CHECK on messages mentioning kind
-- (in case the constraint was created under a different name).
ALTER TABLE public.messages DROP CONSTRAINT IF EXISTS messages_kind_check;
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'public.messages'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%kind%'
  LOOP
    EXECUTE 'ALTER TABLE public.messages DROP CONSTRAINT ' || quote_ident(r.conname);
  END LOOP;
END $$;
ALTER TABLE public.messages
  ADD CONSTRAINT messages_kind_check CHECK (kind IN ('text', 'voice', 'photo'));

-- ---- 2. View-once consume: voice AND photo ----
-- Same name/signature as before; only the kind check widens.
-- Returns true only when the message is a view-once voice or photo
-- message, the caller is not the sender, it was not already
-- consumed, and the caller is a participant. The client then removes
-- the media file from Storage as well.
CREATE OR REPLACE FUNCTION public.consume_voice_message(p_message_id uuid)
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
  FROM public.messages WHERE id = p_message_id;
  IF NOT FOUND THEN RETURN false; END IF;
  IF v_kind NOT IN ('voice', 'photo') OR v_once IS NOT TRUE THEN RETURN false; END IF;
  IF v_sender = auth.uid() THEN RETURN false; END IF;
  IF v_consumed IS NOT NULL THEN RETURN false; END IF;
  IF NOT public.is_conversation_participant(v_conv) THEN RETURN false; END IF;
  DELETE FROM public.messages WHERE id = p_message_id;
  RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.consume_voice_message(uuid) TO authenticated;

-- ---- 3. Storage bucket 'chat-photos' + policies ----

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('chat-photos', 'chat-photos', true, 10485760,
        ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Anyone can read (view) a photo.
DROP POLICY IF EXISTS "photo_public_read" ON storage.objects;
CREATE POLICY "photo_public_read" ON storage.objects
  FOR SELECT TO public USING (bucket_id = 'chat-photos');
-- Only the owner can upload into their own folder.
DROP POLICY IF EXISTS "photo_insert_own" ON storage.objects;
CREATE POLICY "photo_insert_own" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'chat-photos' AND (storage.foldername(name))[1] = auth.uid()::text
  );
-- Only the owner can replace their own photo file.
DROP POLICY IF EXISTS "photo_update_own" ON storage.objects;
CREATE POLICY "photo_update_own" ON storage.objects
  FOR UPDATE TO authenticated
  USING (bucket_id = 'chat-photos' AND (storage.foldername(name))[1] = auth.uid()::text)
  WITH CHECK (bucket_id = 'chat-photos' AND (storage.foldername(name))[1] = auth.uid()::text);
-- Only the owner can delete their own photo file (view-once consume
-- and delete-for-everyone remove the file after deleting the row).
DROP POLICY IF EXISTS "photo_delete_own" ON storage.objects;
CREATE POLICY "photo_delete_own" ON storage.objects
  FOR DELETE TO authenticated USING (
    bucket_id = 'chat-photos' AND (storage.foldername(name))[1] = auth.uid()::text
  );
