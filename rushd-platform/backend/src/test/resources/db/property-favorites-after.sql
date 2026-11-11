-- Owned disposable PostgreSQL only. No fixtures survive this verification.
BEGIN;
DO $test$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM public.properties WHERE id=-91300 AND price=9007199254740993.01 AND area=500.25)
       OR EXISTS(SELECT 1 FROM public.property_favorites) THEN
        RAISE EXCEPTION 'P13 changed legacy data or seeded favorites';
    END IF;
END $test$;
INSERT INTO public.property_favorites(user_id,property_id,created_at) VALUES(-91301,-91300,now());
DO $constraints$
BEGIN
    BEGIN
        INSERT INTO public.property_favorites(user_id,property_id,created_at) VALUES(-91301,-91300,now());
        RAISE EXCEPTION 'Duplicate favorite accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.property_favorites(user_id,property_id,created_at) VALUES(-91302,-91300,now());
        RAISE EXCEPTION 'Unknown user accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.property_favorites(user_id,property_id,created_at) VALUES(-91301,-91302,now());
        RAISE EXCEPTION 'Unknown property accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        UPDATE public.property_favorites SET created_at=NULL WHERE user_id=-91301;
        RAISE EXCEPTION 'Null creation time accepted';
    EXCEPTION WHEN not_null_violation THEN NULL; END;
    DELETE FROM public.users WHERE id=-91301;
    IF EXISTS(SELECT 1 FROM public.property_favorites WHERE user_id=-91301) THEN RAISE EXCEPTION 'User cascade failed'; END IF;
    INSERT INTO public.property_favorites(user_id,property_id,created_at) VALUES(-91300,-91300,now());
    DELETE FROM public.properties WHERE id=-91300;
    IF EXISTS(SELECT 1 FROM public.property_favorites WHERE property_id=-91300) THEN RAISE EXCEPTION 'Property cascade failed'; END IF;
END $constraints$;
ROLLBACK;
DELETE FROM public.properties WHERE id=-91300;
DELETE FROM public.users WHERE id IN(-91300,-91301);
