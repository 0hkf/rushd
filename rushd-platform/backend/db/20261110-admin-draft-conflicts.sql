-- Additive, opt-in migration; never apply automatically to the project database.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
DO $guard$
BEGIN
  IF to_regclass('public.admin_property_drafts') IS NULL THEN
    RAISE EXCEPTION 'Draft conflicts require the admin drafts migration';
  END IF;
  IF to_regclass('public.admin_property_draft_conflicts') IS NOT NULL THEN
    RAISE EXCEPTION 'Draft conflicts refuse repeated migration';
  END IF;
END $guard$;
CREATE TABLE public.admin_property_draft_conflicts (
  id uuid PRIMARY KEY,
  draft_id uuid NOT NULL REFERENCES public.admin_property_drafts(id) ON DELETE CASCADE,
  server_revision bigint NOT NULL CHECK (server_revision >= 0),
  local_payload text NOT NULL CHECK (octet_length(local_payload) <= 65536),
  server_payload text NOT NULL CHECK (octet_length(server_payload) <= 65536),
  property_payload text CHECK (octet_length(property_payload) <= 65536),
  property_fingerprint varchar(64),
  choices_hash varchar(64),
  resolved_revision bigint,
  created_at timestamptz NOT NULL,
  CHECK ((property_payload IS NULL) = (property_fingerprint IS NULL)),
  CHECK (property_fingerprint IS NULL OR property_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK ((choices_hash IS NULL) = (resolved_revision IS NULL)),
  CHECK (choices_hash IS NULL OR choices_hash ~ '^[0-9a-f]{64}$'),
  CHECK (resolved_revision IS NULL OR resolved_revision > server_revision)
);
CREATE INDEX ix_admin_draft_conflicts_draft ON public.admin_property_draft_conflicts(draft_id, created_at, id);
COMMIT;
