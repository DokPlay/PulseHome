package ru.yandex.practicum.telemetry.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.telemetry.analyzer.entity.ScenarioConditionId;
import ru.yandex.practicum.telemetry.analyzer.entity.ScenarioConditionLink;

import java.util.Collection;
import java.util.List;

public interface ScenarioConditionRepository extends JpaRepository<ScenarioConditionLink, ScenarioConditionId> {

    @Query("select link from ScenarioConditionLink link " +
            "join fetch link.sensor " +
            "join fetch link.condition " +
            "where link.scenario.id in :scenarioIds")
    List<ScenarioConditionLink> findAllByScenarioIds(@Param("scenarioIds") Collection<Long> scenarioIds);

    @Query("select link from ScenarioConditionLink link " +
            "join fetch link.condition " +
            "where link.scenario.id = :scenarioId")
    List<ScenarioConditionLink> findDetailedByScenarioId(@Param("scenarioId") Long scenarioId);

    @Query("select link from ScenarioConditionLink link " +
            "join fetch link.condition " +
            "where link.sensor.id = :sensorId")
    List<ScenarioConditionLink> findDetailedBySensorId(@Param("sensorId") String sensorId);

    void deleteByScenario_Id(Long scenarioId);
}
