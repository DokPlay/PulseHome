package ru.yandex.practicum.telemetry.collector.dto.hub;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.HubEventType;

import java.util.ArrayList;
import java.util.List;

public class ScenarioAddedEvent extends HubEvent {

    @NotBlank
    @Size(min = 3)
    private String name;

    @Valid
    @NotEmpty
    private List<ScenarioCondition> conditions = new ArrayList<>();

    @Valid
    @NotEmpty
    private List<DeviceAction> actions = new ArrayList<>();

    public ScenarioAddedEvent() {
        super(HubEventType.SCENARIO_ADDED);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ScenarioCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<ScenarioCondition> conditions) {
        this.conditions = conditions;
    }

    public List<DeviceAction> getActions() {
        return actions;
    }

    public void setActions(List<DeviceAction> actions) {
        this.actions = actions;
    }
}
