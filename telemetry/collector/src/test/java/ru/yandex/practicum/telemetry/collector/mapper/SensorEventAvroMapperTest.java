package ru.yandex.practicum.telemetry.collector.mapper;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.telemetry.collector.dto.sensor.ClimateSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.LightSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;
import ru.yandex.practicum.telemetry.collector.dto.sensor.MotionSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.SwitchSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.TemperatureSensorEvent;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SensorEventAvroMapperTest {

    private final SensorEventAvroMapper mapper = new SensorEventAvroMapper();

    @Test
    void shouldMapMotionSensorEvent() {
        MotionSensorEvent event = new MotionSensorEvent();
        event.setId("sensor.motion.1");
        event.setHubId("hub-1");
        event.setTimestamp(Instant.parse("2024-08-06T16:54:03.129Z"));
        event.setType(SensorEventType.MOTION_SENSOR_EVENT);
        event.setLinkQuality(88);
        event.setMotion(true);
        event.setVoltage(120);

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
    void shouldMapTemperatureSensorEventWithDuplicatedPayloadFields() {
        TemperatureSensorEvent event = new TemperatureSensorEvent();
        event.setId("sensor.temperature.1");
        event.setHubId("hub-3");
        event.setTimestamp(Instant.parse("2024-08-06T16:54:03.129Z"));
        event.setType(SensorEventType.TEMPERATURE_SENSOR_EVENT);
        event.setTemperatureC(23);
        event.setTemperatureF(73);

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getPayload()).isInstanceOf(TemperatureSensorAvro.class);
        TemperatureSensorAvro payload = (TemperatureSensorAvro) avroEvent.getPayload();
        assertThat(payload.getId()).isEqualTo("sensor.temperature.1");
        assertThat(payload.getHubId()).isEqualTo("hub-3");
        assertThat(payload.getTemperatureC()).isEqualTo(23);
        assertThat(payload.getTemperatureF()).isEqualTo(73);
    }

    @Test
    void shouldMapClimateSensorEvent() {
        ClimateSensorEvent event = new ClimateSensorEvent();
        event.setId("sensor.climate.1");
        event.setHubId("hub-7");
        event.setType(SensorEventType.CLIMATE_SENSOR_EVENT);
        event.setTemperatureC(22);
        event.setHumidity(48);
        event.setCo2Level(600);

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getPayload()).isInstanceOf(ClimateSensorAvro.class);
        ClimateSensorAvro payload = (ClimateSensorAvro) avroEvent.getPayload();
        assertThat(payload.getTemperatureC()).isEqualTo(22);
        assertThat(payload.getHumidity()).isEqualTo(48);
        assertThat(payload.getCo2Level()).isEqualTo(600);
    }

    @Test
    void shouldMapLightSensorEvent() {
        LightSensorEvent event = new LightSensorEvent();
        event.setId("sensor.light.1");
        event.setHubId("hub-8");
        event.setType(SensorEventType.LIGHT_SENSOR_EVENT);
        event.setLinkQuality(77);
        event.setLuminosity(320);

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getPayload()).isInstanceOf(LightSensorAvro.class);
        LightSensorAvro payload = (LightSensorAvro) avroEvent.getPayload();
        assertThat(payload.getLinkQuality()).isEqualTo(77);
        assertThat(payload.getLuminosity()).isEqualTo(320);
    }

    @Test
    void shouldMapSwitchSensorEvent() {
        SwitchSensorEvent event = new SwitchSensorEvent();
        event.setId("sensor.switch.1");
        event.setHubId("hub-9");
        event.setType(SensorEventType.SWITCH_SENSOR_EVENT);
        event.setState(true);

        SensorEventAvro avroEvent = mapper.toAvro(event);

        assertThat(avroEvent.getPayload()).isInstanceOf(SwitchSensorAvro.class);
        SwitchSensorAvro payload = (SwitchSensorAvro) avroEvent.getPayload();
        assertThat(payload.getState()).isTrue();
    }
}
