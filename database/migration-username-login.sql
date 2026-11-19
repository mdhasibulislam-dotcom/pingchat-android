-- PingChat Android v3 — username login support.
-- Run once in the Supabase Dashboard > SQL Editor for project
-- tjsalnhtuqqigyhvgpnb ("hasibulialam's Project"). IDEMPOTENT — safe to
-- run again.
--
-- WHY THIS EXISTS: a username has no "@", so the app cannot sign in with
-- it directly — GoTrue needs an email. The sign-in email lives in
-- auth.users, which the client cannot read, so this SECURITY DEFINER
-- function resolves a username to its email for login.
--
-- NOTE: anyone can call this function with any username and get that
-- user's sign-in email back. That is the inherent trade-off of username
-- login (the email must be discoverable to sign in) — the email itself
-- is not a secret, and the password is still required.
CREATE OR REPLACE FUNCTION public.pingchat_email_for_username(p_username text)
RETURNS text
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT u.email
  FROM auth.users u
  JOIN public.pingchat_profiles p ON p.id = u.id
  WHERE p.username = lower(p_username)
  LIMIT 1;
$$;
GRANT EXECUTE ON FUNCTION public.pingchat_email_for_username(text) TO anon, authenticated;
