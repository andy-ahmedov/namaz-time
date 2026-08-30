DROP TABLE IF EXISTS registry_binding_requests;

DROP TRIGGER admin_requests_append_only ON admin_requests;
DELETE FROM admin_requests WHERE operation = 'request_registry_binding';
ALTER TABLE admin_requests DROP CONSTRAINT admin_requests_operation_check;
ALTER TABLE admin_requests ADD CONSTRAINT admin_requests_operation_check
    CHECK (operation IN (
        'issue_pairing',
        'revoke_device',
        'assign_device',
        'set_rollout_group',
        'assign_rollout_group'
    ));
CREATE TRIGGER admin_requests_append_only
BEFORE UPDATE OR DELETE ON admin_requests
FOR EACH ROW EXECUTE FUNCTION reject_admin_request_mutation();
