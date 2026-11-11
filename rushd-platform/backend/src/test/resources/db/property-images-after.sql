-- Executed ONLY by the owned disposable PostgreSQL runner, never against db_rushd.
BEGIN;
DO $test$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM public.properties WHERE id=-91010 AND price=9007199254740993.01 AND area=500.25)
       OR EXISTS(SELECT 1 FROM public.property_image_collections) OR EXISTS(SELECT 1 FROM public.property_images) THEN
        RAISE EXCEPTION 'P10 migration changed legacy data or seeded image metadata';
    END IF;
END $test$;
INSERT INTO public.properties(id,seller_id,title,type,city,district,area,price,status)
VALUES(-91011,-91010,'P10 second','LAND','مكة','النوارية',500,1000,'DRAFT');
INSERT INTO public.property_image_collections(property_id) VALUES(-91010),(-91011);
INSERT INTO public.property_images(id,property_id,storage_key,upload_token,fingerprint,state,sort_order,
display_width,display_height,thumbnail_width,thumbnail_height,display_bytes,thumbnail_bytes,attested_by,rights_basis,created_at)
VALUES('11111111-1111-4111-8111-111111111111',-91010,'21111111-1111-4111-8111-111111111111','31111111-1111-4111-8111-111111111111',repeat('a',64),'READY',0,640,480,640,480,100,100,-91010,'OWNED',now()),
('11111111-1111-4111-8111-111111111112',-91011,'21111111-1111-4111-8111-111111111112','31111111-1111-4111-8111-111111111112',repeat('a',64),'READY',0,640,480,640,480,100,100,-91010,'OWNED',now());
UPDATE public.property_image_collections SET primary_image_id='11111111-1111-4111-8111-111111111111' WHERE property_id=-91010;
DO $constraints$
BEGIN
    BEGIN
        UPDATE public.property_image_collections SET primary_image_id='11111111-1111-4111-8111-111111111112' WHERE property_id=-91010;
        RAISE EXCEPTION 'Cross-property primary was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        DELETE FROM public.properties WHERE id=-91010;
        RAISE EXCEPTION 'Uncoordinated property deletion was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        UPDATE public.property_images SET state='DELETED' WHERE property_id=-91010;
        RAISE EXCEPTION 'Invalid deleted row was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.property_images SET display_bytes=2097153 WHERE property_id=-91010;
        RAISE EXCEPTION 'Oversized display was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.property_images SET rights_basis='LICENSED' WHERE property_id=-91010;
        RAISE EXCEPTION 'Missing license reference was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
END $constraints$;
ROLLBACK;
-- Remove the pre-upgrade preservation fixture; all in-transaction image rows rolled back.
DELETE FROM public.properties WHERE id=-91010;
DELETE FROM public.users WHERE id=-91010;
