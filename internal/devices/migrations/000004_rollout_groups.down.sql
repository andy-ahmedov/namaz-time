DROP INDEX IF EXISTS devices_mosque_rollout_group_idx;
ALTER TABLE devices DROP COLUMN IF EXISTS rollout_group;
