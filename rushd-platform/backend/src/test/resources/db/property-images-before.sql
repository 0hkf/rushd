-- Disposable PostgreSQL only: preservation fixture and deliberate partial-upgrade probe.
INSERT INTO public.users(id,name,email,password,role) VALUES(-91010,'P10 synthetic','p10-migration@example.invalid','unused','ADMIN');
INSERT INTO public.properties(id,seller_id,title,type,city,district,area,price,status)
VALUES(-91010,-91010,'P10 legacy precision','LAND','مكة','النوارية',500.25,9007199254740993.01,'ACTIVE');
CREATE TABLE public.property_images(p10_partial_probe integer);
