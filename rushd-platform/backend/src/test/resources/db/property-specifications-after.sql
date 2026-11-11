-- Run only after the reviewed upgrade in the owned disposable container.
BEGIN;
DO $test$
DECLARE actual jsonb; expected jsonb; item record;
BEGIN
    IF current_database() <> 'rushd_verify' OR current_user <> 'rushd_verify' THEN
        RAISE EXCEPTION 'P04 checks require the disposable verification database';
    END IF;
    IF (SELECT count(*) FROM public.properties) <> 2 OR (SELECT count(*) FROM public.p04_legacy_snapshot) <> 2
       OR (SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='properties' AND left(column_name,5)='spec_') <> 37 THEN
        RAISE EXCEPTION 'P04 row count or column count changed unexpectedly';
    END IF;
    FOR item IN SELECT id FROM public.p04_legacy_snapshot LOOP
        SELECT to_jsonb(s) INTO expected FROM public.p04_legacy_snapshot s WHERE id=item.id;
        SELECT jsonb_object_agg(key,value) INTO actual
          FROM public.properties p, LATERAL jsonb_each(to_jsonb(p))
         WHERE p.id=item.id AND left(key,5)<>'spec_';
        IF actual IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Legacy data changed'; END IF;
        IF EXISTS (SELECT 1 FROM public.properties p, LATERAL jsonb_each(to_jsonb(p))
                    WHERE p.id=item.id AND left(key,5)='spec_' AND value<>'null'::jsonb) THEN
            RAISE EXCEPTION 'Legacy specification was inferred instead of remaining null';
        END IF;
    END LOOP;

    BEGIN
        UPDATE public.properties SET spec_land_area=0 WHERE id=-90401;
        RAISE EXCEPTION 'Expected area bound rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.properties SET spec_has_elevator=false WHERE id=-90401;
        RAISE EXCEPTION 'Expected nonapplicable false rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    UPDATE public.properties SET spec_land_area=area,spec_primary_area_source='LAND' WHERE id=-90401;
    BEGIN
        UPDATE public.properties SET area=area+0.01 WHERE id=-90401;
        RAISE EXCEPTION 'Expected old-client linked area conflict rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.properties SET spec_land_area=NULL WHERE id=-90401;
        RAISE EXCEPTION 'Expected linked source clear rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.properties SET spec_unit_net_area=200,spec_unit_gross_area=100 WHERE id=-90402;
        RAISE EXCEPTION 'Expected net/gross rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.properties SET spec_floor_number=-11 WHERE id=-90402;
        RAISE EXCEPTION 'Expected floor bound rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.properties SET type='COMMERCIAL',spec_floor_count=2 WHERE id=-90402;
        RAISE EXCEPTION 'Expected missing commercial scope rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.properties SET type='BUILDING',spec_total_units=10,spec_residential_units=6,spec_commercial_units=5 WHERE id=-90402;
        RAISE EXCEPTION 'Expected unit total rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.properties SET spec_estimated_age_years=8 WHERE id=-90402;
        RAISE EXCEPTION 'Expected incomplete age pair rejection';
    EXCEPTION WHEN check_violation THEN NULL; END;
    UPDATE public.properties SET spec_estimated_age_years=0,spec_age_as_of_year=2026 WHERE id=-90402;
    IF EXISTS (SELECT 1 FROM public.properties WHERE id=-90402 AND spec_condition IS NOT NULL) THEN
        RAISE EXCEPTION 'Age zero must not infer NEW';
    END IF;
END;
$test$;
DELETE FROM public.properties WHERE id IN (-90401,-90402);
DELETE FROM public.users WHERE id=-90400;
DROP TABLE public.p04_legacy_snapshot;
COMMIT;
