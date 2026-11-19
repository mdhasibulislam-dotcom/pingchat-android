-- ============================================================
-- migration-call-history.sql — per-user call history (v3.0.2, item 5)
-- Run once in the Supabase SQL Editor (project tjsalnhtuqqigyhvgpnb).
--
-- Why a new table: pingchat_calls holds ONE shared row per call, with
-- one status for both sides — so it cannot carry a per-user delete,
-- and the two sides need different statuses for the same call
-- (caller "unanswered" vs callee "missed"). Each side therefore
-- writes its OWN history row when a call finishes for them, and
-- deleting a history row removes it for that user only — the other
-- user's row (and the shared pingchat_calls row) is untouched.
-- No change to schema-supabase-v3.sql; this file is additive only.
-- ============================================================

CREATE TABLE IF NOT EXISTS public.pingchat_call_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  call_id uuid REFERENCES public.pingchat_calls (id) ON DELETE SET NULL,
  peer_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  peer_name text,
  kind text NOT NULL DEFAULT 'audio' CHECK (kind IN ('audio', 'video')),
  direction text NOT NULL CHECK (direction IN ('incoming', 'outgoing')),
  status text NOT NULL CHECK (status IN ('ended', 'missed', 'declined', 'unanswered')),
  started_at timestamptz,
  ended_at timestamptz,
  duration_seconds integer,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pingchat_call_history_user_created
  ON public.pingchat_call_history (user_id, created_at DESC);

ALTER TABLE public.pingchat_call_history ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, DELETE ON public.pingchat_call_history TO authenticated;

--- Each user can only read / write / delete their own history rows.
DROP POLICY IF EXISTS "pingchat_call_history_select_own" ON public.pingchat_call_history;
CREATE POLICY "pingchat_call_history_select_own" ON public.pingchat_call_history
  FOR SELECT TO authenticated USING (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_call_history_insert_self" ON public.pingchat_call_history;
CREATE POLICY "pingchat_call_history_insert_self" ON public.pingchat_call_history
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_call_history_delete_self" ON public.pingchat_call_history;
CREATE POLICY "pingchat_call_history_delete_self" ON public.pingchat_call_history
  FOR DELETE TO authenticated USING (user_id = auth.uid());
