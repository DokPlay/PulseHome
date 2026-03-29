package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeName("TEMPERATURE_SENSOR_EVENT")
/**
 * Structural validation lives here; physical temperature bounds should be added only after
 * product signs off the valid operating range for the production sensor fleet.
 */
public record TemperatureSensorEvent(
        @NotBlank @Size(max = 255) String id,
        @NotBlank @Size(max = 255) String hubId,
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
