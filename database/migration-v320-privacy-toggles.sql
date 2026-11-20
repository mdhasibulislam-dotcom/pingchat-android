-- ============================================================
-- migration-v320-privacy-toggles.sql — Privacy toggles (v3.2.0)
-- Run once in the Supabase SQL Editor (project tjsalnhtuqqigyhvgpnb).
-- Requires database/migration-v310-privacy.sql to have been run first
-- (adds columns to pingchat_privacy_settings, created there).
-- IDEMPOTENT — safe to run again. Additive only: no table is
-- dropped or rebuilt, and no MCQ app table is touched.
--
-- WHAT THIS ADDS (all three are needed by the v3.2.0 Privacy &
-- Security redesign — the mockup's Read Receipts / Typing Indicator
-- / Who Can Add You rows have no column until this runs):
--   1. pingchat_privacy_settings.read_receipts    boolean, default true
--      — whether others see that I read their messages.
--   2. pingchat_privacy_settings.typing_indicator boolean, default true
--      — whether others see "typing…" while I type.
--   3. pingchat_privacy_settings.add_who          text, default 'everybody'
--      — who may send me a friend request ('everybody' / 'friends' /
--      'nobody'), the v3.2.0 "Who Can Add You" setting.
--   4. pingchat_get_privacy_visibility(uuid) is recreated to also
--      return 'add_who', so the app can enforce it for other users
--      (PrivacyRepository.canSendFriendRequestTo).
--
-- No RLS change is needed: the v3.1.0 self/friend policies on
-- pingchat_privacy_settings already cover the whole row, and the
-- RPC is SECURITY DEFINER as before.
--
-- NOTE: until this migration runs, saving any privacy setting from
-- the v3.2.0 app fails (PostgREST rejects the unknown columns on
-- upsert) and the screen shows "Could not save that setting."
-- The read-receipts / typing-indicator toggles are persisted by
-- this migration but are NOT yet enforced anywhere: no call site
-- gates read ticks or the typing broadcast on them yet (that is
-- ChatViewModel work, not this migration).
-- ============================================================

ALTER TABLE public.pingchat_privacy_settings
  ADD COLUMN IF NOT EXISTS read_receipts boolean NOT NULL DEFAULT true;

ALTER TABLE public.pingchat_privacy_settings
  ADD COLUMN IF NOT EXISTS typing_indicator boolean NOT NULL DEFAULT true;

ALTER TABLE public.pingchat_privacy_settings
  ADD COLUMN IF NOT EXISTS add_who text NOT NULL DEFAULT 'everybody'
    CHECK (add_who IN ('everybody', 'friends', 'nobody'));

-- Recreate the visibility RPC with the new 'add_who' key (same
-- SECURITY DEFINER shape as v3.1.0, one key added).
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
    'bio_visibility', s.bio_visibility,
    'add_who', s.add_who
  )
  FROM public.pingchat_privacy_settings s
  WHERE s.user_id = p_user_id;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_get_privacy_visibility(uuid) TO authenticated;

-- ============================================================
-- DONE. Verify in the dashboard:
--   pingchat_privacy_settings has read_receipts, typing_indicator,
--   add_who columns (all with defaults, so old rows still read).
-- ============================================================
