package ru.yandex.practicum.telemetry.aggregator.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.serialization.AvroBinarySerializer;
import ru.yandex.practicum.telemetry.serialization.SensorsSnapshotDeserializer;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensorsSnapshotDeserializerTest {

    @Test
    void shouldDeserializeSingleObjectEncodedSnapshot() {
        SensorsSnapshotAvro snapshot = snapshot();
        byte[] bytes = AvroBinarySerializer.serialize(snapshot);

        try (SensorsSnapshotDeserializer deserializer = new SensorsSnapshotDeserializer()) {
            SensorsSnapshotAvro deserialized = deserializer.deserialize("telemetry.snapshots.v1", bytes);

            assertThat(deserialized.getHubId()).isEqualTo("hub-1");
            assertThat(deserialized.getSensorsState()).containsKey("sensor.light.1");
        }
    }

    @Test
    void shouldDeserializeLegacyRawSnapshot() {
        SensorsSnapshotAvro snapshot = snapshot();
        byte[] bytes = AvroBinarySerializer.serializeLegacy(snapshot);

        try (SensorsSnapshotDeserializer deserializer = new SensorsSnapshotDeserializer()) {
            SensorsSnapshotAvro deserialized = deserializer.deserialize("telemetry.snapshots.v1", bytes);

            assertThat(deserialized.getHubId()).isEqualTo("hub-1");
            assertThat(deserialized.getSensorsState()).containsKey("sensor.light.1");
        }
    }

    private SensorsSnapshotAvro snapshot() {
        return SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(9)
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setSensorsState(Map.of(
                        "sensor.light.1",
                        SensorStateAvro.newBuilder()
                                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                                .setData(LightSensorAvro.newBuilder()
                                        .setLinkQuality(78)
                                        .setLuminosity(40)
                                        .build())
                                .build()
                ))
                .build();
    }
}
