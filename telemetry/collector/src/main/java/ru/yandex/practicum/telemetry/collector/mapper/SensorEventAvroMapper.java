package ru.yandex.practicum.telemetry.collector.mapper;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;
import ru.yandex.practicum.telemetry.collector.dto.sensor.ClimateSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.LightSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.MotionSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.SwitchSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.TemperatureSensorEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SensorEventAvroMapper {

    public SensorEventAvro toAvro(SensorEvent event) {
        Instant normalizedTimestamp = normalizeTimestamp(event.getTimestamp());
        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(normalizedTimestamp)
                .setPayload(mapPayload(event, normalizedTimestamp))
                .build();
    }

    private Object mapPayload(SensorEvent event, Instant normalizedTimestamp) {
        SensorEventType eventType = event.getType();
        return switch (eventType) {
            case CLIMATE_SENSOR_EVENT -> mapClimatePayload((ClimateSensorEvent) event);
            case LIGHT_SENSOR_EVENT -> mapLightPayload((LightSensorEvent) event);
            case MOTION_SENSOR_EVENT -> mapMotionPayload((MotionSensorEvent) event);
            case SWITCH_SENSOR_EVENT -> mapSwitchPayload((SwitchSensorEvent) event);
            case TEMPERATURE_SENSOR_EVENT -> mapTemperaturePayload((TemperatureSensorEvent) event, normalizedTimestamp);
        };
    }

    private ClimateSensorAvro mapClimatePayload(ClimateSensorEvent event) {
        return ClimateSensorAvro.newBuilder()
                .setTemperatureC(event.getTemperatureC())
                .setHumidity(event.getHumidity())
                .setCo2Level(event.getCo2Level())
                .build();
    }

    private LightSensorAvro mapLightPayload(LightSensorEvent event) {
        return LightSensorAvro.newBuilder()
                .setLinkQuality(event.getLinkQuality())
                .setLuminosity(event.getLuminosity())
                .build();
    }

    private MotionSensorAvro mapMotionPayload(MotionSensorEvent event) {
        return MotionSensorAvro.newBuilder()
                .setLinkQuality(event.getLinkQuality())
                .setMotion(event.getMotion())
                .setVoltage(event.getVoltage())
                .build();
    }

    private SwitchSensorAvro mapSwitchPayload(SwitchSensorEvent event) {
        return SwitchSensorAvro.newBuilder()
                .setState(event.getState())
                .build();
    }

    private TemperatureSensorAvro mapTemperaturePayload(TemperatureSensorEvent event, Instant normalizedTimestamp) {
        return TemperatureSensorAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(normalizedTimestamp)
                .setTemperatureC(event.getTemperatureC())
                .setTemperatureF(event.getTemperatureF())
                .build();
    }

    private Instant normalizeTimestamp(Instant timestamp) {
        Instant actualTimestamp = timestamp == null ? Instant.now() : timestamp;
        return actualTimestamp.truncatedTo(ChronoUnit.MILLIS);
    }

}
