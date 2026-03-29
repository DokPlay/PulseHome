package ru.yandex.practicum.telemetry.aggregator.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorPayloadAvro;
import ru.yandex.practicum.telemetry.serialization.AvroBinarySerializer;
import ru.yandex.practicum.telemetry.serialization.SensorsSnapshotDeserializer;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensorsSnapshotDeserializerTest {

    @Test
    void shouldDeserializeSingleObjectEncodedSnapshot() {
        SensorsSnapshotAvro snapshot = snapshot();
        try (SensorsSnapshotDeserializer deserializer = new SensorsSnapshotDeserializer()) {
            byte[] bytes = AvroBinarySerializer.serialize(snapshot);
            SensorsSnapshotAvro deserialized = deserializer.deserialize("telemetry.snapshots.v1", bytes);

            assertThat(deserialized.getHubId()).isEqualTo("hub-1");
            assertThat(deserialized.getTimestamp()).isEqualTo(snapshot.getTimestamp());
            assertThat(deserialized.getSensorsState()).isEqualTo(snapshot.getSensorsState());
        }
    }

    @Test
    void shouldDeserializeLegacyRawSnapshot() {
        SensorsSnapshotAvro snapshot = snapshot();
        try (SensorsSnapshotDeserializer deserializer = new SensorsSnapshotDeserializer()) {
            byte[] bytes = AvroBinarySerializer.serializeLegacy(snapshot);
            SensorsSnapshotAvro deserialized = deserializer.deserialize("telemetry.snapshots.v1", bytes);

            assertThat(deserialized.getHubId()).isEqualTo("hub-1");
            assertThat(deserialized.getTimestamp()).isEqualTo(snapshot.getTimestamp());
            assertThat(deserialized.getSensorsState()).isEqualTo(snapshot.getSensorsState());
        }
    }

    private SensorsSnapshotAvro snapshot() {
        Instant timestamp = Instant.parse("2024-08-06T15:11:24.157Z");
        return SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(9)
                .setTimestamp(timestamp)
                .setSensorsState(Map.of(
                        "sensor.climate.1",
                        SensorStateAvro.newBuilder()
                                .setTimestamp(timestamp)
                                .setData(ClimateSensorAvro.newBuilder()
                                        .setTemperatureC(22)
                                        .setHumidity(43)
                                        .setCo2Level(550)
                                        .build())
                                .build(),
                        "sensor.light.1",
                        SensorStateAvro.newBuilder()
                                .setTimestamp(timestamp)
                                .setData(LightSensorAvro.newBuilder()
                                        .setLinkQuality(78)
                                        .setLuminosity(40)
                                        .build())
                                .build(),
                        "sensor.motion.1",
                        SensorStateAvro.newBuilder()
                                .setTimestamp(timestamp)
                                .setData(MotionSensorAvro.newBuilder()
                                        .setLinkQuality(87)
                                        .setMotion(true)
                                        .setVoltage(220)
                                        .build())
                                .build(),
                        "sensor.switch.1",
                        SensorStateAvro.newBuilder()
                                .setTimestamp(timestamp)
                                .setData(SwitchSensorAvro.newBuilder()
                                        .setState(true)
                                        .build())
                                .build(),
                        "sensor.temperature.legacy.1",
                        SensorStateAvro.newBuilder()
                                .setTimestamp(timestamp)
                                .setData(TemperatureSensorAvro.newBuilder()
                                        .setId("sensor.temperature.legacy.1")
                                        .setHubId("hub-1")
                                        .setTimestamp(timestamp)
                                        .setTemperatureC(21)
                                        .setTemperatureF(70)
                                        .build())
                                .build(),
                        "sensor.temperature.1",
                        SensorStateAvro.newBuilder()
                                .setTimestamp(timestamp)
                                .setData(TemperatureSensorPayloadAvro.newBuilder()
                                        .setTemperatureC(23)
                                        .setTemperatureF(73)
                                        .build())
                                .build()
                ))
                .build();
    }
}
