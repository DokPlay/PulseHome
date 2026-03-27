package ru.yandex.practicum.telemetry.analyzer.model;

import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;

public record ActionSpec(String sensorId,
                         ActionTypeAvro type,
                         Integer value) {
}
