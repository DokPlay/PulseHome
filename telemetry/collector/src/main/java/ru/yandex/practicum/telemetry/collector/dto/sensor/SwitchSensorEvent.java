package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeName("SWITCH_SENSOR_EVENT")
public record SwitchSensorEvent(
        @NotBlank @Size(max = 255) String id,
        @NotBlank @Size(max = 255) String hubId,
        Instant timestamp,
        @NotNull Boolean state
) implements SensorEvent {

    public SwitchSensorEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    @Override
    public SensorEventType type() {
        return SensorEventType.SWITCH_SENSOR_EVENT;
    }

    public Boolean getState() {
        return state;
    }
}
