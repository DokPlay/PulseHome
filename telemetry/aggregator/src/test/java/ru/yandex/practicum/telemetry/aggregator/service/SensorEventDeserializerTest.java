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
        SensorEventAvro deserialized = new SensorEventDeserializer().deserialize("telemetry.sensors.v1", bytes);

        assertThat(deserialized.getId()).isEqualTo("sensor.motion.1");
        assertThat(deserialized.getHubId()).isEqualTo("hub-1");
        assertThat(deserialized.getPayload()).isInstanceOf(MotionSensorAvro.class);
    }
}
