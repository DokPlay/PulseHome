package ru.yandex.practicum.telemetry.collector.mapper;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorPayloadAvro;
import ru.yandex.practicum.telemetry.collector.dto.sensor.ClimateSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.LightSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.MotionSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.SwitchSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.TemperatureSensorEvent;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SensorEventAvroMapperTest {

    private final SensorEventAvroMapper mapper = new SensorEventAvroMapper();

    @Test
    void shouldMapMotionSensorEvent() {
        MotionSensorEvent event = new MotionSensorEvent(
                "sensor.motion.1",
                "hub-1",
                Instant.parse("2024-08-06T16:54:03.129Z"),
                88,
                true,
                120
        );

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getId()).isEqualTo("sensor.motion.1");
        assertThat(avroEvent.getHubId()).isEqualTo("hub-1");
        assertThat(avroEvent.getPayload()).isInstanceOf(MotionSensorAvro.class);
        MotionSensorAvro payload = (MotionSensorAvro) avroEvent.getPayload();
        assertThat(payload.getLinkQuality()).isEqualTo(88);
        assertThat(payload.getMotion()).isTrue();
        assertThat(payload.getVoltage()).isEqualTo(120);
    }

    @Test
    void shouldMapTemperatureSensorEventWithoutDuplicatedEnvelopeFields() {
        TemperatureSensorEvent event = new TemperatureSensorEvent(
                "sensor.temperature.1",
                "hub-3",
                Instant.parse("2024-08-06T16:54:03.129Z"),
                23,
                73
        );

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getId()).isEqualTo("sensor.temperature.1");
        assertThat(avroEvent.getHubId()).isEqualTo("hub-3");
        assertThat(avroEvent.getPayload()).isInstanceOf(TemperatureSensorPayloadAvro.class);
        TemperatureSensorPayloadAvro payload = (TemperatureSensorPayloadAvro) avroEvent.getPayload();
        assertThat(payload.getTemperatureC()).isEqualTo(23);
        assertThat(payload.getTemperatureF()).isEqualTo(73);
    }

    @Test
    void shouldKeepGeneratedTimestampOnlyInTemperatureEventWrapper() {
        TemperatureSensorEvent event = new TemperatureSensorEvent("sensor.temperature.2", "hub-4", null, 20, 68);

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getPayload()).isInstanceOf(TemperatureSensorPayloadAvro.class);
        TemperatureSensorPayloadAvro payload = (TemperatureSensorPayloadAvro) avroEvent.getPayload();
        assertThat(avroEvent.getTimestamp()).isNotNull();
        assertThat(payload.getTemperatureC()).isEqualTo(20);
        assertThat(payload.getTemperatureF()).isEqualTo(68);
    }

    @Test
    void shouldMapClimateSensorEvent() {
        ClimateSensorEvent event = new ClimateSensorEvent("sensor.climate.1", "hub-7", null, 22, 48, 600);

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getPayload()).isInstanceOf(ClimateSensorAvro.class);
        ClimateSensorAvro payload = (ClimateSensorAvro) avroEvent.getPayload();
        assertThat(payload.getTemperatureC()).isEqualTo(22);
        assertThat(payload.getHumidity()).isEqualTo(48);
        assertThat(payload.getCo2Level()).isEqualTo(600);
    }

    @Test
    void shouldMapLightSensorEvent() {
        LightSensorEvent event = new LightSensorEvent("sensor.light.1", "hub-8", null, 77, 320);

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getPayload()).isInstanceOf(LightSensorAvro.class);
        LightSensorAvro payload = (LightSensorAvro) avroEvent.getPayload();
        assertThat(payload.getLinkQuality()).isEqualTo(77);
        assertThat(payload.getLuminosity()).isEqualTo(320);
    }

    @Test
    void shouldMapSwitchSensorEvent() {
        SwitchSensorEvent event = new SwitchSensorEvent("sensor.switch.1", "hub-9", null, true);

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getPayload()).isInstanceOf(SwitchSensorAvro.class);
        SwitchSensorAvro payload = (SwitchSensorAvro) avroEvent.getPayload();
        assertThat(payload.getState()).isTrue();
    }
}
