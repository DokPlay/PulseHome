package ru.yandex.practicum.telemetry.serialization;

import org.junit.jupiter.api.Test;
import org.apache.avro.Schema;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldKeepEnumDefaultsDefinedForForwardCompatibility() throws IOException {
        assertEquals("MOTION_SENSOR", enumDefault("src/main/avro/01-DeviceTypeAvro.avsc"));
        assertEquals("MOTION", enumDefault("src/main/avro/02-ConditionTypeAvro.avsc"));
        assertEquals("EQUALS", enumDefault("src/main/avro/03-ConditionOperationAvro.avsc"));
        assertEquals("ACTIVATE", enumDefault("src/main/avro/04-ActionTypeAvro.avsc"));
    }

    @Test
    void shouldKeepSensorPayloadFieldDefaultsDefinedForSchemaEvolution() throws IOException {
        assertAllFieldsHaveDefaults("src/main/avro/12-ClimateSensorAvro.avsc");
        assertAllFieldsHaveDefaults("src/main/avro/13-LightSensorAvro.avsc");
        assertAllFieldsHaveDefaults("src/main/avro/14-MotionSensorAvro.avsc");
        assertAllFieldsHaveDefaults("src/main/avro/15-SwitchSensorAvro.avsc");
        assertAllFieldsHaveDefaults("src/main/avro/16-TemperatureSensorAvro.avsc");
        assertAllFieldsHaveDefaults("src/main/avro/19-TemperatureSensorPayloadAvro.avsc");
    }

    private List<String> payloadBranchNames(org.apache.avro.Schema schema, String fieldName) {
        return schema.getField(fieldName).schema().getTypes().stream()
                .map(org.apache.avro.Schema::getName)
                .toList();
    }

    private String enumDefault(String path) throws IOException {
        Schema schema = new Schema.Parser().parse(Files.readString(Path.of(path)));
        return schema.getEnumDefault();
    }

    private void assertAllFieldsHaveDefaults(String path) throws IOException {
        Schema schema = new Schema.Parser().parse(Files.readString(Path.of(path)));
        schema.getFields().forEach(field ->
                assertTrue(field.hasDefaultValue(),
                        () -> "Field '%s' in schema '%s' must define a default value".formatted(field.name(), path))
        );
    }
}
