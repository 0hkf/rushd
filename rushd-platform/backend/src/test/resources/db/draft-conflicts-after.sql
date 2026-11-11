BEGIN;
SET LOCAL search_path = pg_catalog, public;
INSERT INTO public.users(id, name, email, password, role)
VALUES (-111, 'Synthetic merge admin', 'merge-schema@example.invalid', 'test-only', 'ADMIN');
INSERT INTO public.admin_property_drafts(id, owner_id, kind, revision, payload, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000111', -111, 'CREATE', 0, '{}', now(), now());
INSERT INTO public.admin_property_draft_conflicts(id, draft_id, server_revision, local_payload, server_payload, created_at)
VALUES ('00000000-0000-4000-8000-000000000112', '00000000-0000-4000-8000-000000000111', 0, '{}', '{}', now());
DO $check$
BEGIN
  BEGIN
    UPDATE public.admin_property_draft_conflicts SET local_payload = repeat('ع', 32769);
    RAISE EXCEPTION 'Oversized conflict snapshot accepted';
  EXCEPTION WHEN check_violation THEN NULL; END;
  BEGIN
    UPDATE public.admin_property_draft_conflicts SET property_payload = '{}';
    RAISE EXCEPTION 'Property snapshot without fingerprint accepted';
  EXCEPTION WHEN check_violation THEN NULL; END;
  DELETE FROM public.admin_property_drafts WHERE owner_id = -111;
  IF EXISTS (SELECT 1 FROM public.admin_property_draft_conflicts WHERE id = '00000000-0000-4000-8000-000000000112') THEN
    RAISE EXCEPTION 'Conflict payloads remained after draft deletion';
  END IF;
END $check$;
ROLLBACK;
