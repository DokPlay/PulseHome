package ru.yandex.practicum.telemetry.collector.dto.hub;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.HubEventType;

import java.time.Instant;

@JsonTypeName("SCENARIO_REMOVED")
public record ScenarioRemovedEvent(
        @NotBlank String hubId,
        Instant timestamp,
        @NotBlank @Size(min = 3) String name
) implements HubEvent {

    public ScenarioRemovedEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    @Override
    public HubEventType type() {
        return HubEventType.SCENARIO_REMOVED;
    }

    public String getName() {
        return name;
    }
}
