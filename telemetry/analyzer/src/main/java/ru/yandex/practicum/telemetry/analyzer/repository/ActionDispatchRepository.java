package ru.yandex.practicum.telemetry.analyzer.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.telemetry.analyzer.entity.ActionDispatch;
import ru.yandex.practicum.telemetry.analyzer.model.ActionType;

import java.time.Instant;

public interface ActionDispatchRepository extends JpaRepository<ActionDispatch, Long> {

    @Modifying
    @Transactional
    @Query(value = """
            insert into action_dispatches (
                hub_id,
                scenario_name,
                snapshot_timestamp,
                snapshot_version,
                sensor_id,
                action_type,
                action_value
            ) values (
                :hubId,
                :scenarioName,
                :snapshotTimestamp,
                :snapshotVersion,
                :sensorId,
                :actionType,
                :actionValue
            )
            on conflict (hub_id, scenario_name, snapshot_version, sensor_id, action_type, action_value)
            do nothing
            """, nativeQuery = true)
    int insertIgnore(@Param("hubId") String hubId,
                     @Param("scenarioName") String scenarioName,
                     @Param("snapshotTimestamp") Instant snapshotTimestamp,
                     @Param("snapshotVersion") Long snapshotVersion,
                     @Param("sensorId") String sensorId,
                     @Param("actionType") String actionType,
                     @Param("actionValue") Integer actionValue);

    boolean existsByHubIdAndScenarioNameAndSnapshotVersionAndSensorIdAndActionTypeAndActionValue(
            String hubId,
            String scenarioName,
            Long snapshotVersion,
            String sensorId,
            ActionType actionType,
            Integer actionValue
    );

    void deleteByHubIdAndSnapshotVersionLessThan(String hubId, Long snapshotVersion);
}
