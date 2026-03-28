package ru.yandex.practicum.telemetry.aggregator.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.serialization.AvroBinarySerializer;
import ru.yandex.practicum.telemetry.serialization.SensorEventDeserializer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SensorEventDeserializerTest {

    @Test
    void shouldDeserializeSensorEventAvroPayload() {
        SensorEventAvro event = SensorEventAvro.newBuilder()
                .setId("sensor.motion.1")
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(MotionSensorAvro.newBuilder()
                        .setLinkQuality(87)
                        .setMotion(true)
                        .setVoltage(220)
                        .build())
                .build();

        byte[] bytes = AvroBinarySerializer.serialize(event);
        try (SensorEventDeserializer deserializer = new SensorEventDeserializer()) {
            SensorEventAvro deserialized = deserializer.deserialize("telemetry.sensors.v1", bytes);

            assertThat(deserialized.getId()).isEqualTo("sensor.motion.1");
            assertThat(deserialized.getHubId()).isEqualTo("hub-1");
            assertThat(deserialized.getTimestamp()).isEqualTo(event.getTimestamp());
            assertThat(deserialized.getPayload()).isInstanceOf(MotionSensorAvro.class);
        }
    }

    @Test
    void shouldDeserializeLegacyRawBinaryPayload() {
        SensorEventAvro event = SensorEventAvro.newBuilder()
                .setId("sensor.motion.2")
                .setHubId("hub-legacy")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(MotionSensorAvro.newBuilder()
                        .setLinkQuality(77)
                        .setMotion(false)
                        .setVoltage(210)
                        .build())
                .build();

        byte[] bytes = AvroBinarySerializer.serializeLegacy(event);
        try (SensorEventDeserializer deserializer = new SensorEventDeserializer()) {
            SensorEventAvro deserialized = deserializer.deserialize("telemetry.sensors.v1", bytes);

            assertThat(deserialized.getId()).isEqualTo("sensor.motion.2");
            assertThat(deserialized.getHubId()).isEqualTo("hub-legacy");
            assertThat(deserialized.getTimestamp()).isEqualTo(event.getTimestamp());
            assertThat(deserialized.getPayload()).isInstanceOf(MotionSensorAvro.class);
        }
    }
}
