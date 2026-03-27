package ru.yandex.practicum.telemetry.analyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ScenarioActionId implements Serializable {

    @Column(name = "scenario_id")
    private Long scenarioId;

    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "action_id")
    private Long actionId;

    public ScenarioActionId() {
    }

    public ScenarioActionId(Long scenarioId, String sensorId, Long actionId) {
        this.scenarioId = scenarioId;
        this.sensorId = sensorId;
        this.actionId = actionId;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public String getSensorId() {
        return sensorId;
    }

    public Long getActionId() {
        return actionId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ScenarioActionId other)) {
            return false;
        }
        return Objects.equals(scenarioId, other.scenarioId)
                && Objects.equals(sensorId, other.sensorId)
                && Objects.equals(actionId, other.actionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scenarioId, sensorId, actionId);
    }
}
