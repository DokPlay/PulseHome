package ru.yandex.practicum.telemetry.analyzer.model;

public record ConditionSpec(String sensorId,
                            ConditionType type,
                            ConditionOperation operation,
                            Integer value) {
}
