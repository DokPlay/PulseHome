ALTER TABLE action_dispatches
    ALTER COLUMN action_value DROP NOT NULL;

ALTER TABLE action_dispatches
    DROP CONSTRAINT IF EXISTS uk_action_dispatches_snapshot_action;

DROP INDEX IF EXISTS uk_action_dispatches_snapshot_action;
DROP INDEX IF EXISTS uk_action_dispatches_snapshot_action_with_value;
DROP INDEX IF EXISTS uk_action_dispatches_snapshot_action_without_value;

CREATE UNIQUE INDEX IF NOT EXISTS uk_action_dispatches_snapshot_action_with_value
    ON action_dispatches (hub_id, scenario_name, snapshot_version, sensor_id, action_type, action_value)
    WHERE action_value IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_action_dispatches_snapshot_action_without_value
    ON action_dispatches (hub_id, scenario_name, snapshot_version, sensor_id, action_type)
    WHERE action_value IS NULL;
