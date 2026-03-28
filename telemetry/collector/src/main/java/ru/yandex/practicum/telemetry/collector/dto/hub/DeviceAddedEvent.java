package ru.yandex.practicum.telemetry.collector.dto.hub;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.telemetry.collector.dto.enums.DeviceType;
import ru.yandex.practicum.telemetry.collector.dto.enums.HubEventType;

import java.time.Instant;

@JsonTypeName("DEVICE_ADDED")
public record DeviceAddedEvent(
        @NotBlank String hubId,
        Instant timestamp,
        @NotBlank String id,
        @NotNull DeviceType deviceType
) implements HubEvent {

    public DeviceAddedEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    @Override
    public HubEventType type() {
        return HubEventType.DEVICE_ADDED;
    }

    public String getId() {
        return id;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }
}
