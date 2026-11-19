-- PingChat Android v3.1.0 — media expiry: view-once is replaced by a 7-day rule.
-- Run once in the Supabase Dashboard > SQL Editor, project
-- tjsalnhtuqqigyhvgpnb ("hasibulialam's Project"). IDEMPOTENT — safe to
-- run again.
--
-- WHAT CHANGES (and why):
-- 1. View-once + Keep are gone from the app (v3.1.0). A photo/voice message
--    now stays in the chat like a normal message — it can be viewed and
--    listened to again and again — BUT the database deletes it permanently
--    7 days after it was sent (created_at): message row + Storage file.
--    TEXT messages are never touched by the cleanup.
-- 2. pingchat_cleanup_expired_media() (§1): deletes every photo/voice
--    message older than 7 days, and its file from storage.objects
--    (chat-photos / voice-messages bucket). SECURITY DEFINER, and EXECUTE
--    is revoked from PUBLIC — only the pg_cron job (which runs as the job
--    owner, postgres) may call it.
-- 3. pg_cron job 'pingchat-cleanup-expired-media' (§2), daily 03:00 UTC.
--    pg_cron must be enabled FIRST: Dashboard > Database > Extensions >
--    pg_cron (toggle it on). If it is not enabled yet, this file still
--    succeeds and logs a NOTICE — enable pg_cron and re-run this file
--    (or just the §2 block) to schedule the daily cleanup. Until the job
--    runs, the app itself hides expired media: a photo/voice message
--    older than 7 days renders as a "deleted" placeholder, never a
--    broken bubble.
-- 4. Drops the view-once leftovers the app no longer uses (§3):
--      - pingchat_messages.kept (+ its RLS UPDATE policy
--        "pingchat_messages_update_keep")
--      - pingchat_consume_voice_message(uuid)
--    The view_once / consumed_at COLUMNS are deliberately KEPT: the
--    already-installed v3.0.2 APK still sends view_once=true on insert,
--    so dropping the column would break photo/voice sending for users
--    who have not updated yet. v3.1.0 never reads or writes them; they
--    can be dropped in a later release once v3.0.2 is gone. (The old
--    client's Keep/consume calls degrade gracefully without the column
--    and function — both are wrapped in try/catch and simply return
--    false — so nothing crashes on an old install.)
--
-- WHAT THIS DOES NOT TOUCH: no MCQ app table, no other pingchat_* table,
-- no Storage bucket or policy, no Realtime setting, no push trigger.

-- ============================================================
-- §1. Cleanup function — photo/voice older than 7 days: row + file.
-- ============================================================

CREATE OR REPLACE FUNCTION public.pingchat_cleanup_expired_media()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row record;
  v_count integer := 0;
BEGIN
  FOR v_row IN
    SELECT id, kind, media_path
    FROM public.pingchat_messages
    WHERE kind IN ('voice', 'photo')
      AND created_at < now() - interval '7 days'
  LOOP
    DELETE FROM public.pingchat_messages WHERE id = v_row.id;
    -- The file lives in the bucket matching the message kind
    -- (photo -> chat-photos, voice -> voice-messages). Deleting the
    -- storage.objects row removes the file; the message row is
    -- already gone, so a missing file here is harmless.
    IF v_row.media_path IS NOT NULL THEN
      DELETE FROM storage.objects
      WHERE bucket_id = CASE v_row.kind WHEN 'photo' THEN 'chat-photos' ELSE 'voice-messages' END
        AND name = v_row.media_path;
    END IF;
    v_count := v_count + 1;
  END LOOP;
  RETURN v_count;
END;
$$;

-- Only the job owner (postgres, which runs the pg_cron job) may call it.
REVOKE ALL ON FUNCTION public.pingchat_cleanup_expired_media() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.pingchat_cleanup_expired_media() TO postgres;

-- Index so the daily scan does not read the whole messages table.
CREATE INDEX IF NOT EXISTS idx_pingchat_messages_media_expiry
  ON public.pingchat_messages (created_at) WHERE kind IN ('voice', 'photo');

-- ============================================================
-- §2. Daily schedule (pg_cron). Enable pg_cron first — see header.
-- ============================================================

DO $cleanup_schedule$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
    IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'pingchat-cleanup-expired-media') THEN
      PERFORM cron.unschedule('pingchat-cleanup-expired-media');
    END IF;
    PERFORM cron.schedule(
      'pingchat-cleanup-expired-media',
      '0 3 * * *',
      $$SELECT public.pingchat_cleanup_expired_media();$$
    );
    RAISE NOTICE 'pg_cron job pingchat-cleanup-expired-media scheduled (daily 03:00 UTC).';
  ELSE
    RAISE NOTICE 'pg_cron is NOT enabled — enable it in Dashboard > Database > Extensions, then re-run this file to schedule the daily cleanup.';
  END IF;
END
$cleanup_schedule$;

-- ============================================================
-- §3. Drop the view-once leftovers the app no longer uses.
-- ============================================================

DROP POLICY IF EXISTS "pingchat_messages_update_keep" ON public.pingchat_messages;
ALTER TABLE public.pingchat_messages DROP COLUMN IF EXISTS kept;
DROP FUNCTION IF EXISTS public.pingchat_consume_voice_message(uuid);

-- DONE. Verify in the dashboard:
--   Database > Tables > pingchat_messages: no 'kept' column.
--   Database > Functions: pingchat_cleanup_expired_media() exists.
--   Database > Extensions: pg_cron enabled; Database > Cron Jobs (or
--   SELECT * FROM cron.job): 'pingchat-cleanup-expired-media' listed.
--   Manual test (deletes nothing unless a photo/voice is 7+ days old):
--   SELECT public.pingchat_cleanup_expired_media();
