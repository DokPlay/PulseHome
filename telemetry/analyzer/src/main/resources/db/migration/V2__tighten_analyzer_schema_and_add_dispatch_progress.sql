ALTER TABLE scenarios
    ALTER COLUMN hub_id SET NOT NULL;

ALTER TABLE scenarios
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE sensors
    ALTER COLUMN hub_id SET NOT NULL;

ALTER TABLE conditions
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE conditions
    ALTER COLUMN operation SET NOT NULL;

ALTER TABLE actions
    ALTER COLUMN type SET NOT NULL;

CREATE TABLE IF NOT EXISTS action_dispatches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hub_id VARCHAR NOT NULL,
    scenario_name VARCHAR NOT NULL,
    snapshot_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    sensor_id VARCHAR NOT NULL,
    action_type VARCHAR NOT NULL,
    action_value INTEGER NOT NULL,
    CONSTRAINT uk_action_dispatches_snapshot_action
        UNIQUE (hub_id, scenario_name, snapshot_timestamp, sensor_id, action_type, action_value)
);

CREATE INDEX IF NOT EXISTS idx_action_dispatches_hub_snapshot
    ON action_dispatches (hub_id, snapshot_timestamp);
