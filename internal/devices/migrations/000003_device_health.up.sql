ALTER TABLE devices ADD COLUMN last_seen_at timestamptz;

CREATE TABLE device_health (
    device_id text PRIMARY KEY,
    mosque_id text NOT NULL,
    reported_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    reported_snapshot_id text CHECK (reported_snapshot_id IS NULL OR char_length(reported_snapshot_id) BETWEEN 8 AND 128),
    sync_status text NOT NULL CHECK (sync_status IN ('ok', 'offline', 'transient_failure', 'rejected_snapshot', 'auth_failure', 'unknown')),
    coverage_days_remaining smallint NOT NULL CHECK (coverage_days_remaining BETWEEN 0 AND 732),
    clock_mismatch boolean NOT NULL,
    timezone_mismatch boolean NOT NULL,
    storage_health text NOT NULL CHECK (storage_health IN ('ok', 'low', 'critical', 'unknown')),
    memory_health text NOT NULL CHECK (memory_health IN ('ok', 'low', 'critical', 'unknown')),
    boot_mode text NOT NULL CHECK (boot_mode IN ('manual', 'best_effort', 'managed', 'unknown')),
    kiosk_mode text NOT NULL CHECK (kiosk_mode IN ('none', 'best_effort', 'managed', 'unknown')),
    FOREIGN KEY (device_id, mosque_id) REFERENCES devices(id, mosque_id) ON DELETE RESTRICT
);

CREATE INDEX device_health_mosque_seen_idx ON device_health (mosque_id, received_at DESC, device_id);
