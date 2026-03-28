package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeName("TEMPERATURE_SENSOR_EVENT")
public record TemperatureSensorEvent(
        @NotBlank String id,
        @NotBlank String hubId,
        Instant timestamp,
        @NotNull Integer temperatureC,
        @NotNull Integer temperatureF
) implements SensorEvent {

    public TemperatureSensorEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    @Override
    public SensorEventType type() {
        return SensorEventType.TEMPERATURE_SENSOR_EVENT;
    }

    public Integer getTemperatureC() {
        return temperatureC;
    }

    public Integer getTemperatureF() {
        return temperatureF;
    }
}
