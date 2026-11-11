-- Additive PostgreSQL upgrade from the reviewed pre-P04 properties schema.
-- Review/back up before a separately authorized deployment. NEVER auto-run from Spring.
-- No row updates, defaults, type inference, account changes, or destructive rollback.
-- Run with psql --set ON_ERROR_STOP=1. Replays/partial upgrades fail before any DDL.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
DO $guard$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                   WHERE n.nspname='public' AND c.relname='properties' AND c.relkind IN ('r','p')) THEN
        RAISE EXCEPTION 'P04 upgrade requires the existing public.properties table';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='properties'
                   AND column_name='area' AND data_type='numeric' AND numeric_precision=12 AND numeric_scale=2)
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='properties'
                      AND column_name='type' AND data_type='character varying')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='properties'
                      AND column_name='id' AND data_type='bigint') THEN
        RAISE EXCEPTION 'P04 upgrade refused: reviewed base columns do not match';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='properties'
               AND left(column_name,5)='spec_') THEN
        RAISE EXCEPTION 'P04 upgrade refused: specification columns already exist (complete or partial upgrade)';
    END IF;
END;
$guard$;

ALTER TABLE public.properties
    ADD COLUMN spec_primary_area_source varchar(32),
    ADD COLUMN spec_land_area numeric(12,2),
    ADD COLUMN spec_unit_net_area numeric(12,2),
    ADD COLUMN spec_unit_gross_area numeric(12,2),
    ADD COLUMN spec_total_built_area numeric(12,2),
    ADD COLUMN spec_bedrooms integer,
    ADD COLUMN spec_bathrooms integer,
    ADD COLUMN spec_living_rooms integer,
    ADD COLUMN spec_floor_number integer,
    ADD COLUMN spec_floor_count integer,
    ADD COLUMN spec_total_units integer,
    ADD COLUMN spec_residential_units integer,
    ADD COLUMN spec_commercial_units integer,
    ADD COLUMN spec_parking_spaces integer,
    ADD COLUMN spec_estimated_age_years integer,
    ADD COLUMN spec_age_as_of_year integer,
    ADD COLUMN spec_condition varchar(32),
    ADD COLUMN spec_finishing varchar(32),
    ADD COLUMN spec_furnishing varchar(32),
    ADD COLUMN spec_has_elevator boolean,
    ADD COLUMN spec_has_air_conditioning boolean,
    ADD COLUMN spec_has_fitted_kitchen boolean,
    ADD COLUMN spec_has_maid_room boolean,
    ADD COLUMN spec_has_driver_room boolean,
    ADD COLUMN spec_has_pool boolean,
    ADD COLUMN spec_has_yard boolean,
    ADD COLUMN spec_has_balcony boolean,
    ADD COLUMN spec_has_private_roof boolean,
    ADD COLUMN spec_electricity_connected boolean,
    ADD COLUMN spec_water_connected boolean,
    ADD COLUMN spec_sewer_connected boolean,
    ADD COLUMN spec_has_irrigation boolean,
    ADD COLUMN spec_well_count integer,
    ADD COLUMN spec_water_source varchar(32),
    ADD COLUMN spec_commercial_scope varchar(32),
    ADD COLUMN spec_commercial_subtype varchar(32),
    ADD COLUMN spec_other_type_description varchar(100);

ALTER TABLE public.properties
    ADD CONSTRAINT ck_property_spec_primary_area_source_enum CHECK (spec_primary_area_source IS NULL OR spec_primary_area_source IN ('LAND', 'UNIT_NET', 'UNIT_GROSS', 'TOTAL_BUILT')),
    ADD CONSTRAINT ck_property_spec_land_area_range CHECK (spec_land_area IS NULL OR spec_land_area BETWEEN 0.01 AND 9999999999.99),
    ADD CONSTRAINT ck_property_spec_land_area_type CHECK (spec_land_area IS NULL OR (type IN ('LAND', 'VILLA', 'BUILDING', 'FARM', 'OTHER') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='WHOLE_BUILDING'))),
    ADD CONSTRAINT ck_property_spec_unit_net_area_range CHECK (spec_unit_net_area IS NULL OR spec_unit_net_area BETWEEN 0.01 AND 9999999999.99),
    ADD CONSTRAINT ck_property_spec_unit_net_area_type CHECK (spec_unit_net_area IS NULL OR (type IN ('APARTMENT', 'OTHER') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='UNIT'))),
    ADD CONSTRAINT ck_property_spec_unit_gross_area_range CHECK (spec_unit_gross_area IS NULL OR spec_unit_gross_area BETWEEN 0.01 AND 9999999999.99),
    ADD CONSTRAINT ck_property_spec_unit_gross_area_type CHECK (spec_unit_gross_area IS NULL OR (type IN ('APARTMENT', 'OTHER') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='UNIT'))),
    ADD CONSTRAINT ck_property_spec_total_built_area_range CHECK (spec_total_built_area IS NULL OR spec_total_built_area BETWEEN 0.01 AND 9999999999.99),
    ADD CONSTRAINT ck_property_spec_total_built_area_type CHECK (spec_total_built_area IS NULL OR (type IN ('VILLA', 'BUILDING', 'FARM', 'OTHER') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='WHOLE_BUILDING'))),
    ADD CONSTRAINT ck_property_spec_bedrooms_range CHECK (spec_bedrooms IS NULL OR spec_bedrooms BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_property_spec_bedrooms_type CHECK (spec_bedrooms IS NULL OR (type IN ('APARTMENT', 'VILLA'))),
    ADD CONSTRAINT ck_property_spec_bathrooms_range CHECK (spec_bathrooms IS NULL OR spec_bathrooms BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_property_spec_bathrooms_type CHECK (spec_bathrooms IS NULL OR (type IN ('APARTMENT', 'VILLA', 'COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_living_rooms_range CHECK (spec_living_rooms IS NULL OR spec_living_rooms BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_property_spec_living_rooms_type CHECK (spec_living_rooms IS NULL OR (type IN ('APARTMENT', 'VILLA'))),
    ADD CONSTRAINT ck_property_spec_floor_number_range CHECK (spec_floor_number IS NULL OR spec_floor_number BETWEEN -10 AND 200),
    ADD CONSTRAINT ck_property_spec_floor_number_type CHECK (spec_floor_number IS NULL OR (type IN ('APARTMENT') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='UNIT'))),
    ADD CONSTRAINT ck_property_spec_floor_count_range CHECK (spec_floor_count IS NULL OR spec_floor_count BETWEEN 1 AND 200),
    ADD CONSTRAINT ck_property_spec_floor_count_type CHECK (spec_floor_count IS NULL OR (type IN ('VILLA', 'BUILDING') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='WHOLE_BUILDING'))),
    ADD CONSTRAINT ck_property_spec_total_units_range CHECK (spec_total_units IS NULL OR spec_total_units BETWEEN 0 AND 10000),
    ADD CONSTRAINT ck_property_spec_total_units_type CHECK (spec_total_units IS NULL OR (type IN ('BUILDING') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='WHOLE_BUILDING'))),
    ADD CONSTRAINT ck_property_spec_residential_units_range CHECK (spec_residential_units IS NULL OR spec_residential_units BETWEEN 0 AND 10000),
    ADD CONSTRAINT ck_property_spec_residential_units_type CHECK (spec_residential_units IS NULL OR (type IN ('BUILDING') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='WHOLE_BUILDING'))),
    ADD CONSTRAINT ck_property_spec_commercial_units_range CHECK (spec_commercial_units IS NULL OR spec_commercial_units BETWEEN 0 AND 10000),
    ADD CONSTRAINT ck_property_spec_commercial_units_type CHECK (spec_commercial_units IS NULL OR (type IN ('BUILDING') OR (type='COMMERCIAL' AND spec_commercial_scope IS NOT NULL AND spec_commercial_scope='WHOLE_BUILDING'))),
    ADD CONSTRAINT ck_property_spec_parking_spaces_range CHECK (spec_parking_spaces IS NULL OR spec_parking_spaces BETWEEN 0 AND 1000),
    ADD CONSTRAINT ck_property_spec_parking_spaces_type CHECK (spec_parking_spaces IS NULL OR (type IN ('LAND', 'APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL', 'FARM', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_estimated_age_years_range CHECK (spec_estimated_age_years IS NULL OR spec_estimated_age_years BETWEEN 0 AND 1000),
    ADD CONSTRAINT ck_property_spec_estimated_age_years_type CHECK (spec_estimated_age_years IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_age_as_of_year_range CHECK (spec_age_as_of_year IS NULL OR spec_age_as_of_year BETWEEN 1900 AND 9999),
    ADD CONSTRAINT ck_property_spec_age_as_of_year_type CHECK (spec_age_as_of_year IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_condition_enum CHECK (spec_condition IS NULL OR spec_condition IN ('UNDER_CONSTRUCTION', 'NEW', 'USED')),
    ADD CONSTRAINT ck_property_spec_condition_type CHECK (spec_condition IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_finishing_enum CHECK (spec_finishing IS NULL OR spec_finishing IN ('UNFINISHED', 'PARTIAL', 'FULL')),
    ADD CONSTRAINT ck_property_spec_finishing_type CHECK (spec_finishing IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_furnishing_enum CHECK (spec_furnishing IS NULL OR spec_furnishing IN ('UNFURNISHED', 'PARTIAL', 'FULL')),
    ADD CONSTRAINT ck_property_spec_furnishing_type CHECK (spec_furnishing IS NULL OR (type IN ('APARTMENT', 'VILLA'))),
    ADD CONSTRAINT ck_property_spec_has_elevator_type CHECK (spec_has_elevator IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_has_air_conditioning_type CHECK (spec_has_air_conditioning IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_has_fitted_kitchen_type CHECK (spec_has_fitted_kitchen IS NULL OR (type IN ('APARTMENT', 'VILLA', 'COMMERCIAL', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_has_maid_room_type CHECK (spec_has_maid_room IS NULL OR (type IN ('APARTMENT', 'VILLA', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_has_driver_room_type CHECK (spec_has_driver_room IS NULL OR (type IN ('APARTMENT', 'VILLA', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_has_pool_type CHECK (spec_has_pool IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL', 'FARM', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_has_yard_type CHECK (spec_has_yard IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL', 'FARM', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_has_balcony_type CHECK (spec_has_balcony IS NULL OR (type IN ('APARTMENT', 'VILLA', 'COMMERCIAL', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_has_private_roof_type CHECK (spec_has_private_roof IS NULL OR (type IN ('APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL', 'OTHER'))),
    ADD CONSTRAINT ck_property_spec_electricity_connected_type CHECK (spec_electricity_connected IS NULL OR (type IN ('LAND', 'BUILDING', 'COMMERCIAL', 'FARM'))),
    ADD CONSTRAINT ck_property_spec_water_connected_type CHECK (spec_water_connected IS NULL OR (type IN ('LAND', 'BUILDING', 'COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_sewer_connected_type CHECK (spec_sewer_connected IS NULL OR (type IN ('LAND', 'BUILDING', 'COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_has_irrigation_type CHECK (spec_has_irrigation IS NULL OR (type IN ('FARM'))),
    ADD CONSTRAINT ck_property_spec_well_count_range CHECK (spec_well_count IS NULL OR spec_well_count BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_property_spec_well_count_type CHECK (spec_well_count IS NULL OR (type IN ('FARM'))),
    ADD CONSTRAINT ck_property_spec_water_source_enum CHECK (spec_water_source IS NULL OR spec_water_source IN ('NETWORK', 'WELL', 'EXTERNAL_DELIVERY', 'MULTIPLE', 'OTHER', 'NONE')),
    ADD CONSTRAINT ck_property_spec_water_source_type CHECK (spec_water_source IS NULL OR (type IN ('FARM'))),
    ADD CONSTRAINT ck_property_spec_commercial_scope_enum CHECK (spec_commercial_scope IS NULL OR spec_commercial_scope IN ('UNIT', 'WHOLE_BUILDING')),
    ADD CONSTRAINT ck_property_spec_commercial_scope_type CHECK (spec_commercial_scope IS NULL OR (type IN ('COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_commercial_subtype_enum CHECK (spec_commercial_subtype IS NULL OR spec_commercial_subtype IN ('SHOP', 'OFFICE', 'WAREHOUSE', 'SHOWROOM', 'OTHER')),
    ADD CONSTRAINT ck_property_spec_commercial_subtype_type CHECK (spec_commercial_subtype IS NULL OR (type IN ('COMMERCIAL'))),
    ADD CONSTRAINT ck_property_spec_other_type_description_text CHECK (spec_other_type_description IS NULL OR length(btrim(spec_other_type_description)) > 0),
    ADD CONSTRAINT ck_property_spec_other_type_description_type CHECK (spec_other_type_description IS NULL OR (type IN ('OTHER'))),
    ADD CONSTRAINT ck_property_spec_age_pair CHECK ((spec_estimated_age_years IS NULL) = (spec_age_as_of_year IS NULL)),
    ADD CONSTRAINT ck_property_spec_net_gross CHECK (spec_unit_net_area IS NULL OR spec_unit_gross_area IS NULL OR spec_unit_net_area <= spec_unit_gross_area),
    ADD CONSTRAINT ck_property_spec_primary_area CHECK (spec_primary_area_source IS NULL
        OR (spec_primary_area_source='LAND' AND spec_land_area IS NOT NULL AND area=spec_land_area)
        OR (spec_primary_area_source='UNIT_NET' AND spec_unit_net_area IS NOT NULL AND area=spec_unit_net_area)
        OR (spec_primary_area_source='UNIT_GROSS' AND spec_unit_gross_area IS NOT NULL AND area=spec_unit_gross_area)
        OR (spec_primary_area_source='TOTAL_BUILT' AND spec_total_built_area IS NOT NULL AND area=spec_total_built_area)),
    ADD CONSTRAINT ck_property_spec_units_total CHECK (spec_total_units IS NULL OR (
        (spec_residential_units IS NULL OR spec_residential_units <= spec_total_units)
        AND (spec_commercial_units IS NULL OR spec_commercial_units <= spec_total_units)
        AND (spec_residential_units IS NULL OR spec_commercial_units IS NULL
             OR spec_residential_units::bigint + spec_commercial_units::bigint <= spec_total_units)));

COMMIT;
