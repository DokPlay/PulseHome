package ru.yandex.practicum.telemetry.analyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ru.yandex.practicum.telemetry.analyzer.model.ActionType;

import java.time.Instant;
import java.util.Objects;
import org.hibernate.Hibernate;

@Entity
@Table(name = "action_dispatches")
public class ActionDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hub_id", nullable = false)
    private String hubId;

    @Column(name = "scenario_name", nullable = false)
    private String scenarioName;

    @Column(name = "snapshot_timestamp", nullable = false)
    private Instant snapshotTimestamp;

    @Column(name = "snapshot_version", nullable = false)
    private Long snapshotVersion;

    @Column(name = "sensor_id", nullable = false)
    private String sensorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Column(name = "action_value")
    private Integer actionValue;

    public ActionDispatch() {
    }

    public ActionDispatch(String hubId,
                          String scenarioName,
                          Instant snapshotTimestamp,
                          Long snapshotVersion,
                          String sensorId,
                          ActionType actionType,
                          Integer actionValue) {
        this.hubId = hubId;
        this.scenarioName = scenarioName;
        this.snapshotTimestamp = snapshotTimestamp;
        this.snapshotVersion = snapshotVersion;
        this.sensorId = sensorId;
        this.actionType = actionType;
        this.actionValue = actionValue;
    }

    public Long getId() {
        return id;
    }

    public String getHubId() {
        return hubId;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public Instant getSnapshotTimestamp() {
        return snapshotTimestamp;
    }

    public Long getSnapshotVersion() {
        return snapshotVersion;
    }

    public String getSensorId() {
        return sensorId;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public Integer getActionValue() {
        return actionValue;
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || Hibernate.getClass(this) != Hibernate.getClass(object)) {
            return false;
        }
        ActionDispatch other = (ActionDispatch) object;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
