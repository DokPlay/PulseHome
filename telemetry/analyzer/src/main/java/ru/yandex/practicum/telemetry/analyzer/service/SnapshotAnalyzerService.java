package ru.yandex.practicum.telemetry.analyzer.service;

import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionOperation;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionType;
import ru.yandex.practicum.telemetry.analyzer.model.ScenarioDefinition;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Service
public class SnapshotAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotAnalyzerService.class);

    private final HubConfigurationService hubConfigurationService;
    private final DeviceActionDispatcher deviceActionDispatcher;
    private final ActionDispatchTracker actionDispatchTracker;

    public SnapshotAnalyzerService(HubConfigurationService hubConfigurationService,
                                   DeviceActionDispatcher deviceActionDispatcher,
                                   ActionDispatchTracker actionDispatchTracker) {
        this.hubConfigurationService = hubConfigurationService;
        this.deviceActionDispatcher = deviceActionDispatcher;
        this.actionDispatchTracker = actionDispatchTracker;
    }

    public void analyze(SensorsSnapshotAvro snapshot) {
        try {
            List<ScenarioDefinition> scenarios = hubConfigurationService.getScenarios(snapshot.getHubId());
            if (scenarios.isEmpty()) {
                log.debug("No scenarios configured for hub snapshot. hubId={}, timestamp={}",
                        snapshot.getHubId(), snapshot.getTimestamp());
                return;
            }

            Map<String, SensorStateAvro> sensorStates = snapshot.getSensorsState();
            scenarios.stream()
                    .filter(scenario -> matchesAllConditions(sensorStates, scenario))
                    .forEach(scenario -> dispatchScenarioActions(snapshot, scenario));
        } finally {
            // Keep only the current snapshot's dispatch progress for the hub to bound dedupe state growth.
            actionDispatchTracker.pruneOlderSnapshots(snapshot.getHubId(), snapshot.getTimestamp());
        }
    }

    private void dispatchScenarioActions(SensorsSnapshotAvro snapshot, ScenarioDefinition scenario) {
        log.info("Scenario matched. hubId={}, scenario={}, actions={}",
                snapshot.getHubId(), scenario.name(), scenario.actions().size());

        scenario.actions().stream()
                .filter(action -> shouldDispatchAction(snapshot, scenario, action))
                .forEach(action -> {
                    log.debug("Dispatching scenario action. hubId={}, scenario={}, sensorId={}, actionType={}",
                            snapshot.getHubId(), scenario.name(), action.sensorId(), action.type());
                    deviceActionDispatcher.dispatch(snapshot.getHubId(), scenario.name(), snapshot.getTimestamp(), action);
                    actionDispatchTracker.markDispatched(snapshot.getHubId(), scenario.name(), snapshot.getTimestamp(), action);
                });
    }

    private boolean shouldDispatchAction(SensorsSnapshotAvro snapshot, ScenarioDefinition scenario, ActionSpec action) {
        boolean alreadyDispatched = actionDispatchTracker.isAlreadyDispatched(
                snapshot.getHubId(),
                scenario.name(),
                snapshot.getTimestamp(),
                action
        );
        if (alreadyDispatched) {
            log.debug("Skipping already dispatched action. hubId={}, scenario={}, sensorId={}, actionType={}",
                    snapshot.getHubId(), scenario.name(), action.sensorId(), action.type());
        }
        return !alreadyDispatched;
    }

    private boolean matchesAllConditions(Map<String, SensorStateAvro> sensorStates, ScenarioDefinition scenario) {
        return scenario.conditions().stream()
                .allMatch(condition -> matchesCondition(sensorStates, scenario, condition));
    }

    private boolean matchesCondition(Map<String, SensorStateAvro> sensorStates,
                                     ScenarioDefinition scenario,
                                     ConditionSpec condition) {
        SensorStateAvro sensorState = sensorStates.get(condition.sensorId());
        if (sensorState == null) {
            log.debug("Scenario condition skipped because sensor state is missing. hubId={}, scenario={}, sensorId={}, conditionType={}",
                    scenario.hubId(), scenario.name(), condition.sensorId(), condition.type());
            return false;
        }

        Object sensorData = sensorState.getData();
        if (!(sensorData instanceof SpecificRecordBase specificRecordBase)) {
            log.warn("Scenario condition received unsupported sensor payload. hubId={}, scenario={}, sensorId={}, payloadType={}",
                    scenario.hubId(), scenario.name(), condition.sensorId(), sensorData == null ? "null" : sensorData.getClass().getName());
            return false;
        }

        OptionalInt actualValue = extractConditionValue(specificRecordBase, condition.type());
        if (actualValue.isEmpty()) {
            log.debug("Scenario condition could not extract value from sensor payload. hubId={}, scenario={}, sensorId={}, conditionType={}, payloadType={}",
                    scenario.hubId(), scenario.name(), condition.sensorId(), condition.type(), sensorData.getClass().getSimpleName());
            return false;
        }

        boolean matches = matches(actualValue.getAsInt(), condition.operation(), condition.value());
        if (!matches) {
            log.debug("Scenario condition did not match. hubId={}, scenario={}, sensorId={}, conditionType={}, operation={}, expectedValue={}, actualValue={}",
                    scenario.hubId(), scenario.name(), condition.sensorId(), condition.type(), condition.operation(),
                    condition.value(), actualValue.getAsInt());
        }
        return matches;
    }

    private OptionalInt extractConditionValue(SpecificRecordBase sensorData, ConditionType conditionType) {
        return switch (conditionType) {
            case MOTION -> sensorData instanceof MotionSensorAvro motionSensor
                    ? OptionalInt.of(motionSensor.getMotion() ? 1 : 0)
                    : OptionalInt.empty();
            case LUMINOSITY -> sensorData instanceof LightSensorAvro lightSensor
                    ? OptionalInt.of(lightSensor.getLuminosity())
                    : OptionalInt.empty();
            case SWITCH -> sensorData instanceof SwitchSensorAvro switchSensor
                    ? OptionalInt.of(switchSensor.getState() ? 1 : 0)
                    : OptionalInt.empty();
            case TEMPERATURE -> {
                if (sensorData instanceof TemperatureSensorAvro temperatureSensor) {
                    yield OptionalInt.of(temperatureSensor.getTemperatureC());
                }
                if (sensorData instanceof ClimateSensorAvro climateSensor) {
                    yield OptionalInt.of(climateSensor.getTemperatureC());
                }
                yield OptionalInt.empty();
            }
            case CO2LEVEL -> sensorData instanceof ClimateSensorAvro climateSensor
                    ? OptionalInt.of(climateSensor.getCo2Level())
                    : OptionalInt.empty();
            case HUMIDITY -> sensorData instanceof ClimateSensorAvro climateSensor
                    ? OptionalInt.of(climateSensor.getHumidity())
                    : OptionalInt.empty();
        };
    }

    private boolean matches(int actualValue, ConditionOperation operation, Integer expectedValue) {
        if (expectedValue == null) {
            return false;
        }

        return switch (operation) {
            case EQUALS -> actualValue == expectedValue;
            case GREATER_THAN -> actualValue > expectedValue;
            case LOWER_THAN -> actualValue < expectedValue;
        };
    }
}
