package ru.yandex.practicum.telemetry.analyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ScenarioConditionId implements Serializable {

    @Column(name = "scenario_id")
    private Long scenarioId;

    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "condition_id")
    private Long conditionId;

    public ScenarioConditionId() {
    }

    public ScenarioConditionId(Long scenarioId, String sensorId, Long conditionId) {
        this.scenarioId = scenarioId;
        this.sensorId = sensorId;
        this.conditionId = conditionId;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public String getSensorId() {
        return sensorId;
    }

    public Long getConditionId() {
        return conditionId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ScenarioConditionId other)) {
            return false;
        }
        return Objects.equals(scenarioId, other.scenarioId)
                && Objects.equals(sensorId, other.sensorId)
                && Objects.equals(conditionId, other.conditionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scenarioId, sensorId, conditionId);
    }
}
