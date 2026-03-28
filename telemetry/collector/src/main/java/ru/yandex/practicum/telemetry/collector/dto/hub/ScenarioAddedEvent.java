package ru.yandex.practicum.telemetry.collector.dto.hub;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.HubEventType;

import java.time.Instant;
import java.util.List;

@JsonTypeName("SCENARIO_ADDED")
public record ScenarioAddedEvent(
        @NotBlank @Size(max = 255) String hubId,
        Instant timestamp,
        @NotBlank @Size(min = 3, max = 255) String name,
        @Valid @NotEmpty List<ScenarioCondition> conditions,
        @Valid @NotEmpty List<DeviceAction> actions
) implements HubEvent {

    public ScenarioAddedEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        conditions = List.copyOf(conditions == null ? List.of() : conditions);
        actions = List.copyOf(actions == null ? List.of() : actions);
    }

    @Override
    public HubEventType type() {
        return HubEventType.SCENARIO_ADDED;
    }

    public String getName() {
        return name;
    }

    public List<ScenarioCondition> getConditions() {
        return conditions;
    }

    public List<DeviceAction> getActions() {
        return actions;
    }
}
