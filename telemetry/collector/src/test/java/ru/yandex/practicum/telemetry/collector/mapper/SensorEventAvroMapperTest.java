package ru.yandex.practicum.telemetry.collector.mapper;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;
import ru.yandex.practicum.telemetry.collector.dto.sensor.MotionSensorEvent;
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
}
