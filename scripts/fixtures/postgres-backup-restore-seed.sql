BEGIN;

INSERT INTO mosques (id, name, timezone_id, status, created_at, updated_at)
VALUES (
    'mosque-restore-0001',
    'Synthetic Restore Mosque',
    'Europe/Ulyanovsk',
    'active',
    '2026-08-20T12:00:00Z',
    '2026-08-20T12:00:00Z'
);

INSERT INTO admin_actors (id, display_name, status, created_at)
VALUES ('actor-restore-admin-0001', 'Synthetic Restore Admin', 'active', '2026-08-20T12:00:00Z');

INSERT INTO admin_credentials (id, actor_id, token_hash, created_at)
VALUES (
    'credential-restore-admin-0001',
    'actor-restore-admin-0001',
    decode('7ae6c111573b71268a29c7e237bb801a3d476c05dce07fa7fdbbb26c08297bfb', 'hex'),
    '2026-08-20T12:00:00Z'
);

INSERT INTO admin_memberships (actor_id, mosque_id, role, created_at)
VALUES ('actor-restore-admin-0001', 'mosque-restore-0001', 'viewer_support', '2026-08-20T12:00:00Z');

INSERT INTO devices (
    id, mosque_id, status, token_hash, installation_public_key,
    app_version, os_version, model, capabilities, created_at, paired_at,
    last_seen_at, rollout_group
) VALUES (
    'device-restore-0001',
    'mosque-restore-0001',
    'active',
    decode('2ef62afe91b4e88047d3e2953996a386d9d759c55b064c5daf573345a18e0258', 'hex'),
    'synthetic-public-key-not-a-secret',
    '1.0.0-restore',
    'Android 14',
    'Synthetic TV',
    '["heartbeat-v1"]'::jsonb,
    '2026-08-20T12:00:00Z',
    '2026-08-20T12:01:00Z',
    '2026-08-20T12:20:00Z',
    'canary.restore'
);

INSERT INTO pairing_codes (
    id, device_id, mosque_id, code_hash, expires_at, uses_count,
    consumed_at, issued_by_actor_id, created_at
) VALUES (
    'pairing-restore-0001',
    'device-restore-0001',
    'mosque-restore-0001',
    decode('bcf4719d4fb337e821298e9c7084c3a4671efad4c6691dccaf425fdcbedca54e', 'hex'),
    '2026-08-20T12:15:00Z',
    1,
    '2026-08-20T12:01:00Z',
    'actor-restore-admin-0001',
    '2026-08-20T12:00:00Z'
);

INSERT INTO pairing_rate_buckets (
    bucket_kind, bucket_hash, window_started_at, attempts, updated_at
) VALUES (
    'device',
    decode('1111111111111111111111111111111111111111111111111111111111111111', 'hex'),
    '2026-08-20T12:00:00Z',
    2,
    '2026-08-20T12:01:00Z'
);

INSERT INTO device_assignments (
    device_id, mosque_id, manifest_version, snapshot_id, snapshot_url,
    snapshot_sha256, signing_key_id, minimum_app_version,
    assigned_by_actor_id, assigned_at
) VALUES (
    'device-restore-0001',
    'mosque-restore-0001',
    7,
    'snapshot-restore-0001',
    'https://api.example.invalid/v1/snapshots/snapshot-restore-0001',
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    'restore-signing-key-01',
    '1.0.0',
    'actor-restore-admin-0001',
    '2026-08-20T12:10:00Z'
);

INSERT INTO device_health (
    device_id, mosque_id, reported_at, received_at, reported_snapshot_id,
    sync_status, coverage_days_remaining, clock_mismatch, timezone_mismatch,
    storage_health, memory_health, boot_mode, kiosk_mode
) VALUES (
    'device-restore-0001',
    'mosque-restore-0001',
    '2026-08-20T12:19:55Z',
    '2026-08-20T12:20:00Z',
    'snapshot-restore-0001',
    'ok',
    180,
    false,
    false,
    'ok',
    'ok',
    'managed',
    'managed'
);

INSERT INTO audit_events (
    id, occurred_at, actor_type, actor_id, mosque_id, action, entity_type,
    entity_id, before_hash, after_hash, reason, request_id
) VALUES (
    'audit-restore-0001',
    '2026-08-20T12:10:00Z',
    'admin',
    'actor-restore-admin-0001',
    'mosque-restore-0001',
    'device.assignment_changed',
    'device_assignment',
    'device-restore-0001',
    decode('2222222222222222222222222222222222222222222222222222222222222222', 'hex'),
    decode('3333333333333333333333333333333333333333333333333333333333333333', 'hex'),
    'synthetic backup restore fixture',
    'request-restore-0001'
);

INSERT INTO admin_requests (
    idempotency_hash, request_hash, actor_id, mosque_id, operation,
    resource_id, response, created_at, expires_at
) VALUES (
    decode('4444444444444444444444444444444444444444444444444444444444444444', 'hex'),
    decode('5555555555555555555555555555555555555555555555555555555555555555', 'hex'),
    'actor-restore-admin-0001',
    'mosque-restore-0001',
    'assign_device',
    'device-restore-0001',
    '{"manifest_version":7,"snapshot_id":"snapshot-restore-0001"}'::jsonb,
    '2026-08-20T12:10:00Z',
    '2099-01-01T00:00:00Z'
);

COMMIT;
