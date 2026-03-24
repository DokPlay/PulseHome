package ru.yandex.practicum.telemetry.collector.mapper;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
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
        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(normalizeTimestamp(event.getTimestamp()))
                .setPayload(mapPayload(event))
                .build();
    }

    private Object mapPayload(SensorEvent event) {
        if (event instanceof ClimateSensorEvent climateSensorEvent) {
            return ClimateSensorAvro.newBuilder()
                    .setTemperatureC(climateSensorEvent.getTemperatureC())
                    .setHumidity(climateSensorEvent.getHumidity())
                    .setCo2Level(climateSensorEvent.getCo2Level())
                    .build();
        }
        if (event instanceof LightSensorEvent lightSensorEvent) {
            return LightSensorAvro.newBuilder()
                    .setLinkQuality(defaultInt(lightSensorEvent.getLinkQuality()))
                    .setLuminosity(defaultInt(lightSensorEvent.getLuminosity()))
                    .build();
        }
        if (event instanceof MotionSensorEvent motionSensorEvent) {
            return MotionSensorAvro.newBuilder()
                    .setLinkQuality(motionSensorEvent.getLinkQuality())
                    .setMotion(motionSensorEvent.getMotion())
                    .setVoltage(motionSensorEvent.getVoltage())
                    .build();
        }
        if (event instanceof SwitchSensorEvent switchSensorEvent) {
            return SwitchSensorAvro.newBuilder()
                    .setState(switchSensorEvent.getState())
                    .build();
        }
        if (event instanceof TemperatureSensorEvent temperatureSensorEvent) {
            return TemperatureSensorAvro.newBuilder()
                    .setId(temperatureSensorEvent.getId())
                    .setHubId(temperatureSensorEvent.getHubId())
                    .setTimestamp(normalizeTimestamp(temperatureSensorEvent.getTimestamp()))
                    .setTemperatureC(temperatureSensorEvent.getTemperatureC())
                    .setTemperatureF(temperatureSensorEvent.getTemperatureF())
                    .build();
        }
        throw new IllegalArgumentException("Unsupported sensor event type: " + event.getClass().getName());
    }

    private Instant normalizeTimestamp(Instant timestamp) {
        Instant actualTimestamp = timestamp == null ? Instant.now() : timestamp;
        return actualTimestamp.truncatedTo(ChronoUnit.MILLIS);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
