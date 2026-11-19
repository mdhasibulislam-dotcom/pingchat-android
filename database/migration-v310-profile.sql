-- ============================================================
-- migration-v310-profile.sql — public profile design (v3.1.0, worker F)
-- Run once in the Supabase SQL Editor (project tjsalnhtuqqigyhvgpnb).
-- IDEMPOTENT — safe to run again. Additive only: no existing
-- column/table is changed or dropped, and no MCQ app table is touched.
--
-- ADDS:
--   pingchat_profiles: cover_photo_url, city, education, status_line,
--     social_links (jsonb), theme_color, is_verified
--   pingchat_profile_views: who viewed a profile (one row per viewer
--     per profile; viewed_at refreshes on repeat views)
--   RPCs: pingchat_record_profile_view, pingchat_profile_view_count,
--     pingchat_mutual_friends
--   Storage bucket 'covers' (public, 10 MB, images only) for cover photos
--
-- DOES NOT DO:
--   - No global username search (profile screen shows @username only).
--   - No follower counts, media gallery, story highlights, profile
--     video, or pinned posts (all removed by Hasibul, 2026-11-19).
--   - No Two-Step / Passcode / Invites / Profile-Photos privacy setting.
-- ============================================================

-- ---------- 1. New profile columns ----------
ALTER TABLE public.pingchat_profiles
  ADD COLUMN IF NOT EXISTS cover_photo_url text;
ALTER TABLE public.pingchat_profiles
  ADD COLUMN IF NOT EXISTS city text;
ALTER TABLE public.pingchat_profiles
  ADD COLUMN IF NOT EXISTS education text;
ALTER TABLE public.pingchat_profiles
  ADD COLUMN IF NOT EXISTS status_line text;
ALTER TABLE public.pingchat_profiles
  ADD COLUMN IF NOT EXISTS social_links jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE public.pingchat_profiles
  ADD COLUMN IF NOT EXISTS theme_color text;
ALTER TABLE public.pingchat_profiles
  ADD COLUMN IF NOT EXISTS is_verified boolean NOT NULL DEFAULT false;

-- theme_color is a "#RRGGBB" hex string when set (validated client-side);
-- keep obviously-bad values out at the DB level too.
ALTER TABLE public.pingchat_profiles
  DROP CONSTRAINT IF EXISTS pingchat_profiles_theme_color_hex;
ALTER TABLE public.pingchat_profiles
  ADD CONSTRAINT pingchat_profiles_theme_color_hex
  CHECK (theme_color IS NULL OR theme_color ~ '^#[0-9A-Fa-f]{6}$');

-- ---------- 2. Profile views ----------
CREATE TABLE IF NOT EXISTS public.pingchat_profile_views (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  viewer_user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  viewed_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (profile_user_id, viewer_user_id),
  CHECK (profile_user_id <> viewer_user_id)
);

CREATE INDEX IF NOT EXISTS idx_pingchat_profile_views_profile_viewed
  ON public.pingchat_profile_views (profile_user_id, viewed_at DESC);

ALTER TABLE public.pingchat_profile_views ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE ON public.pingchat_profile_views TO authenticated;

-- The profile owner reads their own visitors; a viewer reads only the
-- rows they created (so they can see they already viewed). The visitor
-- COUNT for other viewers comes from the SECURITY DEFINER RPC below,
-- not from a direct select — viewer identities stay private.
DROP POLICY IF EXISTS "pingchat_profile_views_select_owner" ON public.pingchat_profile_views;
CREATE POLICY "pingchat_profile_views_select_owner" ON public.pingchat_profile_views
  FOR SELECT TO authenticated USING (
    profile_user_id = auth.uid() OR viewer_user_id = auth.uid()
  );
DROP POLICY IF EXISTS "pingchat_profile_views_insert_self" ON public.pingchat_profile_views;
CREATE POLICY "pingchat_profile_views_insert_self" ON public.pingchat_profile_views
  FOR INSERT TO authenticated WITH CHECK (viewer_user_id = auth.uid());
DROP POLICY IF EXISTS "pingchat_profile_views_update_self" ON public.pingchat_profile_views;
CREATE POLICY "pingchat_profile_views_update_self" ON public.pingchat_profile_views
  FOR UPDATE TO authenticated
  USING (viewer_user_id = auth.uid())
  WITH CHECK (viewer_user_id = auth.uid());

-- Record a view: one row per (profile, viewer); repeat views only refresh
-- viewed_at. Self-views are ignored (opening your own profile is not a
-- visit). SECURITY DEFINER so the upsert works under RLS.
CREATE OR REPLACE FUNCTION public.pingchat_record_profile_view(p_profile_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me uuid := auth.uid();
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF p_profile_user_id = v_me THEN RETURN; END IF;
  INSERT INTO public.pingchat_profile_views (profile_user_id, viewer_user_id)
  VALUES (p_profile_user_id, v_me)
  ON CONFLICT (profile_user_id, viewer_user_id)
  DO UPDATE SET viewed_at = now();
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_record_profile_view(uuid) TO authenticated;

-- Visitor count for a profile, visible to any signed-in user without
-- exposing WHO the viewers are (the raw rows stay owner-only, above).
CREATE OR REPLACE FUNCTION public.pingchat_profile_view_count(p_profile_user_id uuid)
RETURNS bigint
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT count(*) FROM public.pingchat_profile_views
  WHERE profile_user_id = p_profile_user_id;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_profile_view_count(uuid) TO authenticated;

-- ---------- 3. Mutual friends ----------
-- My accepted friends ∩ the other user's accepted friends, as full
-- profile rows. Must be an RPC: the friendships SELECT policy only lets
-- a user read rows they are a party to, so the client cannot compute the
-- other side's friend list itself.
CREATE OR REPLACE FUNCTION public.pingchat_mutual_friends(p_user_id uuid)
RETURNS SETOF public.pingchat_profiles
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  WITH my_friends AS (
    SELECT CASE WHEN requester_id = auth.uid() THEN addressee_id ELSE requester_id END AS friend_id
    FROM public.pingchat_friendships
    WHERE status = 'accepted'
      AND (requester_id = auth.uid() OR addressee_id = auth.uid())
  ),
  their_friends AS (
    SELECT CASE WHEN requester_id = p_user_id THEN addressee_id ELSE requester_id END AS friend_id
    FROM public.pingchat_friendships
    WHERE status = 'accepted'
      AND (requester_id = p_user_id OR addressee_id = p_user_id)
  )
  SELECT p.*
  FROM public.pingchat_profiles p
  WHERE p.id IN (SELECT friend_id FROM my_friends
                 INTERSECT
                 SELECT friend_id FROM their_friends)
  ORDER BY p.full_name ASC;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_mutual_friends(uuid) TO authenticated;

-- ---------- 4. Storage bucket 'covers' (cover photos) ----------
-- Public bucket; each user writes only inside their own folder
-- (<userId>/...), same pattern as 'avatars'. 10 MB like chat-photos:
-- covers are wide images and compress less aggressively than avatars.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('covers', 'covers', true, 10485760,
        ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "pingchat_covers_public_read" ON storage.objects;
CREATE POLICY "pingchat_covers_public_read" ON storage.objects
  FOR SELECT TO public USING (bucket_id = 'covers');
DROP POLICY IF EXISTS "pingchat_covers_insert_own" ON storage.objects;
CREATE POLICY "pingchat_covers_insert_own" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'covers' AND (storage.foldername(name))[1] = auth.uid()::text
  );
DROP POLICY IF EXISTS "pingchat_covers_update_own" ON storage.objects;
CREATE POLICY "pingchat_covers_update_own" ON storage.objects
  FOR UPDATE TO authenticated
  USING (bucket_id = 'covers' AND (storage.foldername(name))[1] = auth.uid()::text)
  WITH CHECK (bucket_id = 'covers' AND (storage.foldername(name))[1] = auth.uid()::text);
DROP POLICY IF EXISTS "pingchat_covers_delete_own" ON storage.objects;
CREATE POLICY "pingchat_covers_delete_own" ON storage.objects
  FOR DELETE TO authenticated USING (
    bucket_id = 'covers' AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- ============================================================
-- DONE. Verify in the dashboard:
--   Database > Tables: pingchat_profile_views exists; pingchat_profiles
--     has cover_photo_url, city, education, status_line, social_links,
--     theme_color, is_verified.
--   Storage: 'covers' bucket listed (4 buckets total now).
-- ============================================================
