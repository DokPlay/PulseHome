package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeName("MOTION_SENSOR_EVENT")
public record MotionSensorEvent(
        @NotBlank @Size(max = 255) String id,
        @NotBlank @Size(max = 255) String hubId,
        Instant timestamp,
        @NotNull Integer linkQuality,
        @NotNull Boolean motion,
        @NotNull @Min(0) Integer voltage
) implements SensorEvent {

    public MotionSensorEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    @Override
    public SensorEventType type() {
        return SensorEventType.MOTION_SENSOR_EVENT;
    }

    public Integer getLinkQuality() {
        return linkQuality;
    }

    public Boolean getMotion() {
        return motion;
    }

    public Integer getVoltage() {
        return voltage;
    }
}
