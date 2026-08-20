ALTER TABLE devices ADD COLUMN rollout_group text
    CHECK (
        rollout_group IS NULL OR (
            char_length(rollout_group) BETWEEN 8 AND 64
            AND rollout_group ~ '^[A-Za-z0-9][A-Za-z0-9._-]{7,63}$'
        )
    );

CREATE INDEX devices_mosque_rollout_group_idx
    ON devices (mosque_id, rollout_group, id)
    WHERE rollout_group IS NOT NULL AND status <> 'revoked';
