package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeName("LIGHT_SENSOR_EVENT")
public record LightSensorEvent(
        @NotBlank String id,
        @NotBlank String hubId,
        Instant timestamp,
        @NotNull Integer linkQuality,
        @NotNull Integer luminosity
) implements SensorEvent {

    public LightSensorEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    @Override
    public SensorEventType type() {
        return SensorEventType.LIGHT_SENSOR_EVENT;
    }

    public Integer getLinkQuality() {
        return linkQuality;
    }

    public Integer getLuminosity() {
        return luminosity;
    }
}
