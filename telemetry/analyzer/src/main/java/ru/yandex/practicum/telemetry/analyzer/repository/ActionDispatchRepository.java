package ru.yandex.practicum.telemetry.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;
import ru.yandex.practicum.telemetry.analyzer.entity.ActionDispatch;

import java.time.Instant;

public interface ActionDispatchRepository extends JpaRepository<ActionDispatch, Long> {

    boolean existsByHubIdAndScenarioNameAndSnapshotTimestampAndSensorIdAndActionTypeAndActionValue(
            String hubId,
            String scenarioName,
            Instant snapshotTimestamp,
            String sensorId,
            ActionTypeAvro actionType,
            Integer actionValue
    );

    void deleteByHubIdAndSnapshotTimestampBefore(String hubId, Instant snapshotTimestamp);
}
