-- Manual PostgreSQL migration. Review/backup first. Not automatically executed.
BEGIN;
CREATE TABLE auth_sessions (
    id uuid PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    revoked_at timestamptz
);
CREATE INDEX idx_auth_session_user ON auth_sessions(user_id);
CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    consumed_at timestamptz,
    replaced_by_token_id uuid REFERENCES refresh_tokens(id),
    CHECK ((consumed_at IS NULL AND replaced_by_token_id IS NULL) OR
           (consumed_at IS NOT NULL AND replaced_by_token_id IS NOT NULL))
);
CREATE INDEX idx_refresh_family ON refresh_tokens(session_id);
CREATE INDEX idx_refresh_expiry ON refresh_tokens(expires_at);
COMMIT;
