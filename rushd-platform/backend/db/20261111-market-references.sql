-- P20. Reviewed additive migration. Apply only to an approved isolated database.
BEGIN;
CREATE TABLE market_sources (
 id uuid PRIMARY KEY, title varchar(150) NOT NULL, official_url varchar(1000) NOT NULL,
 period_start date NOT NULL, period_end date NOT NULL, obtained_date date NOT NULL,
 licence_note varchar(2000) NOT NULL, notes varchar(2000), creation_hash varchar(64) NOT NULL,
 original_bytes bytea, original_sha256 varchar(64), original_size bigint,
 archived boolean NOT NULL DEFAULT false, state_reason varchar(1000), revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
 created_by bigint NOT NULL REFERENCES users(id), changed_by bigint NOT NULL REFERENCES users(id),
 created_at timestamp with time zone NOT NULL, updated_at timestamp with time zone NOT NULL,
 CHECK (period_start >= DATE '1900-01-01' AND period_start <= period_end AND period_end <= obtained_date),
 CHECK ((original_bytes IS NULL AND original_sha256 IS NULL AND original_size IS NULL) OR
        (original_bytes IS NOT NULL AND original_sha256 IS NOT NULL AND original_size BETWEEN 1 AND 10485760
         AND octet_length(original_bytes) = original_size))
);
CREATE TABLE market_references (
 id uuid PRIMARY KEY, source_id uuid NOT NULL REFERENCES market_sources(id), source_reference varchar(150) NOT NULL,
 creation_hash varchar(64) NOT NULL, revision bigint NOT NULL CHECK (revision >= 0),
 UNIQUE(source_id, source_reference)
);
CREATE TABLE market_reference_versions (
 reference_id uuid NOT NULL REFERENCES market_references(id), revision bigint NOT NULL CHECK (revision >= 0),
 facts_json text NOT NULL, price numeric(19,2) NOT NULL CHECK (price >= 0.01),
 area numeric(19,2) CHECK (area >= 0.01), archived boolean NOT NULL, state_reason varchar(1000),
 change_reason varchar(1000) NOT NULL, changed_by bigint NOT NULL REFERENCES users(id),
 changed_at timestamp with time zone NOT NULL, PRIMARY KEY(reference_id, revision)
);
CREATE TABLE market_storage_quota (
 id integer PRIMARY KEY CHECK (id = 1), used_bytes bigint NOT NULL CHECK (used_bytes BETWEEN 0 AND 104857600)
);
INSERT INTO market_storage_quota(id, used_bytes) VALUES (1, 0);
CREATE INDEX idx_market_sources_updated ON market_sources(updated_at DESC, id);
CREATE INDEX idx_market_reference_source ON market_references(source_id);
COMMIT;
