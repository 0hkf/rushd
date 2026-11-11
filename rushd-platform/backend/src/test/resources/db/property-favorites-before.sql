-- Owned disposable PostgreSQL only. Seed existing data and a partial-upgrade probe.
INSERT INTO public.users(id,name,email,password,role) VALUES
(-91300,'P13 synthetic publisher','p13-migration-admin@example.invalid','unused','ADMIN'),
(-91301,'P13 synthetic buyer','p13-migration-buyer@example.invalid','unused','BUYER');
INSERT INTO public.properties(id,seller_id,title,type,city,district,area,price,status)
VALUES(-91300,-91300,'P13 legacy preservation','LAND','مكة','النوارية',500.25,9007199254740993.01,'ACTIVE');
CREATE INDEX ix_favorites_page ON public.properties(id);
