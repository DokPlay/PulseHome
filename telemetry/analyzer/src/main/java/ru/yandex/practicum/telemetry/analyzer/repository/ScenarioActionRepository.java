package ru.yandex.practicum.telemetry.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.telemetry.analyzer.entity.ScenarioActionId;
import ru.yandex.practicum.telemetry.analyzer.entity.ScenarioActionLink;

import java.util.Collection;
import java.util.List;

public interface ScenarioActionRepository extends JpaRepository<ScenarioActionLink, ScenarioActionId> {

    @Query("select link from ScenarioActionLink link " +
            "join fetch link.sensor " +
            "join fetch link.action " +
            "where link.scenario.id in :scenarioIds")
    List<ScenarioActionLink> findAllByScenarioIds(@Param("scenarioIds") Collection<Long> scenarioIds);

    @Query("select link from ScenarioActionLink link " +
            "join fetch link.action " +
            "where link.scenario.id = :scenarioId")
    List<ScenarioActionLink> findDetailedByScenarioId(@Param("scenarioId") Long scenarioId);

    @Query("select link from ScenarioActionLink link " +
            "join fetch link.action " +
            "where link.sensor.id = :sensorId")
    List<ScenarioActionLink> findDetailedBySensorId(@Param("sensorId") String sensorId);

    void deleteByScenario_Id(Long scenarioId);

    void deleteBySensor_Id(String sensorId);
}
