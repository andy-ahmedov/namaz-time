DROP TABLE IF EXISTS device_health;
ALTER TABLE devices DROP COLUMN IF EXISTS last_seen_at;
