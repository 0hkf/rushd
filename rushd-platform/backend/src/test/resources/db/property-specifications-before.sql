-- Disposable runner fixtures only. Refuses the normal project database.
BEGIN;
DO $test$
BEGIN
    IF current_database() <> 'rushd_verify' OR current_user <> 'rushd_verify'
       OR EXISTS (SELECT 1 FROM public.properties) OR EXISTS (SELECT 1 FROM public.users) THEN
        RAISE EXCEPTION 'P04 fixtures require the empty disposable verification database';
    END IF;
END;
$test$;
INSERT INTO public.users (id,name,email,password,role)
VALUES (-90400,'Migration fixture','p04-migration@example.invalid','unused-test-hash','ADMIN');
INSERT INTO public.properties (id,seller_id,title,type,listing_type,city,district,area,price,status,purpose,latitude,longitude,created_at,updated_at)
VALUES (-90401,-90400,'Legacy land','LAND','SALE','مكة المكرمة','النوارية',500.25,9007199254740993.01,'ACTIVE','RESIDENTIAL',-21.1234567,0,'2026-01-01','2026-01-02'),
       (-90402,-90400,'Legacy apartment','APARTMENT','SALE','مكة المكرمة','الياسمين',150.25,1250000.50,'ACTIVE','RESIDENTIAL',NULL,NULL,'2026-01-03','2026-01-04');
CREATE TABLE public.p04_legacy_snapshot AS SELECT * FROM public.properties;
ALTER TABLE public.properties ADD COLUMN spec_partial_probe integer;
COMMIT;
