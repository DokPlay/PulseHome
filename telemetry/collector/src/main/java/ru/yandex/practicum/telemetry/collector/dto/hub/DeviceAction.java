package ru.yandex.practicum.telemetry.collector.dto.hub;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.telemetry.collector.dto.enums.ActionType;

public record DeviceAction(
        @NotBlank String sensorId,
        @NotNull ActionType type,
        Integer value
) {

    public String getSensorId() {
        return sensorId;
    }

    public ActionType getType() {
        return type;
    }

    public Integer getValue() {
        return value;
    }
}
