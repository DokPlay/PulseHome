package ru.yandex.practicum.telemetry.collector.dto.hub;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.telemetry.collector.dto.enums.ActionType;

public record DeviceAction(
        @NotBlank @Size(max = 255) String sensorId,
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
