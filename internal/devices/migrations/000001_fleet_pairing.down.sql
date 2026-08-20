DROP TRIGGER IF EXISTS audit_events_append_only ON audit_events;
DROP TRIGGER IF EXISTS audit_events_reject_truncate ON audit_events;
DROP FUNCTION IF EXISTS reject_audit_event_mutation();
DROP TABLE IF EXISTS audit_events;
DROP TABLE IF EXISTS pairing_rate_buckets;
DROP TABLE IF EXISTS pairing_codes;
DROP TABLE IF EXISTS devices;
DROP TABLE IF EXISTS mosques;
