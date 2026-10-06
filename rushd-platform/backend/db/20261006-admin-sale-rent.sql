-- Manual PostgreSQL upgrade; review and back up before applying.
-- Existing properties are sale listings. Preserve seller_id and existing accounts.
BEGIN;
ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS google_place_id varchar(255),
    ADD COLUMN IF NOT EXISTS formatted_address varchar(500),
    ADD COLUMN IF NOT EXISTS neighborhood varchar(150),
    ADD COLUMN IF NOT EXISTS latitude numeric(10,7),
    ADD COLUMN IF NOT EXISTS longitude numeric(10,7),
    ADD COLUMN IF NOT EXISTS listing_type varchar(10) DEFAULT 'SALE',
    ADD COLUMN IF NOT EXISTS rental_period varchar(10);
UPDATE properties SET listing_type = 'SALE' WHERE listing_type IS NULL;
ALTER TABLE properties ALTER COLUMN listing_type SET DEFAULT 'SALE';
ALTER TABLE properties ALTER COLUMN listing_type SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_properties_google_place_id ON properties (google_place_id);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_property_rental_pricing' AND conrelid = 'properties'::regclass) THEN
        ALTER TABLE properties ADD CONSTRAINT ck_property_rental_pricing CHECK (
            (listing_type = 'SALE' AND rental_period IS NULL) OR
            (listing_type = 'RENT' AND rental_period IS NOT NULL AND rental_period IN ('MONTHLY', 'YEARLY'))
        );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_property_coordinates' AND conrelid = 'properties'::regclass) THEN
        ALTER TABLE properties ADD CONSTRAINT ck_property_coordinates CHECK (
            (latitude IS NULL OR latitude BETWEEN -90 AND 90) AND
            (longitude IS NULL OR longitude BETWEEN -180 AND 180)
        );
    END IF;
END $$;
COMMIT;
