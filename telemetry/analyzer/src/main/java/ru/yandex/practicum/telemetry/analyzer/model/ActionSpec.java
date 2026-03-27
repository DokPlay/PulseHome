package ru.yandex.practicum.telemetry.analyzer.model;

public record ActionSpec(String sensorId,
                         ActionType type,
                         Integer value) {
}
