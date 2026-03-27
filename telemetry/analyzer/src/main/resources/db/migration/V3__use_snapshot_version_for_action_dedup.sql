ALTER TABLE action_dispatches
    ADD COLUMN IF NOT EXISTS snapshot_version BIGINT DEFAULT 0;

UPDATE action_dispatches dispatch
SET snapshot_version = ranked.snapshot_version
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY hub_id ORDER BY snapshot_timestamp, id) AS snapshot_version
    FROM action_dispatches
) ranked
WHERE dispatch.id = ranked.id;

ALTER TABLE action_dispatches
    ALTER COLUMN snapshot_version SET NOT NULL;

ALTER TABLE action_dispatches
    DROP CONSTRAINT IF EXISTS uk_action_dispatches_snapshot_action;

ALTER TABLE action_dispatches
    ADD CONSTRAINT uk_action_dispatches_snapshot_action
        UNIQUE (hub_id, scenario_name, snapshot_version, sensor_id, action_type, action_value);

DROP INDEX IF EXISTS idx_action_dispatches_hub_snapshot;

CREATE INDEX IF NOT EXISTS idx_action_dispatches_hub_snapshot
    ON action_dispatches (hub_id, snapshot_version);
