package ru.yandex.practicum.telemetry.analyzer.model;

import java.util.List;

public record ScenarioDefinition(String hubId,
                                 String name,
                                 List<ConditionSpec> conditions,
                                 List<ActionSpec> actions) {
}
