ALTER TABLE devices DROP CONSTRAINT IF EXISTS devices_capabilities_count_check;

DROP TRIGGER admin_requests_append_only ON admin_requests;

UPDATE admin_requests
SET operation = 'assign_device'
WHERE operation IN ('set_rollout_group', 'assign_rollout_group');

ALTER TABLE admin_requests DROP CONSTRAINT admin_requests_operation_check;
ALTER TABLE admin_requests ADD CONSTRAINT admin_requests_operation_check
    CHECK (operation IN ('issue_pairing', 'revoke_device', 'assign_device'));

CREATE OR REPLACE FUNCTION reject_admin_request_mutation() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'admin_requests is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER admin_requests_append_only
BEFORE UPDATE OR DELETE ON admin_requests
FOR EACH ROW EXECUTE FUNCTION reject_admin_request_mutation();
