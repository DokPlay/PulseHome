package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeName("LIGHT_SENSOR_EVENT")
/**
 * Structural validation lives here; physical bounds for light telemetry are intentionally deferred
 * until product defines supported link-quality and luminosity ranges per device family.
 */
public record LightSensorEvent(
        @NotBlank @Size(max = 255) String id,
        @NotBlank @Size(max = 255) String hubId,
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
