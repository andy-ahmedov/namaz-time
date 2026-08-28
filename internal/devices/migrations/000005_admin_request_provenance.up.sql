ALTER TABLE admin_requests DROP CONSTRAINT admin_requests_operation_check;
DROP TRIGGER admin_requests_append_only ON admin_requests;

UPDATE admin_requests
SET operation = CASE
    WHEN response IS NULL THEN 'set_rollout_group'
    ELSE 'assign_rollout_group'
END
WHERE operation = 'assign_device'
  AND (
      response IS NULL OR
      (response ? 'rollout_group' AND response ? 'assignments')
  );

ALTER TABLE admin_requests ADD CONSTRAINT admin_requests_operation_check
    CHECK (operation IN (
        'issue_pairing',
        'revoke_device',
        'assign_device',
        'set_rollout_group',
        'assign_rollout_group'
    ));

ALTER TABLE devices ADD CONSTRAINT devices_capabilities_count_check
    CHECK (jsonb_array_length(capabilities) <= 128);

CREATE OR REPLACE FUNCTION reject_admin_request_mutation() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.expires_at <= clock_timestamp() - interval '7 days' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'admin_requests is append-only during retention' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER admin_requests_append_only
BEFORE UPDATE OR DELETE ON admin_requests
FOR EACH ROW EXECUTE FUNCTION reject_admin_request_mutation();
