-- ============================================================
-- migration-v310-stories.sql — 12-hour stories (v3.1.0)
-- Run once in the Supabase SQL Editor (project tjsalnhtuqqigyhvgpnb,
-- "hasibulialam's Project"). IDEMPOTENT — safe to run again.
-- No change to schema-supabase-v3.sql; this file is additive only.
-- It touches NO MCQ app table and NO other pingchat_* table.
--
-- WHAT THIS CREATES:
--   - pingchat_stories table (user_id, media_url, media_path,
--     media_type 'photo' | 'video', created_at, expires_at)
--   - RLS: only the owner can insert/delete; friends can read; blocks
--     hide stories both ways; nobody else can read.
--   - Storage bucket 'stories' (public, 10 MB, 4 policies — same shape
--     as the 'chat-photos' bucket in schema-supabase-v3.sql §6).
--   - Realtime on pingchat_stories (new stories update the ring strip).
--   - pingchat_cleanup_expired_stories(): deletes expired rows AND
--     their Storage files. Scheduled with pg_cron every 15 minutes.
--
-- STORY LIFETIME — EXACTLY 12 HOURS, THEN FULLY DELETED:
--   expires_at = created_at + 12 hours. After that the row and the
--   Storage file are both removed — there is NO permanent save and NO
--   Story Highlight (the user removed Highlight on purpose). The app
--   ALSO filters expires_at > now() on every read, so an expired story
--   is never shown even before the next cron run.
--
-- ⚠️ pg_cron SETUP (do this FIRST, in the Supabase Dashboard):
--   Database > Extensions > enable "pg_cron". Then run this file (or
--   re-run it — §6 only creates the schedule when the extension is
--   present, so running it before enabling pg_cron is harmless: the
--   table/bucket still work, only the automatic cleanup waits until
--   pg_cron is enabled and the file is run again).
--   Verify afterwards: Database > Cron Jobs shows
--   "pingchat-cleanup-expired-stories" (every 15 minutes).
-- ============================================================

-- ============================================================
-- 1. TABLE
-- ============================================================

CREATE TABLE IF NOT EXISTS public.pingchat_stories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  media_url text NOT NULL,
  media_path text NOT NULL,
  media_type text NOT NULL DEFAULT 'photo'
    CHECK (media_type IN ('photo', 'video')),
  created_at timestamptz NOT NULL DEFAULT now(),
  -- Exactly 12 hours from creation. The app also sets this explicitly
  -- on insert (now + 12h); the default covers any other writer.
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '12 hours'),
  CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS idx_pingchat_stories_user_created
  ON public.pingchat_stories (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pingchat_stories_expires_at
  ON public.pingchat_stories (expires_at);

-- ============================================================
-- 2. ROW LEVEL SECURITY
-- ============================================================

ALTER TABLE public.pingchat_stories ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, DELETE ON public.pingchat_stories TO authenticated;

-- Friend-or-self helper (SECURITY DEFINER so the policy never recurses
-- into pingchat_friendships' own policies — same pattern as
-- pingchat_is_conversation_participant in schema-supabase-v3.sql).
CREATE OR REPLACE FUNCTION public.pingchat_is_friend_or_self(p_user_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT auth.uid() = p_user_id
    OR EXISTS (
      SELECT 1 FROM public.pingchat_friendships
      WHERE status = 'accepted'
        AND ((requester_id = auth.uid() AND addressee_id = p_user_id)
          OR (requester_id = p_user_id AND addressee_id = auth.uid()))
    );
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_is_friend_or_self(uuid) TO authenticated;

-- Read: me + my accepted friends — never anyone else, and never across
-- a block in either direction.
DROP POLICY IF EXISTS "pingchat_stories_select_friend" ON public.pingchat_stories;
CREATE POLICY "pingchat_stories_select_friend" ON public.pingchat_stories
  FOR SELECT TO authenticated USING (
    public.pingchat_is_friend_or_self(pingchat_stories.user_id)
    AND NOT EXISTS (
      SELECT 1 FROM public.pingchat_blocks b
      WHERE (b.blocker_id = auth.uid() AND b.blocked_id = pingchat_stories.user_id)
         OR (b.blocker_id = pingchat_stories.user_id AND b.blocked_id = auth.uid())
    )
  );

-- Insert: only my own stories (media_type is already CHECK-constrained).
DROP POLICY IF EXISTS "pingchat_stories_insert_self" ON public.pingchat_stories;
CREATE POLICY "pingchat_stories_insert_self" ON public.pingchat_stories
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

-- Delete: only my own stories (the viewer offers Delete on my stories).
DROP POLICY IF EXISTS "pingchat_stories_delete_self" ON public.pingchat_stories;
CREATE POLICY "pingchat_stories_delete_self" ON public.pingchat_stories
  FOR DELETE TO authenticated USING (user_id = auth.uid());

-- No UPDATE policy on purpose: a story is immutable — it is only
-- created, read, and deleted (by its owner, or by expiry cleanup).

-- ============================================================
-- 3. REALTIME (guarded: safe if the table is already added)
-- ============================================================

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pingchat_stories'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.pingchat_stories';
  END IF;
END $$;

-- ============================================================
-- 4. STORAGE BUCKET 'stories' — public, 10 MB, own-folder writes
-- (same 4-policy shape as 'chat-photos' in schema-supabase-v3.sql §6)
-- ============================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('stories', 'stories', true, 10485760,
        ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif',
              'video/mp4', 'video/quicktime', 'video/webm', 'video/3gpp'])
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "pingchat_stories_public_read" ON storage.objects;
CREATE POLICY "pingchat_stories_public_read" ON storage.objects
  FOR SELECT TO public USING (bucket_id = 'stories');
DROP POLICY IF EXISTS "pingchat_stories_insert_own" ON storage.objects;
CREATE POLICY "pingchat_stories_insert_own" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'stories' AND (storage.foldername(name))[1] = auth.uid()::text
  );
DROP POLICY IF EXISTS "pingchat_stories_update_own" ON storage.objects;
CREATE POLICY "pingchat_stories_update_own" ON storage.objects
  FOR UPDATE TO authenticated
  USING (bucket_id = 'stories' AND (storage.foldername(name))[1] = auth.uid()::text)
  WITH CHECK (bucket_id = 'stories' AND (storage.foldername(name))[1] = auth.uid()::text);
DROP POLICY IF EXISTS "pingchat_stories_delete_own" ON storage.objects;
CREATE POLICY "pingchat_stories_delete_own" ON storage.objects
  FOR DELETE TO authenticated USING (
    bucket_id = 'stories' AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- ============================================================
-- 5. EXPIRY CLEANUP — deletes expired rows AND their Storage files
-- ============================================================

-- Runs as the function owner (postgres) via SECURITY DEFINER, so it
-- can remove Storage files regardless of the caller's RLS. Returns
-- how many expired stories were removed.
CREATE OR REPLACE FUNCTION public.pingchat_cleanup_expired_stories()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, storage
AS $$
DECLARE
  r record;
  v_count integer := 0;
BEGIN
  FOR r IN
    SELECT id, media_path FROM public.pingchat_stories
    WHERE expires_at <= now()
  LOOP
    -- Storage file first, then the row (the file is the orphan risk).
    DELETE FROM storage.objects
    WHERE bucket_id = 'stories' AND name = r.media_path;
    DELETE FROM public.pingchat_stories WHERE id = r.id;
    v_count := v_count + 1;
  END LOOP;
  RETURN v_count;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_cleanup_expired_stories() TO authenticated;

-- Manual check (optional): SELECT public.pingchat_cleanup_expired_stories();

-- ============================================================
-- 6. pg_cron SCHEDULE — every 15 minutes
-- Requires the pg_cron extension (Dashboard > Database > Extensions).
-- Guarded: if pg_cron is not enabled yet, this block does nothing and
-- the file can simply be re-run after enabling it.
-- ============================================================

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
    IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'pingchat-cleanup-expired-stories') THEN
      PERFORM cron.unschedule('pingchat-cleanup-expired-stories');
    END IF;
    PERFORM cron.schedule(
      'pingchat-cleanup-expired-stories',
      '*/15 * * * *',
      'SELECT public.pingchat_cleanup_expired_stories();'
    );
  ELSE
    RAISE NOTICE 'pg_cron is not enabled — enable it in Dashboard > Database > Extensions, then re-run this file to schedule the cleanup.';
  END IF;
END $$;

-- ============================================================
-- DONE. Verify in the dashboard:
--   Database > Tables: pingchat_stories exists (10th pingchat_* table).
--   Storage: 'stories' bucket, public, 10 MB.
--   Database > Replication: pingchat_stories is listed (Realtime on).
--   Database > Cron Jobs: pingchat-cleanup-expired-stories, */15 * * * *.
--   (If the cron job is missing, pg_cron was not enabled — see §6.)
-- ============================================================
