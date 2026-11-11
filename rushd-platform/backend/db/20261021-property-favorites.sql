-- P13 additive migration. REVIEW ONLY; never auto-applied to the project database.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
DO $guard$
BEGIN
    IF to_regclass('public.users') IS NULL OR to_regclass('public.properties') IS NULL THEN
        RAISE EXCEPTION 'P13 requires the existing users/properties schema';
    END IF;
    IF to_regclass('public.property_favorites') IS NOT NULL OR to_regclass('public.ix_favorites_page') IS NOT NULL THEN
        RAISE EXCEPTION 'P13 refuses partial or repeated migration';
    END IF;
END $guard$;
CREATE TABLE public.property_favorites (
    user_id bigint NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    property_id bigint NOT NULL REFERENCES public.properties(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    PRIMARY KEY(user_id, property_id)
);
CREATE INDEX ix_favorites_page ON public.property_favorites(user_id, created_at DESC, property_id DESC);
COMMIT;
