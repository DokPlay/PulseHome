package ru.yandex.practicum.telemetry.collector.dto.hub;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import ru.yandex.practicum.telemetry.collector.dto.enums.HubEventType;

import java.time.Instant;

@JsonTypeName("DEVICE_REMOVED")
public record DeviceRemovedEvent(
        @NotBlank String hubId,
        Instant timestamp,
        @NotBlank String id
) implements HubEvent {

    public DeviceRemovedEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    @Override
    public HubEventType type() {
        return HubEventType.DEVICE_REMOVED;
    }

    public String getId() {
        return id;
    }
}
