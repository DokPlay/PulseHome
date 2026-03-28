ALTER TABLE scenarios
    ALTER COLUMN hub_id TYPE VARCHAR(255),
    ALTER COLUMN name TYPE VARCHAR(255);

ALTER TABLE sensors
    ALTER COLUMN id TYPE VARCHAR(255),
    ALTER COLUMN hub_id TYPE VARCHAR(255);

ALTER TABLE conditions
    ALTER COLUMN type TYPE VARCHAR(64),
    ALTER COLUMN operation TYPE VARCHAR(64);

ALTER TABLE actions
    ALTER COLUMN type TYPE VARCHAR(64);

ALTER TABLE action_dispatches
    ALTER COLUMN hub_id TYPE VARCHAR(255),
    ALTER COLUMN scenario_name TYPE VARCHAR(255),
    ALTER COLUMN sensor_id TYPE VARCHAR(255),
    ALTER COLUMN action_type TYPE VARCHAR(64);

ALTER TABLE scenario_conditions
    DROP CONSTRAINT IF EXISTS scenario_conditions_scenario_id_fkey,
    DROP CONSTRAINT IF EXISTS scenario_conditions_sensor_id_fkey,
    DROP CONSTRAINT IF EXISTS scenario_conditions_condition_id_fkey;

ALTER TABLE scenario_conditions
    ADD CONSTRAINT scenario_conditions_scenario_id_fkey
        FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE,
    ADD CONSTRAINT scenario_conditions_sensor_id_fkey
        FOREIGN KEY (sensor_id) REFERENCES sensors(id) ON DELETE CASCADE,
    ADD CONSTRAINT scenario_conditions_condition_id_fkey
        FOREIGN KEY (condition_id) REFERENCES conditions(id) ON DELETE CASCADE;

ALTER TABLE scenario_actions
    DROP CONSTRAINT IF EXISTS scenario_actions_scenario_id_fkey,
    DROP CONSTRAINT IF EXISTS scenario_actions_sensor_id_fkey,
    DROP CONSTRAINT IF EXISTS scenario_actions_action_id_fkey;

ALTER TABLE scenario_actions
    ADD CONSTRAINT scenario_actions_scenario_id_fkey
        FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE,
    ADD CONSTRAINT scenario_actions_sensor_id_fkey
        FOREIGN KEY (sensor_id) REFERENCES sensors(id) ON DELETE CASCADE,
    ADD CONSTRAINT scenario_actions_action_id_fkey
        FOREIGN KEY (action_id) REFERENCES actions(id) ON DELETE CASCADE;
