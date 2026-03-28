package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeName("CLIMATE_SENSOR_EVENT")
/**
 * Structural validation lives here; product-approved physical ranges for climate sensors should be
 * introduced only after the supported device fleet and calibration rules are agreed.
 */
public record ClimateSensorEvent(
        @NotBlank @Size(max = 255) String id,
        @NotBlank @Size(max = 255) String hubId,
        Instant timestamp,
        @NotNull Integer temperatureC,
        @NotNull @Min(0) @Max(100) Integer humidity,
        @NotNull @Min(0) Integer co2Level
) implements SensorEvent {

    public ClimateSensorEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    @Override
    public SensorEventType type() {
        return SensorEventType.CLIMATE_SENSOR_EVENT;
    }

    public Integer getTemperatureC() {
        return temperatureC;
    }

    public Integer getHumidity() {
        return humidity;
    }

    public Integer getCo2Level() {
        return co2Level;
    }
}
