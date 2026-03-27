package ru.yandex.practicum.telemetry.analyzer.service;

import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionOperationAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ScenarioDefinition;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Service
public class SnapshotAnalyzerService {

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
        List<ScenarioDefinition> scenarios = hubConfigurationService.getScenarios(snapshot.getHubId());
        if (scenarios.isEmpty()) {
            return;
        }

        Map<String, SensorStateAvro> sensorStates = snapshot.getSensorsState();
        for (ScenarioDefinition scenario : scenarios) {
            if (!matchesAllConditions(sensorStates, scenario.conditions())) {
                continue;
            }

            for (ActionSpec action : scenario.actions()) {
                if (actionDispatchTracker.isAlreadyDispatched(snapshot.getHubId(), scenario.name(), snapshot.getTimestamp(), action)) {
                    continue;
                }

                deviceActionDispatcher.dispatch(snapshot.getHubId(), scenario.name(), snapshot.getTimestamp(), action);
                actionDispatchTracker.markDispatched(snapshot.getHubId(), scenario.name(), snapshot.getTimestamp(), action);
            }
        }

        // Keep only the current snapshot's dispatch progress for the hub to bound dedupe state growth.
        actionDispatchTracker.pruneOlderSnapshots(snapshot.getHubId(), snapshot.getTimestamp());
    }

    private boolean matchesAllConditions(Map<String, SensorStateAvro> sensorStates, List<ConditionSpec> conditions) {
        for (ConditionSpec condition : conditions) {
            SensorStateAvro sensorState = sensorStates.get(condition.sensorId());
            if (sensorState == null) {
                return false;
            }

            Object sensorData = sensorState.getData();
            if (!(sensorData instanceof SpecificRecordBase specificRecordBase)) {
                return false;
            }

            OptionalInt actualValue = extractConditionValue(specificRecordBase, condition.type());
            if (actualValue.isEmpty()) {
                return false;
            }

            if (!matches(actualValue.getAsInt(), condition.operation(), condition.value())) {
                return false;
            }
        }
        return true;
    }

    private OptionalInt extractConditionValue(SpecificRecordBase sensorData, ConditionTypeAvro conditionType) {
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

    private boolean matches(int actualValue, ConditionOperationAvro operation, Integer expectedValue) {
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
