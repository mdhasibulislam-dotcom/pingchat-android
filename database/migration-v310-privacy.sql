-- ============================================================
-- migration-v310-privacy.sql — Privacy & Security (v3.1.0, worker A)
-- Run once in the Supabase SQL Editor (project tjsalnhtuqqigyhvgpnb).
-- Requires database/schema-supabase-v3.sql to have been run first
-- (uses pingchat_set_updated_at(), pingchat_profiles, pingchat_blocks,
-- pingchat_friendships, pingchat_conversations, pingchat_messages).
-- IDEMPOTENT — safe to run again. Additive only: no MCQ app table
-- (chapters, exam_answers, exam_results, profiles, questions,
-- subjects) is touched, and no v3 table is dropped or rebuilt.
--
-- WHAT THIS CREATES:
--   1. pingchat_privacy_settings — one row per user, all 11 v3.1.0
--      privacy settings + defaults (see the table comment below).
--   2. pingchat_devices — signed-in devices (remote logout support).
--   3. pingchat_conversations.auto_delete — per-chat auto-delete timer
--      (Off / 24h / 7d / 30d); the per-chat value wins over the user's
--      global auto_delete_default in pingchat_privacy_settings.
--   4. pingchat_get_privacy_visibility(uuid) — SECURITY DEFINER RPC
--      returning ONLY the six "who can …" settings of another user,
--      so the app can enforce them without reading that user's
--      auto-delete / inactive-account preferences.
--   5. pingchat_cleanup_auto_delete() — deletes messages older than
--      their conversation's timer. Called by pg_cron every night.
--   6. pingchat_cleanup_inactive_accounts() — deletes auth.users rows
--      (cascades to every pingchat_* row) for users inactive longer
--      than their own "Delete my account if away" setting.
--      REVIEW BEFORE ENABLING: the deletion is permanent. It only
--      counts a user inactive when their profile is offline AND
--      last_seen (or created_at) is older than their threshold.
--
-- pg_cron: enable it FIRST in Dashboard > Database > Extensions.
-- The DO block at the end schedules both jobs ONLY when the
-- extension exists; otherwise it raises a NOTICE and you run the
-- two cron.schedule() calls manually after enabling it.
-- NOTE: pg_cron runs in UTC — 03:00 UTC is 09:00 Dhaka.
--
-- STORAGE NOTE: cleanup deletes message ROWS. A deleted photo/voice
-- message's file in the chat-photos / voice-messages bucket is left
-- behind (same as the existing delete-for-everyone flow, where the
-- client removes the file). Orphaned files do not leak message text.
-- ============================================================

-- ============================================================
-- 1. pingchat_privacy_settings
--    last_seen_visibility: everybody / friends / nobody
--    messages_who:         everybody / friends
--    calls_who:            everybody / friends / nobody
--    voice_who:            everybody / friends / nobody
--    forwarded_who:        everybody / friends / nobody
--    bio_visibility:       everybody / friends / nobody
--    auto_delete_default:  off / 24h / 7d / 30d (default for new chats)
--    inactive_delete_after: 3mo / 6mo / 12mo / 24mo (default 12mo)
-- ============================================================

CREATE TABLE IF NOT EXISTS public.pingchat_privacy_settings (
  user_id uuid PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
  last_seen_visibility text NOT NULL DEFAULT 'everybody'
    CHECK (last_seen_visibility IN ('everybody', 'friends', 'nobody')),
  messages_who text NOT NULL DEFAULT 'everybody'
    CHECK (messages_who IN ('everybody', 'friends')),
  calls_who text NOT NULL DEFAULT 'everybody'
    CHECK (calls_who IN ('everybody', 'friends', 'nobody')),
  voice_who text NOT NULL DEFAULT 'everybody'
    CHECK (voice_who IN ('everybody', 'friends', 'nobody')),
  forwarded_who text NOT NULL DEFAULT 'everybody'
    CHECK (forwarded_who IN ('everybody', 'friends', 'nobody')),
  bio_visibility text NOT NULL DEFAULT 'everybody'
    CHECK (bio_visibility IN ('everybody', 'friends', 'nobody')),
  auto_delete_default text NOT NULL DEFAULT 'off'
    CHECK (auto_delete_default IN ('off', '24h', '7d', '30d')),
  inactive_delete_after text NOT NULL DEFAULT '12mo'
    CHECK (inactive_delete_after IN ('3mo', '6mo', '12mo', '24mo')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.pingchat_privacy_settings ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_privacy_settings TO authenticated;

-- Self: full read/write of my own row.
DROP POLICY IF EXISTS "pingchat_privacy_settings_select_self" ON public.pingchat_privacy_settings;
CREATE POLICY "pingchat_privacy_settings_select_self" ON public.pingchat_privacy_settings
  FOR SELECT TO authenticated USING (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_privacy_settings_insert_self" ON public.pingchat_privacy_settings;
CREATE POLICY "pingchat_privacy_settings_insert_self" ON public.pingchat_privacy_settings
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_privacy_settings_update_self" ON public.pingchat_privacy_settings;
CREATE POLICY "pingchat_privacy_settings_update_self" ON public.pingchat_privacy_settings
  FOR UPDATE TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_privacy_settings_delete_self" ON public.pingchat_privacy_settings;
CREATE POLICY "pingchat_privacy_settings_delete_self" ON public.pingchat_privacy_settings
  FOR DELETE TO authenticated USING (user_id = auth.uid());

-- Friends: may read the row (the app needs the peer's visibility
-- settings to enforce them, e.g. "can I call this person?").
-- Non-friends get nothing from this table directly; they use the
-- pingchat_get_privacy_visibility RPC instead (visibility only).
DROP POLICY IF EXISTS "pingchat_privacy_settings_select_friend" ON public.pingchat_privacy_settings;
CREATE POLICY "pingchat_privacy_settings_select_friend" ON public.pingchat_privacy_settings
  FOR SELECT TO authenticated USING (
    EXISTS (
      SELECT 1 FROM public.pingchat_friendships f
      WHERE f.status = 'accepted'
        AND ((f.requester_id = auth.uid() AND f.addressee_id = pingchat_privacy_settings.user_id)
          OR (f.requester_id = pingchat_privacy_settings.user_id AND f.addressee_id = auth.uid()))
    )
  );

-- Keep updated_at fresh (trigger function comes from schema-supabase-v3.sql).
DROP TRIGGER IF EXISTS pingchat_privacy_settings_updated_at ON public.pingchat_privacy_settings;
CREATE TRIGGER pingchat_privacy_settings_updated_at
  BEFORE UPDATE ON public.pingchat_privacy_settings
  FOR EACH ROW EXECUTE FUNCTION public.pingchat_set_updated_at();

-- ============================================================
-- 2. pingchat_devices — signed-in devices for remote logout.
--    device_id is ANDROID_ID (stable per device for this app).
--    revoked = true means "this device must sign itself out";
--    the device checks the flag on start / foreground and signs
--    out locally (the client cannot revoke another device's JWT).
-- ============================================================

CREATE TABLE IF NOT EXISTS public.pingchat_devices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  device_id text NOT NULL,
  device_name text,
  last_seen timestamptz NOT NULL DEFAULT now(),
  revoked boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_pingchat_devices_user_last_seen
  ON public.pingchat_devices (user_id, last_seen DESC);

ALTER TABLE public.pingchat_devices ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_devices TO authenticated;

-- Only my own device rows, for every operation (a device row is
-- never visible to another user, not even a friend).
DROP POLICY IF EXISTS "pingchat_devices_select_own" ON public.pingchat_devices;
CREATE POLICY "pingchat_devices_select_own" ON public.pingchat_devices
  FOR SELECT TO authenticated USING (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_devices_insert_self" ON public.pingchat_devices;
CREATE POLICY "pingchat_devices_insert_self" ON public.pingchat_devices
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_devices_update_self" ON public.pingchat_devices;
CREATE POLICY "pingchat_devices_update_self" ON public.pingchat_devices
  FOR UPDATE TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_devices_delete_self" ON public.pingchat_devices;
CREATE POLICY "pingchat_devices_delete_self" ON public.pingchat_devices
  FOR DELETE TO authenticated USING (user_id = auth.uid());

-- ============================================================
-- 3. Per-chat auto-delete timer on pingchat_conversations.
--    'off' (default) = keep messages; otherwise the cleanup job
--    deletes messages older than the timer.
-- ============================================================

ALTER TABLE public.pingchat_conversations
  ADD COLUMN IF NOT EXISTS auto_delete text NOT NULL DEFAULT 'off'
    CHECK (auto_delete IN ('off', '24h', '7d', '30d'));

-- ============================================================
-- 4. RPC: visibility settings of another user (SECURITY DEFINER).
--    Returns ONLY the six "who can …" values as JSON, or NULL when
--    the user has no settings row yet (the app then uses defaults).
--    Any signed-in user may call it — knowing that someone set
--    "calls: friends only" does not leak their messages or bio;
--    the value itself is what the caller's client must enforce.
-- ============================================================

DROP FUNCTION IF EXISTS public.pingchat_get_privacy_visibility(uuid);
CREATE OR REPLACE FUNCTION public.pingchat_get_privacy_visibility(p_user_id uuid)
RETURNS jsonb
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT jsonb_build_object(
    'last_seen_visibility', s.last_seen_visibility,
    'messages_who', s.messages_who,
    'calls_who', s.calls_who,
    'voice_who', s.voice_who,
    'forwarded_who', s.forwarded_who,
    'bio_visibility', s.bio_visibility
  )
  FROM public.pingchat_privacy_settings s
  WHERE s.user_id = p_user_id;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_get_privacy_visibility(uuid) TO authenticated;

-- ============================================================
-- 5. Cleanup: auto-delete old messages per conversation timer.
--    Deletes rows only; Storage files are left for the client-side
--    cleanup (see the header note). Returns the rows deleted.
-- ============================================================

DROP FUNCTION IF EXISTS public.pingchat_cleanup_auto_delete();
CREATE OR REPLACE FUNCTION public.pingchat_cleanup_auto_delete()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_count integer := 0;
  v_deleted integer;
BEGIN
  DELETE FROM public.pingchat_messages m
  USING public.pingchat_conversations c
  WHERE m.conversation_id = c.id
    AND c.auto_delete = '24h'
    AND m.created_at < now() - interval '24 hours';
  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  v_count := v_count + v_deleted;

  DELETE FROM public.pingchat_messages m
  USING public.pingchat_conversations c
  WHERE m.conversation_id = c.id
    AND c.auto_delete = '7d'
    AND m.created_at < now() - interval '7 days';
  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  v_count := v_count + v_deleted;

  DELETE FROM public.pingchat_messages m
  USING public.pingchat_conversations c
  WHERE m.conversation_id = c.id
    AND c.auto_delete = '30d'
    AND m.created_at < now() - interval '30 days';
  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  v_count := v_count + v_deleted;

  RETURN v_count;
END;
$$;
-- Only the cron job (postgres) calls this; it is deliberately NOT
-- granted to authenticated users — any signed-in user could
-- otherwise trigger a mass delete of old messages on demand.

-- ============================================================
-- 6. Cleanup: delete accounts inactive longer than their own
--    "Delete my account if away" setting (default 12 months).
--    Inactivity = profile offline AND last_seen (or created_at when
--    never seen) older than the threshold. A user who is online
--    right now is never deleted. Deleting the auth.users row
--    cascades to pingchat_profiles and every pingchat_* row.
--    Returns the accounts deleted. Run weekly via pg_cron below.
-- ============================================================

DROP FUNCTION IF EXISTS public.pingchat_cleanup_inactive_accounts();
CREATE OR REPLACE FUNCTION public.pingchat_cleanup_inactive_accounts()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  r record;
  v_threshold timestamptz;
  v_count integer := 0;
  v_deleted integer;
BEGIN
  FOR r IN SELECT user_id, inactive_delete_after
           FROM public.pingchat_privacy_settings LOOP
    v_threshold := now() - CASE r.inactive_delete_after
      WHEN '3mo' THEN interval '3 months'
      WHEN '6mo' THEN interval '6 months'
      WHEN '24mo' THEN interval '24 months'
      ELSE interval '12 months'
    END;
    DELETE FROM auth.users u
    USING public.pingchat_profiles p
    WHERE u.id = r.user_id
      AND p.id = u.id
      AND p.online IS NOT TRUE
      AND COALESCE(p.last_seen, p.created_at) < v_threshold;
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    v_count := v_count + v_deleted;
  END LOOP;
  RETURN v_count;
END;
$$;
-- Only the cron job (postgres / service role) calls this; it is
-- deliberately NOT granted to authenticated users.
REVOKE EXECUTE ON FUNCTION public.pingchat_cleanup_inactive_accounts() FROM authenticated;

-- ============================================================
-- 7. pg_cron schedules (nightly auto-delete, weekly inactive).
--    Enable pg_cron first: Dashboard > Database > Extensions.
--    If it is not enabled yet this block only raises a NOTICE and
--    schedules nothing — run the two cron.schedule() calls by hand
--    after enabling it. Times are UTC (03:00 UTC = 09:00 Dhaka).
-- ============================================================

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
    PERFORM cron.schedule(
      'pingchat-auto-delete-cleanup',
      '0 3 * * *',
      'SELECT public.pingchat_cleanup_auto_delete()'
    );
    PERFORM cron.schedule(
      'pingchat-inactive-account-cleanup',
      '0 4 * * 0',
      'SELECT public.pingchat_cleanup_inactive_accounts()'
    );
  ELSE
    RAISE NOTICE 'pg_cron is not enabled — enable it in Dashboard > Database > Extensions, then run: SELECT cron.schedule(''pingchat-auto-delete-cleanup'', ''0 3 * * *'', ''SELECT public.pingchat_cleanup_auto_delete()''); SELECT cron.schedule(''pingchat-inactive-account-cleanup'', ''0 4 * * 0'', ''SELECT public.pingchat_cleanup_inactive_accounts()'');';
  END IF;
END $$;

-- ============================================================
-- DONE. Verify in the dashboard:
--   Database > Tables: pingchat_privacy_settings, pingchat_devices.
--   pingchat_conversations has the new auto_delete column.
--   Database > Functions: pingchat_get_privacy_visibility,
--   pingchat_cleanup_auto_delete, pingchat_cleanup_inactive_accounts.
--   Database > Extensions: pg_cron enabled; Database > Cron Jobs
--   (or SELECT * FROM cron.job) lists both schedules.
-- ============================================================
