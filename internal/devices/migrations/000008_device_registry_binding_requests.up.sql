CREATE TABLE device_registry_binding_requests (
    id text PRIMARY KEY CHECK (char_length(id) BETWEEN 8 AND 128),
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    city_id text NOT NULL,
    policy_id text NOT NULL,
    choice_id text NOT NULL CHECK (char_length(choice_id) BETWEEN 8 AND 160),
    mosque_id text NOT NULL REFERENCES mosques(id),
    device_id text NOT NULL,
    local_date date NOT NULL,
    resolution_tier text NOT NULL CHECK (resolution_tier IN (
        'exact_city_timetable',
        'regional_official_timetable',
        'approved_regional_calculation_profile',
        'explicitly_configured_fallback'
    )),
    status text NOT NULL CHECK (status = 'pending_review'),
    selection_sha256 text NOT NULL CHECK (selection_sha256 ~ '^[0-9a-f]{64}$'),
    origin text NOT NULL CHECK (origin = 'local_tv_operator'),
    interaction_id text NOT NULL CHECK (char_length(interaction_id) BETWEEN 8 AND 128),
    requested_at timestamptz NOT NULL,
    UNIQUE (device_id, interaction_id),
    FOREIGN KEY (device_id, mosque_id) REFERENCES devices(id, mosque_id),
    FOREIGN KEY (revision_id, city_id) REFERENCES registry_cities(revision_id, id),
    FOREIGN KEY (revision_id, policy_id, mosque_id)
        REFERENCES registry_policy_mosques(revision_id, policy_id, mosque_id)
);

CREATE INDEX device_registry_binding_requests_mosque_time_idx
    ON device_registry_binding_requests (mosque_id, requested_at DESC, id);
CREATE INDEX device_registry_binding_requests_device_time_idx
    ON device_registry_binding_requests (device_id, requested_at DESC, id);

CREATE TRIGGER device_registry_binding_requests_append_only_row
BEFORE UPDATE OR DELETE ON device_registry_binding_requests
FOR EACH ROW EXECUTE FUNCTION reject_registry_row_mutation();

CREATE TRIGGER device_registry_binding_requests_append_only_truncate
BEFORE TRUNCATE ON device_registry_binding_requests
FOR EACH STATEMENT EXECUTE FUNCTION reject_registry_truncate();
