-- PingChat v3.2.2 — date of birth on profiles
--
-- The register screen (v3.2.2) asks for a date of birth, stored in the
-- sign-up user metadata as "date_of_birth" and copied into this column
-- by AuthRepository.ensureProfile() when the profile row is created.
-- ISO yyyy-MM-dd; NULL for accounts created before this migration
-- (Google sign-ins and older email accounts have no DOB).
-- RLS is unchanged: the existing pingchat_profiles policies already
-- cover this column (own row insert/update, public select).

alter table public.pingchat_profiles
    add column if not exists date_of_birth date;
