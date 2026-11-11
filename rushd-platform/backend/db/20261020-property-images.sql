-- P10 additive migration. REVIEW ONLY: never auto-applied to the project database.
-- Requires the reviewed baseline and P04. All guards and DDL are one transaction.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
DO $guard$
BEGIN
    IF to_regclass('public.properties') IS NULL OR to_regclass('public.users') IS NULL THEN
        RAISE EXCEPTION 'P10 requires the existing properties/users schema';
    END IF;
    IF to_regclass('public.property_images') IS NOT NULL OR to_regclass('public.property_image_collections') IS NOT NULL THEN
        RAISE EXCEPTION 'P10 refuses partial or repeated migration';
    END IF;
END $guard$;

CREATE TABLE public.property_image_collections (
    property_id bigint PRIMARY KEY REFERENCES public.properties(id),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    primary_image_id uuid
);
CREATE TABLE public.property_images (
    id uuid PRIMARY KEY,
    property_id bigint NOT NULL REFERENCES public.property_image_collections(property_id),
    storage_key uuid NOT NULL UNIQUE,
    upload_token uuid NOT NULL,
    fingerprint varchar(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    state varchar(16) NOT NULL CHECK (state IN ('READY','DELETING','DELETED')),
    sort_order integer CHECK (sort_order >= 0),
    alt_text varchar(160),
    display_width integer NOT NULL CHECK (display_width BETWEEN 1 AND 2560),
    display_height integer NOT NULL CHECK (display_height BETWEEN 1 AND 2560),
    thumbnail_width integer NOT NULL CHECK (thumbnail_width BETWEEN 1 AND 640),
    thumbnail_height integer NOT NULL CHECK (thumbnail_height BETWEEN 1 AND 640),
    display_bytes bigint NOT NULL CHECK (display_bytes BETWEEN 1 AND 2097152),
    thumbnail_bytes bigint NOT NULL CHECK (thumbnail_bytes BETWEEN 1 AND 262144),
    attested_by bigint NOT NULL REFERENCES public.users(id),
    rights_basis varchar(16) NOT NULL CHECK (rights_basis IN ('OWNED','AUTHORIZED','LICENSED')),
    source_reference varchar(500),
    created_at timestamptz NOT NULL,
    deleted_at timestamptz,
    UNIQUE(property_id, id),
    UNIQUE(property_id, upload_token),
    UNIQUE(property_id, sort_order),
    CHECK ((state='READY' AND sort_order IS NOT NULL AND deleted_at IS NULL)
        OR (state='DELETING' AND sort_order IS NULL AND deleted_at IS NULL)
        OR (state='DELETED' AND sort_order IS NULL AND deleted_at IS NOT NULL)),
    CHECK (rights_basis <> 'LICENSED' OR length(trim(source_reference)) > 0 AND source_reference IS NOT NULL)
);
ALTER TABLE public.property_image_collections ADD CONSTRAINT fk_property_primary_image
    FOREIGN KEY (property_id, primary_image_id) REFERENCES public.property_images(property_id, id);
CREATE INDEX ix_property_images_state ON public.property_images(property_id, state);
COMMIT;
