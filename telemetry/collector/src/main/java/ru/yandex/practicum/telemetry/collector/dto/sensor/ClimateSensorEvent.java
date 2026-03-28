package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeName("CLIMATE_SENSOR_EVENT")
public record ClimateSensorEvent(
        @NotBlank String id,
        @NotBlank String hubId,
        Instant timestamp,
        @NotNull Integer temperatureC,
        @NotNull Integer humidity,
        @NotNull Integer co2Level
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
