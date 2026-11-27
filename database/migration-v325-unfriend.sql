-- ============================================================
-- migration-v325-unfriend.sql — Unfriend (v3.2.5)
-- Run once in the Supabase SQL Editor (project tjsalnhtuqqigyhvgpnb).
-- Requires database/schema-supabase-v3.sql to have been run first
-- (uses pingchat_friendships).
-- IDEMPOTENT — safe to run again. Additive only: one new RPC; no table
-- is dropped or rebuilt, and no MCQ app table is touched.
--
-- WHAT THIS DOES:
--   Adds pingchat_remove_friend(p_user_id) — a SECURITY DEFINER RPC
--   that deletes the ACCEPTED friendship row between the caller and
--   the given user, in either direction. A plain client-side delete
--   cannot do this: the RLS policy
--   "pingchat_friendships_delete_requester" only lets the requester
--   delete a PENDING row (cancel a sent request). The app calls this
--   RPC from FriendsRepository.removeFriend (v3.2.5).
--
--   After unfriending, either side may send a fresh friend request:
--   pingchat_send_friend_request deletes old 'declined' rows first.
--   Blocking is separate and unchanged (pingchat_blocks; a blocked
--   person stays blocked and hidden from requests either way).
-- ============================================================

-- Remove an accepted friend, either side of the row. Only the two
-- parties to the friendship may call this, and only on an accepted row.
CREATE OR REPLACE FUNCTION public.pingchat_remove_friend(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me uuid := auth.uid();
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF p_user_id = v_me THEN RAISE EXCEPTION 'cannot unfriend yourself'; END IF;
  DELETE FROM pingchat_friendships
  WHERE status = 'accepted'
    AND ((requester_id = v_me AND addressee_id = p_user_id)
      OR (requester_id = p_user_id AND addressee_id = v_me));
  IF NOT FOUND THEN RAISE EXCEPTION 'you are not friends with this person'; END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_remove_friend(uuid) TO authenticated;
