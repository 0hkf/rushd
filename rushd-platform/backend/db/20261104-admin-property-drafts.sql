-- Reviewed separately before use. Never apply automatically to the project database.
-- Draft data and commit receipts must be created/removed only within application transactions.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
DO $guard$
BEGIN
    IF to_regclass('public.users') IS NULL OR to_regclass('public.properties') IS NULL THEN
        RAISE EXCEPTION 'Admin drafts require the existing users/properties schema';
    END IF;
    IF to_regclass('public.admin_property_drafts') IS NOT NULL
       OR to_regclass('public.admin_property_draft_receipts') IS NOT NULL
       OR to_regclass('public.ix_admin_property_drafts_owner_updated') IS NOT NULL THEN
        RAISE EXCEPTION 'Admin drafts refuse partial or repeated migration';
    END IF;
END $guard$;

CREATE TABLE public.admin_property_drafts (
    id uuid PRIMARY KEY,
    owner_id bigint NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    kind varchar(6) NOT NULL CHECK (kind IN ('CREATE', 'EDIT')),
    property_id bigint REFERENCES public.properties(id) ON DELETE RESTRICT,
    base_fingerprint varchar(64),
    revision bigint NOT NULL CHECK (revision >= 0),
    payload text NOT NULL CHECK (octet_length(payload) <= 65536),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK ((kind = 'CREATE' AND property_id IS NULL AND base_fingerprint IS NULL)
        OR (kind = 'EDIT' AND property_id IS NOT NULL AND base_fingerprint IS NOT NULL
            AND base_fingerprint ~ '^[0-9a-f]{64}$')),
    -- NULL property ids are distinct in PostgreSQL: multiple create drafts are allowed.
    CONSTRAINT uq_admin_draft_property UNIQUE (owner_id, property_id)
);
CREATE INDEX ix_admin_property_drafts_owner_updated
    ON public.admin_property_drafts(owner_id, updated_at DESC, id DESC);

-- Receipts contain no draft payload. They prevent retrying a completed operation from
-- resurrecting a draft or creating a second property; retention needs an explicit policy.
CREATE TABLE public.admin_property_draft_receipts (
    owner_id bigint NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    operation_id uuid NOT NULL,
    draft_id uuid NOT NULL,
    draft_revision bigint NOT NULL CHECK (draft_revision >= 0),
    outcome varchar(6) NOT NULL CHECK (outcome IN ('COMMIT', 'DELETE')),
    request_fingerprint varchar(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    result_property_id bigint,
    committed_at timestamptz NOT NULL,
    PRIMARY KEY (owner_id, operation_id),
    UNIQUE (owner_id, draft_id),
    CHECK ((outcome = 'COMMIT' AND result_property_id IS NOT NULL)
        OR (outcome = 'DELETE' AND result_property_id IS NULL))
);
COMMIT;
