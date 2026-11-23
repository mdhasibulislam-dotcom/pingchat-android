-- ============================================================
-- migration-v324-privacy-fixes.sql — Privacy fixes (v3.2.4)
-- Run once in the Supabase SQL Editor (project tjsalnhtuqqigyhvgpnb).
-- Requires database/schema-supabase-v3.sql,
-- database/migration-v310-privacy.sql and
-- database/migration-v320-privacy-toggles.sql to have been run first
-- (uses pingchat_friendships, pingchat_blocks and
-- pingchat_privacy_settings.add_who).
-- IDEMPOTENT — safe to run again. Additive only: no table is
-- dropped or rebuilt, and no MCQ app table is touched.
--
-- WHAT THIS DOES (audit fixes, v3.2.4):
--   1. Recreates pingchat_send_friend_request so it ALSO enforces
--      the RECIPIENT's "Who Can Add You" (add_who) setting and the
--      block list — the old version checked only self-requests and
--      duplicates, so a request reached anyone regardless of their
--      setting. The app enforces the same rule client-side
--      (PrivacyRepository.canSendFriendRequestTo, called from
--      FriendsRepository.sendFriendRequest); this is the server
--      backstop so no client can bypass it.
--      add_who semantics (mirrors canSendFriendRequestTo exactly):
--        everybody — anyone may send a request (also when the
--          recipient has no settings row yet: the permissive default).
--        friends   — only someone already friends with the
--          recipient. A stranger is never a friend, so in practice
--          this blocks new strangers; the duplicate check below
--          still rejects a request to someone already a friend.
--        nobody    — no one may send a request.
--      Blocks (either side blocked the other) reject as well.
--      The existing self-request and duplicate checks are unchanged,
--      and a previous 'declined' row is still removed first so a
--      fresh request is allowed.
--
--   NOT in this file (verified, no fix needed):
--   * pingchat_cleanup_auto_delete() (migration-v310-privacy.sql)
--     is CORRECT — it deletes messages older than their
--     conversation's 24h / 7d / 30d timer. The bug was that nothing
--     ever SET the timer: v3.2.4 applies the user's
--     auto_delete_default when a conversation is created (app-side,
--     ChatRepository.getOrCreateConversation) and adds the per-chat
--     picker (peer profile screen). The function itself is NOT
--     recreated here. NOTE: it only runs if pg_cron is enabled and
--     the 'pingchat-auto-delete-cleanup' job exists (scheduled by
--     migration-v310-privacy.sql) — verify with:
--       SELECT * FROM cron.job
--       WHERE jobname = 'pingchat-auto-delete-cleanup';
--   * The 7-day photo/voice expiry (migration-v310-media-expiry.sql,
--     pingchat_cleanup_expired_media) is a separate feature and is
--     left untouched.
-- ============================================================

-- Send a friend request. Rejects self-requests and duplicates; a
-- previous 'declined' row is removed first so a fresh request is allowed.
-- v3.2.4: ALSO rejects when either side blocked the other, and when the
-- RECIPIENT's "Who Can Add You" (add_who) setting does not allow it.
DROP FUNCTION IF EXISTS public.pingchat_send_friend_request(uuid);
CREATE OR REPLACE FUNCTION public.pingchat_send_friend_request(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me uuid := auth.uid();
  v_add_who text;
  v_are_friends boolean;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF p_user_id = v_me THEN RAISE EXCEPTION 'cannot send a friend request to yourself'; END IF;

  -- v3.2.4: blocks — either side blocked the other, no contact at all.
  IF EXISTS (SELECT 1 FROM pingchat_blocks
             WHERE (blocker_id = v_me AND blocked_id = p_user_id)
                OR (blocker_id = p_user_id AND blocked_id = v_me)) THEN
    RAISE EXCEPTION 'You cannot send a friend request to this person.';
  END IF;

  -- v3.2.4: the RECIPIENT's "Who Can Add You" setting governs.
  -- No settings row yet = the permissive default ('everybody').
  SELECT add_who INTO v_add_who
  FROM pingchat_privacy_settings
  WHERE user_id = p_user_id;
  v_add_who := COALESCE(v_add_who, 'everybody');

  IF v_add_who = 'nobody' THEN
    RAISE EXCEPTION 'This person isn''t accepting friend requests.';
  ELSIF v_add_who = 'friends' THEN
    -- "Friends" means the requester must already be a friend of the
    -- recipient (mirrors PrivacyRepository.canSendFriendRequestTo).
    -- A stranger is never a friend, so this blocks new strangers;
    -- an already-friend is still refused by the duplicate check below.
    SELECT EXISTS (SELECT 1 FROM pingchat_friendships
                   WHERE status = 'accepted'
                     AND ((requester_id = v_me AND addressee_id = p_user_id)
                       OR (requester_id = p_user_id AND addressee_id = v_me)))
      INTO v_are_friends;
    IF NOT v_are_friends THEN
      RAISE EXCEPTION 'This person isn''t accepting friend requests.';
    END IF;
  END IF;

  DELETE FROM pingchat_friendships
  WHERE status = 'declined'
    AND ((requester_id = v_me AND addressee_id = p_user_id)
      OR (requester_id = p_user_id AND addressee_id = v_me));
  IF EXISTS (SELECT 1 FROM pingchat_friendships
             WHERE (requester_id = v_me AND addressee_id = p_user_id)
                OR (requester_id = p_user_id AND addressee_id = v_me)) THEN
    RAISE EXCEPTION 'a friend request already exists for this person';
  END IF;
  INSERT INTO pingchat_friendships (requester_id, addressee_id, status)
  VALUES (v_me, p_user_id, 'pending');
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_send_friend_request(uuid) TO authenticated;

-- ============================================================
-- DONE. Verify in the dashboard:
--   Database > Functions: pingchat_send_friend_request exists
--   (recreated with the add_who + blocks checks).
--   Quick check (as a signed-in user, via the API or SQL with
--   auth.uid() set): a request to a user whose add_who = 'nobody'
--   now raises "This person isn't accepting friend requests."
-- ============================================================
