CREATE TABLE admin_actors (
    id text PRIMARY KEY CHECK (char_length(id) BETWEEN 8 AND 128),
    display_name text NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 240),
    status text NOT NULL CHECK (status IN ('active', 'suspended')),
    created_at timestamptz NOT NULL
);

CREATE TABLE admin_credentials (
    id text PRIMARY KEY CHECK (char_length(id) BETWEEN 8 AND 160),
    actor_id text NOT NULL REFERENCES admin_actors(id) ON DELETE RESTRICT,
    token_hash bytea NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    created_at timestamptz NOT NULL,
    expires_at timestamptz CHECK (expires_at IS NULL OR expires_at > created_at),
    revoked_at timestamptz
);

CREATE INDEX admin_credentials_actor_idx ON admin_credentials (actor_id, created_at DESC);

CREATE TABLE admin_memberships (
    actor_id text NOT NULL REFERENCES admin_actors(id) ON DELETE RESTRICT,
    mosque_id text REFERENCES mosques(id) ON DELETE RESTRICT,
    role text NOT NULL CHECK (role IN ('service_admin', 'mosque_admin', 'approver', 'viewer_support')),
    created_at timestamptz NOT NULL,
    CHECK (
        (role = 'service_admin' AND mosque_id IS NULL) OR
        (role <> 'service_admin' AND mosque_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX admin_memberships_scope_unique_idx
    ON admin_memberships (actor_id, role, COALESCE(mosque_id, ''));
CREATE INDEX admin_memberships_mosque_role_idx ON admin_memberships (mosque_id, role, actor_id);

CREATE TABLE device_assignments (
    device_id text PRIMARY KEY,
    mosque_id text NOT NULL REFERENCES mosques(id) ON DELETE RESTRICT,
    manifest_version bigint NOT NULL CHECK (manifest_version >= 1),
    snapshot_id text NOT NULL CHECK (char_length(snapshot_id) BETWEEN 8 AND 128),
    snapshot_url text NOT NULL CHECK (char_length(snapshot_url) BETWEEN 1 AND 2048),
    snapshot_sha256 text NOT NULL CHECK (snapshot_sha256 ~ '^[0-9a-f]{64}$'),
    signing_key_id text NOT NULL CHECK (char_length(signing_key_id) BETWEEN 1 AND 128),
    minimum_app_version text CHECK (minimum_app_version IS NULL OR char_length(minimum_app_version) BETWEEN 1 AND 64),
    assigned_by_actor_id text NOT NULL REFERENCES admin_actors(id) ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL,
    FOREIGN KEY (device_id, mosque_id) REFERENCES devices(id, mosque_id) ON DELETE RESTRICT
);

CREATE INDEX device_assignments_mosque_idx ON device_assignments (mosque_id, device_id);

CREATE TABLE admin_requests (
    idempotency_hash bytea PRIMARY KEY CHECK (octet_length(idempotency_hash) = 32),
    request_hash bytea NOT NULL CHECK (octet_length(request_hash) = 32),
    actor_id text NOT NULL REFERENCES admin_actors(id) ON DELETE RESTRICT,
    mosque_id text NOT NULL REFERENCES mosques(id) ON DELETE RESTRICT,
    operation text NOT NULL CHECK (operation IN ('issue_pairing', 'revoke_device', 'assign_device')),
    resource_id text NOT NULL CHECK (char_length(resource_id) BETWEEN 8 AND 128),
    response jsonb CHECK (response IS NULL OR jsonb_typeof(response) = 'object'),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at)
);

CREATE INDEX admin_requests_actor_time_idx ON admin_requests (actor_id, created_at DESC);

CREATE FUNCTION reject_admin_request_mutation() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'admin_requests is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER admin_requests_append_only
BEFORE UPDATE OR DELETE ON admin_requests
FOR EACH ROW EXECUTE FUNCTION reject_admin_request_mutation();

CREATE TRIGGER admin_requests_reject_truncate
BEFORE TRUNCATE ON admin_requests
FOR EACH STATEMENT EXECUTE FUNCTION reject_admin_request_mutation();
