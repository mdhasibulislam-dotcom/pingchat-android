-- ============================================================
-- migration-v322-stories.sql — story views, reactions, replies (v3.2.2)
-- Run once in the Supabase SQL Editor (project tjsalnhtuqqigyhvgpnb,
-- "hasibulialam's Project"). IDEMPOTENT — safe to run again.
-- No change to schema-supabase-v3.sql; this file is additive only,
-- except for two new nullable columns on pingchat_messages (§3).
-- It touches NO MCQ app table and NO other pingchat_* table's data.
--
-- WHAT THIS CREATES:
--   - pingchat_story_views (§1): one row per (story, viewer) — the
--     owner sees WHO viewed (names + times) in the Viewers sheet.
--   - pingchat_story_reactions (§2): one row per (story, user) —
--     PRIMARY KEY (story_id, user_id) — the owner sees who reacted.
--   - pingchat_messages.story_id + story_media_url (§3): a story
--     reply is a NORMAL chat message with these set, so it lands in
--     the owner's inbox as a message from the replier, and the chat
--     bubble can show the story attached/quoted above the reply text.
--   - pingchat_story_view_count() (§4): SECURITY DEFINER count RPC so
--     a non-owner sees HOW MANY viewed ("128 views") without seeing WHO.
--
-- DESIGN CHOICE — replies as message COLUMNS, not a dedicated table:
--   A reply MUST arrive in the owner's inbox as a chat message, so it
--   has to live in pingchat_messages anyway (chat list preview, unread
--   badge, realtime feed, delete-for-me/everyone all work for free).
--   A separate pingchat_story_replies table would duplicate the message
--   (two sources of truth, two deletes, two realtime feeds) for no
--   benefit. Two nullable columns on the message row keep one source of
--   truth: the Viewers sheet's REPLIES section reads the SAME rows back
--   (WHERE story_id = <story>), and the chat bubble renders the quote
--   from story_media_url. Old rows keep working — the columns are
--   nullable/defaulted and the app model defaults them to null.
--
-- EXPIRY — NO cron change in this file:
--   Stories still live exactly 12 hours (migration-v310-stories.sql):
--   pingchat_cleanup_expired_stories() deletes the story row every
--   15 min, and story_id FKs below are ON DELETE CASCADE, so a story's
--   views + reactions die WITH it — nothing lingers after 12 h.
--   pingchat_messages.story_id is ON DELETE SET NULL instead: the
--   REPLY must survive in the inbox after the story is gone (it is a
--   chat message first). story_media_url is a snapshot kept on the row,
--   but note the Storage file itself IS deleted with the story, so a
--   reply's thumbnail 404s after expiry — the reply TEXT always
--   remains, and the bubble must tolerate a missing thumbnail.
--
-- RLS summary:
--   - views: anyone who can read the story may insert/update ONLY
--     their own row (viewer_id = auth.uid()); ONLY the story owner
--     may SELECT views (the Viewers sheet is the owner's view).
--   - reactions: same shape — own insert/update/delete, owner-only
--     select. Emoji is CHECK-constrained to the six reaction emoji.
--   - messages: NO policy change — the existing insert policy
--     (sender = auth.uid() AND conversation participant) already
--     covers story replies, and the existing select policy already
--     lets the owner read them (they are a participant of the 1-to-1
--     conversation the reply was sent into).
--   - view count RPC: SECURITY DEFINER, returns 0 when the caller
--     cannot read the story (never leaks WHO viewed, only HOW MANY).
--
-- Realtime: NOT added for views/reactions — the Viewers sheet loads
-- on open and stories live 12 h; a live feed of every view would be
-- noise. (pingchat_messages already has Realtime, so replies arrive
-- in the inbox live, as normal messages do.)
-- ============================================================

-- ============================================================
-- 1. pingchat_story_views — one row per (story, viewer)
-- ============================================================

CREATE TABLE IF NOT EXISTS public.pingchat_story_views (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  story_id uuid NOT NULL REFERENCES public.pingchat_stories (id) ON DELETE CASCADE,
  viewer_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  viewed_at timestamptz NOT NULL DEFAULT now(),
  -- One row per viewer per story: re-viewing upserts (refreshes
  -- viewed_at) instead of duplicating. The app upserts with
  -- onConflict = "story_id,viewer_id".
  UNIQUE (story_id, viewer_id)
);

CREATE INDEX IF NOT EXISTS idx_pingchat_story_views_story_time
  ON public.pingchat_story_views (story_id, viewed_at DESC);

ALTER TABLE public.pingchat_story_views ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE ON public.pingchat_story_views TO authenticated;

-- Insert: only my own view, and only on a story I can read (friend or
-- self of the owner, no block either way — same rule as reading the
-- story itself, see migration-v310-stories.sql §2).
DROP POLICY IF EXISTS "pingchat_story_views_insert_own" ON public.pingchat_story_views;
CREATE POLICY "pingchat_story_views_insert_own" ON public.pingchat_story_views
  FOR INSERT TO authenticated WITH CHECK (
    viewer_id = auth.uid()
    AND EXISTS (
      SELECT 1 FROM public.pingchat_stories s
      WHERE s.id = pingchat_story_views.story_id
        AND public.pingchat_is_friend_or_self(s.user_id)
        AND NOT EXISTS (
          SELECT 1 FROM public.pingchat_blocks b
          WHERE (b.blocker_id = auth.uid() AND b.blocked_id = s.user_id)
             OR (b.blocker_id = s.user_id AND b.blocked_id = auth.uid())
        )
    )
  );

-- Update: only my own row (the upsert refreshing viewed_at). The
-- WITH CHECK is the same as the insert — I can only ever hold a view
-- row on a story I can read.
DROP POLICY IF EXISTS "pingchat_story_views_update_own" ON public.pingchat_story_views;
CREATE POLICY "pingchat_story_views_update_own" ON public.pingchat_story_views
  FOR UPDATE TO authenticated
  USING (viewer_id = auth.uid())
  WITH CHECK (
    viewer_id = auth.uid()
    AND EXISTS (
      SELECT 1 FROM public.pingchat_stories s
      WHERE s.id = pingchat_story_views.story_id
        AND public.pingchat_is_friend_or_self(s.user_id)
        AND NOT EXISTS (
          SELECT 1 FROM public.pingchat_blocks b
          WHERE (b.blocker_id = auth.uid() AND b.blocked_id = s.user_id)
             OR (b.blocker_id = s.user_id AND b.blocked_id = auth.uid())
        )
    )
  );

-- Select: ONLY the story owner reads views of their own stories.
-- A viewer never sees who else viewed — only the count, via §4.
DROP POLICY IF EXISTS "pingchat_story_views_select_owner" ON public.pingchat_story_views;
CREATE POLICY "pingchat_story_views_select_owner" ON public.pingchat_story_views
  FOR SELECT TO authenticated USING (
    EXISTS (
      SELECT 1 FROM public.pingchat_stories s
      WHERE s.id = pingchat_story_views.story_id
        AND s.user_id = auth.uid()
    )
  );

-- No DELETE policy on purpose: a view row dies with its story
-- (ON DELETE CASCADE above) — nobody deletes single views.

-- ============================================================
-- 2. pingchat_story_reactions — one row per (story, user)
-- ============================================================

CREATE TABLE IF NOT EXISTS public.pingchat_story_reactions (
  story_id uuid NOT NULL REFERENCES public.pingchat_stories (id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  -- Exactly the six reaction emoji from the mockup (story-react-reply).
  emoji text NOT NULL
    CHECK (emoji IN ('❤️', '😂', '😮', '😢', '🔥', '👍')),
  reacted_at timestamptz NOT NULL DEFAULT now(),
  -- One reaction per user per story: tap again to change it, tap the
  -- same emoji again to remove it (the app deletes the row).
  PRIMARY KEY (story_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_pingchat_story_reactions_story
  ON public.pingchat_story_reactions (story_id);

ALTER TABLE public.pingchat_story_reactions ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.pingchat_story_reactions TO authenticated;

-- Insert / update: only my own reaction, and only on a story I can
-- read (same friend-or-self + no-block rule as §1).
DROP POLICY IF EXISTS "pingchat_story_reactions_insert_own" ON public.pingchat_story_reactions;
CREATE POLICY "pingchat_story_reactions_insert_own" ON public.pingchat_story_reactions
  FOR INSERT TO authenticated WITH CHECK (
    user_id = auth.uid()
    AND EXISTS (
      SELECT 1 FROM public.pingchat_stories s
      WHERE s.id = pingchat_story_reactions.story_id
        AND public.pingchat_is_friend_or_self(s.user_id)
        AND NOT EXISTS (
          SELECT 1 FROM public.pingchat_blocks b
          WHERE (b.blocker_id = auth.uid() AND b.blocked_id = s.user_id)
             OR (b.blocker_id = s.user_id AND b.blocked_id = auth.uid())
        )
    )
  );

DROP POLICY IF EXISTS "pingchat_story_reactions_update_own" ON public.pingchat_story_reactions;
CREATE POLICY "pingchat_story_reactions_update_own" ON public.pingchat_story_reactions
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (
    user_id = auth.uid()
    AND EXISTS (
      SELECT 1 FROM public.pingchat_stories s
      WHERE s.id = pingchat_story_reactions.story_id
        AND public.pingchat_is_friend_or_self(s.user_id)
        AND NOT EXISTS (
          SELECT 1 FROM public.pingchat_blocks b
          WHERE (b.blocker_id = auth.uid() AND b.blocked_id = s.user_id)
             OR (b.blocker_id = s.user_id AND b.blocked_id = auth.uid())
        )
    )
  );

-- Delete: only my own reaction (tap the same emoji again to remove).
DROP POLICY IF EXISTS "pingchat_story_reactions_delete_own" ON public.pingchat_story_reactions;
CREATE POLICY "pingchat_story_reactions_delete_own" ON public.pingchat_story_reactions
  FOR DELETE TO authenticated USING (user_id = auth.uid());

-- Select: ONLY the story owner reads reactions on their own stories
-- (shown in the Viewers sheet, emoji on the right of each viewer).
DROP POLICY IF EXISTS "pingchat_story_reactions_select_owner" ON public.pingchat_story_reactions;
CREATE POLICY "pingchat_story_reactions_select_owner" ON public.pingchat_story_reactions
  FOR SELECT TO authenticated USING (
    EXISTS (
      SELECT 1 FROM public.pingchat_stories s
      WHERE s.id = pingchat_story_reactions.story_id
        AND s.user_id = auth.uid()
    )
  );

-- ============================================================
-- 3. Story replies — two nullable columns on pingchat_messages
--    (see the design-choice note at the top of this file)
-- ============================================================

ALTER TABLE public.pingchat_messages
  ADD COLUMN IF NOT EXISTS story_id uuid
    REFERENCES public.pingchat_stories (id) ON DELETE SET NULL;

ALTER TABLE public.pingchat_messages
  ADD COLUMN IF NOT EXISTS story_media_url text;

CREATE INDEX IF NOT EXISTS idx_pingchat_messages_story_id
  ON public.pingchat_messages (story_id);

-- No RLS change: the existing pingchat_messages insert/select
-- policies already cover story replies (sender = auth.uid() AND
-- conversation participant; the owner reads them as a participant).
-- story_id is ON DELETE SET NULL so the reply stays in the inbox
-- after the 12-hour story is deleted; story_media_url is a snapshot
-- of the story's media URL at reply time (its Storage file is still
-- deleted with the story — see the expiry note at the top).

-- ============================================================
-- 4. pingchat_story_view_count() — count only, no identities
-- ============================================================

-- A non-owner sees HOW MANY viewed ("128 views" on someone else's
-- story) without seeing WHO. SECURITY DEFINER so the count works
-- even though the views table itself is owner-only for SELECT.
-- Returns 0 when the caller cannot read the story at all.
CREATE OR REPLACE FUNCTION public.pingchat_story_view_count(p_story_id uuid)
RETURNS bigint
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COUNT(*)
  FROM public.pingchat_story_views v
  WHERE v.story_id = p_story_id
    AND EXISTS (
      SELECT 1 FROM public.pingchat_stories s
      WHERE s.id = p_story_id
        AND public.pingchat_is_friend_or_self(s.user_id)
        AND NOT EXISTS (
          SELECT 1 FROM public.pingchat_blocks b
          WHERE (b.blocker_id = auth.uid() AND b.blocked_id = s.user_id)
             OR (b.blocker_id = s.user_id AND b.blocked_id = auth.uid())
        )
    );
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_story_view_count(uuid) TO authenticated;

-- ============================================================
-- DONE. Verify in the dashboard:
--   Database > Tables: pingchat_story_views, pingchat_story_reactions
--     exist; pingchat_messages has story_id + story_media_url.
--   Database > Functions: pingchat_story_view_count(uuid) exists.
--   No new cron job: the 12-hour cleanup in
--     migration-v310-stories.sql already deletes expired stories, and
--     views/reactions cascade with them (messages keep the reply).
-- ============================================================
