-- PingChat Android v3.1.0 — group chats.
-- Run in the Supabase Dashboard > SQL Editor for project
-- tjsalnhtuqqigyhvgpnb ("hasibulialam's Project"). IDEMPOTENT — safe to
-- run again.
--
-- DESIGN (reuses the v3 tables, no new pingchat_groups table):
-- - pingchat_conversations gains is_group / group_name / group_avatar_url /
--   created_by. A 1-to-1 conversation has is_group = false and exactly 2
--   participants; a group has is_group = true and 2+ participants.
-- - pingchat_conversation_participants gains role ('admin' | 'member').
--   The creator is the first admin; admins add/remove members and promote
--   other members to admin. Group calls are NOT built — calls stay 1-to-1.
-- - Member add/remove and admin changes go through SECURITY DEFINER RPCs
--   below: the v3 participants RLS only lets a user insert/update their
--   OWN row (and has no DELETE policy at all), so an admin could never
--   add or remove someone else directly. The RPCs enforce the rules
--   server-side (caller is admin, target is a friend of the caller for
--   adds, a group never drops below... see each function).
-- - Group photos reuse the public 'chat-photos' bucket (10 MB, images):
--   the app uploads to <creatorId>/group-<uuid>.jpg and stores the public
--   URL in group_avatar_url. No new bucket is needed.
-- - Group text/photo/voice messages are ordinary pingchat_messages rows
--   in the group conversation; the app shows the sender's name on each
--   bubble. Push for groups goes through the same send-push flow.
--
-- WHAT THIS DOES:
-- 1. Columns on pingchat_conversations + pingchat_conversation_participants.
-- 2. RPCs: pingchat_create_group, pingchat_add_group_member,
--    pingchat_remove_group_member, pingchat_make_group_admin,
--    pingchat_leave_group, pingchat_update_group.
-- 3. A group row's last_message_at trigger already exists (v3
--    pingchat_on_message_created) and covers groups unchanged.
-- 4. Realtime: pingchat_conversations + pingchat_conversation_participants
--    are already in the supabase_realtime publication (v3 schema).

-- ============================================================
-- 1. COLUMNS
-- ============================================================

ALTER TABLE public.pingchat_conversations
  ADD COLUMN IF NOT EXISTS is_group boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS group_name text,
  ADD COLUMN IF NOT EXISTS group_avatar_url text,
  ADD COLUMN IF NOT EXISTS created_by uuid REFERENCES auth.users (id) ON DELETE SET NULL;

ALTER TABLE public.pingchat_conversation_participants
  ADD COLUMN IF NOT EXISTS role text NOT NULL DEFAULT 'member'
    CHECK (role IN ('admin', 'member'));

-- ============================================================
-- 2. HELPERS + RPCs
-- ============================================================

-- True when the caller is an admin of the group (SECURITY DEFINER so
-- the RLS on participants never blocks the check itself).
CREATE OR REPLACE FUNCTION public.pingchat_is_group_admin(p_conversation_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.pingchat_conversation_participants
    WHERE conversation_id = p_conversation_id
      AND user_id = auth.uid()
      AND role = 'admin'
  );
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_is_group_admin(uuid) TO authenticated;

-- True when the conversation is a group and the caller is a member.
CREATE OR REPLACE FUNCTION public.pingchat_is_group_member(p_conversation_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.pingchat_conversations c
    JOIN public.pingchat_conversation_participants p
      ON p.conversation_id = c.id
    WHERE c.id = p_conversation_id
      AND c.is_group IS TRUE
      AND p.user_id = auth.uid()
  );
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_is_group_member(uuid) TO authenticated;

-- Create a group. p_member_ids are the OTHER members (friends of the
-- caller); the caller is added as the first admin. Returns the new
-- conversation id. Raises when a member is not a friend, is blocked
-- either way, or is the caller themself.
CREATE OR REPLACE FUNCTION public.pingchat_create_group(
  p_name text,
  p_member_ids uuid[],
  p_avatar_url text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_conv_id uuid;
  v_me uuid := auth.uid();
  v_member uuid;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF p_name IS NULL OR btrim(p_name) = '' THEN
    RAISE EXCEPTION 'group name is required';
  END IF;
  IF p_member_ids IS NULL OR array_length(p_member_ids, 1) IS NULL THEN
    RAISE EXCEPTION 'a group needs at least one other member';
  END IF;

  FOREACH v_member IN ARRAY p_member_ids LOOP
    IF v_member = v_me THEN RAISE EXCEPTION 'cannot add yourself twice'; END IF;
    IF EXISTS (SELECT 1 FROM pingchat_blocks
               WHERE (blocker_id = v_me AND blocked_id = v_member)
                  OR (blocker_id = v_member AND blocked_id = v_me)) THEN
      RAISE EXCEPTION 'cannot add a blocked user to a group';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pingchat_friendships
                   WHERE status = 'accepted'
                     AND ((requester_id = v_me AND addressee_id = v_member)
                       OR (requester_id = v_member AND addressee_id = v_me))) THEN
      RAISE EXCEPTION 'group members must be your friends first';
    END IF;
  END LOOP;

  INSERT INTO pingchat_conversations (is_group, group_name, group_avatar_url, created_by)
  VALUES (true, btrim(p_name), p_avatar_url, v_me)
  RETURNING id INTO v_conv_id;

  INSERT INTO pingchat_conversation_participants (conversation_id, user_id, role)
  VALUES (v_conv_id, v_me, 'admin');

  INSERT INTO pingchat_conversation_participants (conversation_id, user_id, role)
  SELECT v_conv_id, m, 'member'
  FROM unnest(p_member_ids) AS m
  ON CONFLICT (conversation_id, user_id) DO NOTHING;

  RETURN v_conv_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_create_group(text, uuid[], text) TO authenticated;

-- Add a member (admin only, target must be a friend of the caller).
CREATE OR REPLACE FUNCTION public.pingchat_add_group_member(
  p_conversation_id uuid,
  p_user_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me uuid := auth.uid();
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF NOT public.pingchat_is_group_admin(p_conversation_id) THEN
    RAISE EXCEPTION 'only a group admin can add members';
  END IF;
  IF p_user_id = v_me THEN RAISE EXCEPTION 'you are already in this group'; END IF;
  IF EXISTS (SELECT 1 FROM pingchat_conversation_participants
             WHERE conversation_id = p_conversation_id AND user_id = p_user_id) THEN
    RAISE EXCEPTION 'user is already a member of this group';
  END IF;
  IF EXISTS (SELECT 1 FROM pingchat_blocks
             WHERE (blocker_id = v_me AND blocked_id = p_user_id)
                OR (blocker_id = p_user_id AND blocked_id = v_me)) THEN
    RAISE EXCEPTION 'cannot add a blocked user to a group';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pingchat_friendships
                 WHERE status = 'accepted'
                   AND ((requester_id = v_me AND addressee_id = p_user_id)
                     OR (requester_id = p_user_id AND addressee_id = v_me))) THEN
    RAISE EXCEPTION 'group members must be your friends first';
  END IF;
  INSERT INTO pingchat_conversation_participants (conversation_id, user_id, role)
  VALUES (p_conversation_id, p_user_id, 'member');
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_add_group_member(uuid, uuid) TO authenticated;

-- Remove a member (admin only). The last admin cannot be removed; use
-- make-admin first. An admin removing themself must leave via
-- pingchat_leave_group instead.
CREATE OR REPLACE FUNCTION public.pingchat_remove_group_member(
  p_conversation_id uuid,
  p_user_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me uuid := auth.uid();
  v_admins integer;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF NOT public.pingchat_is_group_admin(p_conversation_id) THEN
    RAISE EXCEPTION 'only a group admin can remove members';
  END IF;
  IF p_user_id = v_me THEN
    RAISE EXCEPTION 'use leave-group to remove yourself';
  END IF;
  SELECT count(*) INTO v_admins FROM pingchat_conversation_participants
  WHERE conversation_id = p_conversation_id AND role = 'admin';
  IF EXISTS (SELECT 1 FROM pingchat_conversation_participants
             WHERE conversation_id = p_conversation_id
               AND user_id = p_user_id AND role = 'admin')
     AND v_admins <= 1 THEN
    RAISE EXCEPTION 'cannot remove the last admin — make someone else an admin first';
  END IF;
  DELETE FROM pingchat_conversation_participants
  WHERE conversation_id = p_conversation_id AND user_id = p_user_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'user is not a member of this group'; END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_remove_group_member(uuid, uuid) TO authenticated;

-- Promote a member to admin (admin only).
CREATE OR REPLACE FUNCTION public.pingchat_make_group_admin(
  p_conversation_id uuid,
  p_user_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF NOT public.pingchat_is_group_admin(p_conversation_id) THEN
    RAISE EXCEPTION 'only a group admin can make another admin';
  END IF;
  UPDATE pingchat_conversation_participants SET role = 'admin'
  WHERE conversation_id = p_conversation_id AND user_id = p_user_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'user is not a member of this group'; END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_make_group_admin(uuid, uuid) TO authenticated;

-- Leave a group. If the leaver is the last admin and other members
-- remain, the longest-standing member is promoted to admin first so
-- the group is never left admin-less. If the leaver is the last member,
-- the conversation row is deleted (messages cascade).
CREATE OR REPLACE FUNCTION public.pingchat_leave_group(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me uuid := auth.uid();
  v_remaining integer;
  v_next_admin uuid;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF NOT public.pingchat_is_group_member(p_conversation_id) THEN
    RAISE EXCEPTION 'not a member of this group';
  END IF;
  DELETE FROM pingchat_conversation_participants
  WHERE conversation_id = p_conversation_id AND user_id = v_me;
  SELECT count(*) INTO v_remaining FROM pingchat_conversation_participants
  WHERE conversation_id = p_conversation_id;
  IF v_remaining = 0 THEN
    DELETE FROM pingchat_conversations WHERE id = p_conversation_id;
    RETURN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pingchat_conversation_participants
                 WHERE conversation_id = p_conversation_id AND role = 'admin') THEN
    SELECT user_id INTO v_next_admin FROM pingchat_conversation_participants
    WHERE conversation_id = p_conversation_id
    ORDER BY user_id LIMIT 1;
    UPDATE pingchat_conversation_participants SET role = 'admin'
    WHERE conversation_id = p_conversation_id AND user_id = v_next_admin;
  END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_leave_group(uuid) TO authenticated;

-- Rename a group / change its photo (admin only). Pass NULL for a field
-- to leave it unchanged.
CREATE OR REPLACE FUNCTION public.pingchat_update_group(
  p_conversation_id uuid,
  p_name text DEFAULT NULL,
  p_avatar_url text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  IF NOT public.pingchat_is_group_admin(p_conversation_id) THEN
    RAISE EXCEPTION 'only a group admin can change group details';
  END IF;
  UPDATE pingchat_conversations
  SET group_name = COALESCE(NULLIF(btrim(p_name), ''), group_name),
      group_avatar_url = COALESCE(p_avatar_url, group_avatar_url)
  WHERE id = p_conversation_id AND is_group IS TRUE;
  IF NOT FOUND THEN RAISE EXCEPTION 'group not found'; END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_update_group(uuid, text, text) TO authenticated;

-- ============================================================
-- DONE. Verify in the dashboard:
--   pingchat_conversations has is_group / group_name / group_avatar_url /
--   created_by; pingchat_conversation_participants has role.
--   Database > Functions lists the six pingchat_*_group RPCs above.
-- The 1-to-1 RPC pingchat_get_or_create_conversation is untouched and
-- still refuses non-friends and self-chats (no message-yourself).
-- ============================================================
