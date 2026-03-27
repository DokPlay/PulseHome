package ru.yandex.practicum.telemetry.analyzer.model;

import ru.yandex.practicum.kafka.telemetry.event.ConditionOperationAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionTypeAvro;

public record ConditionSpec(String sensorId,
                            ConditionTypeAvro type,
                            ConditionOperationAvro operation,
                            Integer value) {
}
