CREATE TABLE mosques (
    id text PRIMARY KEY CHECK (char_length(id) BETWEEN 8 AND 128),
    name text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 240),
    timezone_id text NOT NULL CHECK (char_length(timezone_id) BETWEEN 3 AND 64),
    status text NOT NULL CHECK (status IN ('active', 'suspended')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at)
);

CREATE TABLE devices (
    id text PRIMARY KEY CHECK (char_length(id) BETWEEN 8 AND 128),
    mosque_id text NOT NULL REFERENCES mosques(id) ON DELETE RESTRICT,
    status text NOT NULL CHECK (status IN ('pending', 'active', 'revoked')),
    token_hash bytea UNIQUE CHECK (token_hash IS NULL OR octet_length(token_hash) = 32),
    installation_public_key text CHECK (installation_public_key IS NULL OR char_length(installation_public_key) <= 4096),
    app_version text CHECK (app_version IS NULL OR char_length(app_version) BETWEEN 1 AND 64),
    os_version text CHECK (os_version IS NULL OR char_length(os_version) BETWEEN 1 AND 128),
    model text CHECK (model IS NULL OR char_length(model) BETWEEN 1 AND 240),
    capabilities jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(capabilities) = 'array'),
    created_at timestamptz NOT NULL,
    paired_at timestamptz,
    revoked_at timestamptz,
	UNIQUE (id, mosque_id),
    CHECK (
        (status = 'pending' AND token_hash IS NULL AND paired_at IS NULL AND revoked_at IS NULL) OR
        (status = 'active' AND token_hash IS NOT NULL AND paired_at IS NOT NULL AND revoked_at IS NULL) OR
        (status = 'revoked' AND token_hash IS NULL AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX devices_mosque_status_idx ON devices (mosque_id, status, id);

CREATE TABLE pairing_codes (
    id text PRIMARY KEY CHECK (char_length(id) BETWEEN 8 AND 128),
    device_id text NOT NULL,
    mosque_id text NOT NULL REFERENCES mosques(id) ON DELETE RESTRICT,
    code_hash bytea NOT NULL UNIQUE CHECK (octet_length(code_hash) = 32),
    expires_at timestamptz NOT NULL,
    max_uses smallint NOT NULL DEFAULT 1 CHECK (max_uses = 1),
    uses_count smallint NOT NULL DEFAULT 0 CHECK (uses_count BETWEEN 0 AND max_uses),
    consumed_at timestamptz,
    revoked_at timestamptz,
    issued_by_actor_id text NOT NULL CHECK (char_length(issued_by_actor_id) BETWEEN 8 AND 128),
    created_at timestamptz NOT NULL CHECK (expires_at > created_at),
    FOREIGN KEY (device_id, mosque_id) REFERENCES devices(id, mosque_id) ON DELETE RESTRICT,
    CHECK ((uses_count = 0 AND consumed_at IS NULL) OR (uses_count = 1 AND consumed_at IS NOT NULL))
);

CREATE UNIQUE INDEX pairing_codes_one_open_per_device_idx
    ON pairing_codes (device_id)
    WHERE uses_count = 0 AND revoked_at IS NULL;
CREATE INDEX pairing_codes_mosque_created_idx ON pairing_codes (mosque_id, created_at DESC, id);

CREATE TABLE pairing_rate_buckets (
    bucket_kind text NOT NULL CHECK (bucket_kind IN ('code', 'device', 'source')),
    bucket_hash bytea NOT NULL CHECK (octet_length(bucket_hash) = 32),
    window_started_at timestamptz NOT NULL,
    attempts integer NOT NULL CHECK (attempts >= 1),
    updated_at timestamptz NOT NULL CHECK (updated_at >= window_started_at),
    PRIMARY KEY (bucket_kind, bucket_hash)
);

CREATE INDEX pairing_rate_buckets_updated_idx ON pairing_rate_buckets (updated_at);

CREATE TABLE audit_events (
    id text PRIMARY KEY CHECK (char_length(id) BETWEEN 8 AND 128),
    occurred_at timestamptz NOT NULL,
    actor_type text NOT NULL CHECK (actor_type IN ('admin', 'device', 'system')),
    actor_id text NOT NULL CHECK (char_length(actor_id) BETWEEN 8 AND 128),
    mosque_id text REFERENCES mosques(id) ON DELETE RESTRICT,
    action text NOT NULL CHECK (char_length(action) BETWEEN 3 AND 128),
    entity_type text NOT NULL CHECK (char_length(entity_type) BETWEEN 3 AND 128),
    entity_id text NOT NULL CHECK (char_length(entity_id) BETWEEN 8 AND 128),
    before_hash bytea CHECK (before_hash IS NULL OR octet_length(before_hash) = 32),
    after_hash bytea CHECK (after_hash IS NULL OR octet_length(after_hash) = 32),
    reason text NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 512),
    request_id text NOT NULL CHECK (char_length(request_id) BETWEEN 8 AND 128)
);

CREATE INDEX audit_events_mosque_time_idx ON audit_events (mosque_id, occurred_at DESC, id);
CREATE INDEX audit_events_entity_time_idx ON audit_events (entity_type, entity_id, occurred_at DESC, id);

CREATE FUNCTION reject_audit_event_mutation() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER audit_events_append_only
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();

CREATE TRIGGER audit_events_reject_truncate
BEFORE TRUNCATE ON audit_events
FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_event_mutation();
