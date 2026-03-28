package ru.yandex.practicum.telemetry.serialization;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvroSchemaCompatibilityTest {

    @Test
    void shouldKeepHubEventUnionBranchOrderStable() {
        assertEquals(
                List.of(
                        "DeviceAddedEventAvro",
                        "DeviceRemovedEventAvro",
                        "ScenarioAddedEventAvro",
                        "ScenarioRemovedEventAvro"
                ),
                payloadBranchNames(HubEventAvro.getClassSchema(), "payload")
        );
    }

    @Test
    void shouldOnlyAppendNewSensorEventUnionBranches() {
        assertEquals(
                List.of(
                        "ClimateSensorAvro",
                        "LightSensorAvro",
                        "MotionSensorAvro",
                        "SwitchSensorAvro",
                        "TemperatureSensorAvro",
                        "TemperatureSensorPayloadAvro"
                ),
                payloadBranchNames(SensorEventAvro.getClassSchema(), "payload")
        );
    }

    private List<String> payloadBranchNames(org.apache.avro.Schema schema, String fieldName) {
        return schema.getField(fieldName).schema().getTypes().stream()
                .map(org.apache.avro.Schema::getName)
                .toList();
    }
}
