package ru.yandex.practicum.telemetry.aggregator.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorPayloadAvro;
import ru.yandex.practicum.telemetry.serialization.AvroBinarySerializer;
import ru.yandex.practicum.telemetry.serialization.SensorEventDeserializer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensorEventDeserializerTest {

    @Test
    void shouldDeserializeAllSingleObjectEncodedSensorPayloadBranches() {
        try (SensorEventDeserializer deserializer = new SensorEventDeserializer()) {
            sensorEvents().forEach(event -> {
                byte[] bytes = AvroBinarySerializer.serialize(event);
                SensorEventAvro deserialized = deserializer.deserialize("telemetry.sensors.v1", bytes);

                assertThat(deserialized.getId()).isEqualTo(event.getId());
                assertThat(deserialized.getHubId()).isEqualTo(event.getHubId());
                assertThat(deserialized.getTimestamp()).isEqualTo(event.getTimestamp());
                assertThat(deserialized.getPayload()).isEqualTo(event.getPayload());
            });
        }
    }

    @Test
    void shouldDeserializeAllLegacyRawSensorPayloadBranches() {
        try (SensorEventDeserializer deserializer = new SensorEventDeserializer()) {
            sensorEvents().forEach(event -> {
                byte[] bytes = AvroBinarySerializer.serializeLegacy(event);
                SensorEventAvro deserialized = deserializer.deserialize("telemetry.sensors.v1", bytes);

                assertThat(deserialized.getId()).isEqualTo(event.getId());
                assertThat(deserialized.getHubId()).isEqualTo(event.getHubId());
                assertThat(deserialized.getTimestamp()).isEqualTo(event.getTimestamp());
                assertThat(deserialized.getPayload()).isEqualTo(event.getPayload());
            });
        }
    }

    private List<SensorEventAvro> sensorEvents() {
        Instant timestamp = Instant.parse("2024-08-06T15:11:24.157Z");
        return List.of(
                SensorEventAvro.newBuilder()
                        .setId("sensor.climate.1")
                        .setHubId("hub-1")
                        .setTimestamp(timestamp)
                        .setPayload(ClimateSensorAvro.newBuilder().setTemperatureC(22).setHumidity(43).setCo2Level(550).build())
                        .build(),
                SensorEventAvro.newBuilder()
                        .setId("sensor.light.1")
                        .setHubId("hub-1")
                        .setTimestamp(timestamp)
                        .setPayload(LightSensorAvro.newBuilder().setLinkQuality(75).setLuminosity(59).build())
                        .build(),
                SensorEventAvro.newBuilder()
                        .setId("sensor.motion.1")
                        .setHubId("hub-1")
                        .setTimestamp(timestamp)
                        .setPayload(MotionSensorAvro.newBuilder().setLinkQuality(87).setMotion(true).setVoltage(220).build())
                        .build(),
                SensorEventAvro.newBuilder()
                        .setId("sensor.switch.1")
                        .setHubId("hub-1")
                        .setTimestamp(timestamp)
                        .setPayload(SwitchSensorAvro.newBuilder().setState(true).build())
                        .build(),
                SensorEventAvro.newBuilder()
                        .setId("sensor.temperature.legacy.1")
                        .setHubId("hub-1")
                        .setTimestamp(timestamp)
                        .setPayload(TemperatureSensorAvro.newBuilder()
                                .setId("sensor.temperature.legacy.1")
                                .setHubId("hub-1")
                                .setTimestamp(timestamp)
                                .setTemperatureC(21)
                                .setTemperatureF(70)
                                .build())
                        .build(),
                SensorEventAvro.newBuilder()
                        .setId("sensor.temperature.1")
                        .setHubId("hub-1")
                        .setTimestamp(timestamp)
                        .setPayload(TemperatureSensorPayloadAvro.newBuilder().setTemperatureC(23).setTemperatureF(73).build())
                        .build()
        );
    }
}
