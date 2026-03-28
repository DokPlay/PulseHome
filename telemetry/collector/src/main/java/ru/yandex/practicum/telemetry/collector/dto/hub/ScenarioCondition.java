package ru.yandex.practicum.telemetry.collector.dto.hub;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionOperation;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionType;

public record ScenarioCondition(
        @NotBlank String sensorId,
        @NotNull ConditionType type,
        @NotNull ConditionOperation operation,
        Integer value
) {

    public String getSensorId() {
        return sensorId;
    }

    public ConditionType getType() {
        return type;
    }

    public ConditionOperation getOperation() {
        return operation;
    }

    public Integer getValue() {
        return value;
    }
}
