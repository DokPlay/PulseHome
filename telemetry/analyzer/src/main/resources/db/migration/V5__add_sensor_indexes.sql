CREATE INDEX IF NOT EXISTS idx_scenario_conditions_sensor_id
    ON scenario_conditions (sensor_id);

CREATE INDEX IF NOT EXISTS idx_scenario_actions_sensor_id
    ON scenario_actions (sensor_id);
