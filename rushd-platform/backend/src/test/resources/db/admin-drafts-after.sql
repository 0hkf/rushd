-- Disposable-database checks; every fixture is rolled back.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
INSERT INTO public.users(id, name, email, password, role)
VALUES (-11, 'Synthetic draft admin', 'draft-schema@example.invalid', 'not-a-password', 'ADMIN');
INSERT INTO public.properties(id, seller_id, title, type, listing_type, city, district, area, price, purpose, status)
VALUES (-11, -11, 'Synthetic property', 'LAND', 'SALE', 'مكة', 'حي', 100, 100, 'RESIDENTIAL', 'ACTIVE');

INSERT INTO public.admin_property_drafts(id, owner_id, kind, revision, payload, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000001', -11, 'CREATE', 0, '{}', now(), now()),
       ('00000000-0000-4000-8000-000000000002', -11, 'CREATE', 0, '{}', now(), now());
INSERT INTO public.admin_property_drafts(id, owner_id, kind, property_id, base_fingerprint, revision, payload, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000003', -11, 'EDIT', -11, repeat('a', 64), 0, '{}', now(), now());

DO $checks$
BEGIN
    BEGIN
        INSERT INTO public.admin_property_drafts(id, owner_id, kind, property_id, base_fingerprint, revision, payload, created_at, updated_at)
        VALUES ('00000000-0000-4000-8000-000000000004', -11, 'EDIT', -11, repeat('a', 64), 0, '{}', now(), now());
        RAISE EXCEPTION 'Duplicate edit draft was accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.admin_property_drafts(id, owner_id, kind, property_id, revision, payload, created_at, updated_at)
        VALUES ('00000000-0000-4000-8000-000000000005', -11, 'EDIT', -11, 0, '{}', now(), now());
        RAISE EXCEPTION 'Edit draft without baseline was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.admin_property_drafts(id, owner_id, kind, revision, payload, created_at, updated_at)
        VALUES ('00000000-0000-4000-8000-000000000006', -11, 'CREATE', -1, '{}', now(), now());
        RAISE EXCEPTION 'Negative revision was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.admin_property_drafts(id, owner_id, kind, revision, payload, created_at, updated_at)
        VALUES ('00000000-0000-4000-8000-000000000007', -11, 'CREATE', 0, repeat('ع', 32769), now(), now());
        RAISE EXCEPTION 'Oversized UTF-8 payload was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        DELETE FROM public.properties WHERE id = -11;
        RAISE EXCEPTION 'Property with saved edit draft was deleted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    INSERT INTO public.admin_property_draft_receipts
        (owner_id, operation_id, draft_id, draft_revision, outcome, request_fingerprint, result_property_id, committed_at)
    VALUES (-11, '00000000-0000-4000-8000-000000000011',
            '00000000-0000-4000-8000-000000000001', 0, 'DELETE', repeat('a', 64), NULL, now());
    BEGIN
        INSERT INTO public.admin_property_draft_receipts
            (owner_id, operation_id, draft_id, draft_revision, outcome, request_fingerprint, result_property_id, committed_at)
        VALUES (-11, '00000000-0000-4000-8000-000000000012',
                '00000000-0000-4000-8000-000000000001', 0, 'COMMIT', repeat('a', 64), -11, now());
        RAISE EXCEPTION 'Second receipt for the same draft was accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.admin_property_draft_receipts
            (owner_id, operation_id, draft_id, draft_revision, outcome, request_fingerprint, result_property_id, committed_at)
        VALUES (-11, '00000000-0000-4000-8000-000000000013',
                '00000000-0000-4000-8000-000000000002', 0, 'COMMIT', repeat('a', 64), NULL, now());
        RAISE EXCEPTION 'Commit receipt without a result was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
END;
$checks$;
ROLLBACK;
