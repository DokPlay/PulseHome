CREATE OR REPLACE FUNCTION check_hub_id()
RETURNS TRIGGER AS
'
BEGIN
    IF (SELECT hub_id FROM scenarios WHERE id = NEW.scenario_id) IS DISTINCT FROM (SELECT hub_id FROM sensors WHERE id = NEW.sensor_id) THEN
        RAISE EXCEPTION ''Hub IDs do not match for scenario_id % and sensor_id %'', NEW.scenario_id, NEW.sensor_id;
    END IF;
    RETURN NEW;
END;
'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_bi_scenario_conditions_hub_id_check ON scenario_conditions;
DROP TRIGGER IF EXISTS tr_bi_scenario_actions_hub_id_check ON scenario_actions;

CREATE TRIGGER tr_biu_scenario_conditions_hub_id_check
BEFORE INSERT OR UPDATE ON scenario_conditions
FOR EACH ROW
EXECUTE FUNCTION check_hub_id();

CREATE TRIGGER tr_biu_scenario_actions_hub_id_check
BEFORE INSERT OR UPDATE ON scenario_actions
FOR EACH ROW
EXECUTE FUNCTION check_hub_id();
