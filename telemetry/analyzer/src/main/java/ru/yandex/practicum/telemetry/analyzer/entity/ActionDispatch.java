package ru.yandex.practicum.telemetry.analyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;

import java.time.Instant;

@Entity
@Table(name = "action_dispatches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_action_dispatches_snapshot_action",
                columnNames = {"hub_id", "scenario_name", "snapshot_timestamp", "sensor_id", "action_type", "action_value"})
})
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

    @Column(name = "sensor_id", nullable = false)
    private String sensorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionTypeAvro actionType;

    @Column(name = "action_value", nullable = false)
    private Integer actionValue;

    public ActionDispatch() {
    }

    public ActionDispatch(String hubId,
                          String scenarioName,
                          Instant snapshotTimestamp,
                          String sensorId,
                          ActionTypeAvro actionType,
                          Integer actionValue) {
        this.hubId = hubId;
        this.scenarioName = scenarioName;
        this.snapshotTimestamp = snapshotTimestamp;
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

    public String getSensorId() {
        return sensorId;
    }

    public ActionTypeAvro getActionType() {
        return actionType;
    }

    public Integer getActionValue() {
        return actionValue;
    }
}
