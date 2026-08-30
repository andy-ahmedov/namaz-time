ALTER TABLE admin_requests DROP CONSTRAINT admin_requests_operation_check;
ALTER TABLE admin_requests ADD CONSTRAINT admin_requests_operation_check
    CHECK (operation IN (
        'issue_pairing',
        'revoke_device',
        'assign_device',
        'set_rollout_group',
        'assign_rollout_group',
        'request_registry_binding'
    ));

CREATE TABLE registry_binding_requests (
    id text PRIMARY KEY CHECK (char_length(id) BETWEEN 8 AND 128),
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    city_id text NOT NULL,
    policy_id text NOT NULL,
    mosque_id text NOT NULL REFERENCES mosques(id),
    local_date date NOT NULL,
    resolution_tier text NOT NULL CHECK (resolution_tier IN (
        'exact_city_timetable',
        'regional_official_timetable',
        'approved_regional_calculation_profile',
        'explicitly_configured_fallback'
    )),
    status text NOT NULL CHECK (status = 'pending_review'),
    selection_sha256 text NOT NULL CHECK (selection_sha256 ~ '^[0-9a-f]{64}$'),
    requested_by_actor_id text NOT NULL REFERENCES admin_actors(id),
    reason text NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 512),
    request_id text NOT NULL CHECK (char_length(request_id) BETWEEN 8 AND 128),
    requested_at timestamptz NOT NULL,
    FOREIGN KEY (revision_id, city_id) REFERENCES registry_cities(revision_id, id),
    FOREIGN KEY (revision_id, policy_id, mosque_id)
        REFERENCES registry_policy_mosques(revision_id, policy_id, mosque_id)
);

CREATE INDEX registry_binding_requests_mosque_time_idx
    ON registry_binding_requests (mosque_id, requested_at DESC, id);

CREATE TRIGGER registry_binding_requests_append_only_row
BEFORE UPDATE OR DELETE ON registry_binding_requests
FOR EACH ROW EXECUTE FUNCTION reject_registry_row_mutation();

CREATE TRIGGER registry_binding_requests_append_only_truncate
BEFORE TRUNCATE ON registry_binding_requests
FOR EACH STATEMENT EXECUTE FUNCTION reject_registry_truncate();
