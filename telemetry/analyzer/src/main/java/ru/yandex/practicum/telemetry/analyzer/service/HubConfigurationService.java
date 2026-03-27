package ru.yandex.practicum.telemetry.analyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionOperationAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceActionAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioConditionAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioRemovedEventAvro;
import ru.yandex.practicum.telemetry.analyzer.entity.Action;
import ru.yandex.practicum.telemetry.analyzer.entity.Condition;
import ru.yandex.practicum.telemetry.analyzer.entity.Scenario;
import ru.yandex.practicum.telemetry.analyzer.entity.ScenarioActionId;
import ru.yandex.practicum.telemetry.analyzer.entity.ScenarioActionLink;
import ru.yandex.practicum.telemetry.analyzer.entity.ScenarioConditionId;
import ru.yandex.practicum.telemetry.analyzer.entity.ScenarioConditionLink;
import ru.yandex.practicum.telemetry.analyzer.entity.Sensor;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ScenarioDefinition;
import ru.yandex.practicum.telemetry.analyzer.repository.ActionRepository;
import ru.yandex.practicum.telemetry.analyzer.repository.ConditionRepository;
import ru.yandex.practicum.telemetry.analyzer.repository.ScenarioActionRepository;
import ru.yandex.practicum.telemetry.analyzer.repository.ScenarioConditionRepository;
import ru.yandex.practicum.telemetry.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.telemetry.analyzer.repository.SensorRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class HubConfigurationService {

    private final ActionRepository actionRepository;
    private final ConditionRepository conditionRepository;
    private final ScenarioRepository scenarioRepository;
    private final SensorRepository sensorRepository;
    private final ScenarioConditionRepository scenarioConditionRepository;
    private final ScenarioActionRepository scenarioActionRepository;

    public HubConfigurationService(ActionRepository actionRepository,
                                   ConditionRepository conditionRepository,
                                   ScenarioRepository scenarioRepository,
                                   SensorRepository sensorRepository,
                                   ScenarioConditionRepository scenarioConditionRepository,
                                   ScenarioActionRepository scenarioActionRepository) {
        this.actionRepository = actionRepository;
        this.conditionRepository = conditionRepository;
        this.scenarioRepository = scenarioRepository;
        this.sensorRepository = sensorRepository;
        this.scenarioConditionRepository = scenarioConditionRepository;
        this.scenarioActionRepository = scenarioActionRepository;
    }

    @Transactional
    public void handleHubEvent(HubEventAvro event) {
        Object payload = event.getPayload();
        switch (payload) {
            case DeviceAddedEventAvro deviceAddedEvent -> upsertSensor(deviceAddedEvent.getId(), event.getHubId());
            case DeviceRemovedEventAvro deviceRemovedEvent -> removeSensor(event.getHubId(), deviceRemovedEvent.getId());
            case ScenarioAddedEventAvro scenarioAddedEvent -> upsertScenario(event.getHubId(), scenarioAddedEvent);
            case ScenarioRemovedEventAvro scenarioRemovedEvent -> removeScenario(event.getHubId(), scenarioRemovedEvent.getName());
            default -> throw new IllegalStateException("Unsupported hub event payload type: " + payload.getClass().getName());
        }
    }

    @Transactional(readOnly = true)
    public List<ScenarioDefinition> getScenarios(String hubId) {
        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);
        if (scenarios.isEmpty()) {
            return List.of();
        }

        List<Long> scenarioIds = scenarios.stream()
                .map(Scenario::getId)
                .toList();

        Map<Long, List<ConditionSpec>> conditionSpecsByScenarioId = scenarioConditionRepository.findAllByScenarioIds(scenarioIds).stream()
                .collect(Collectors.groupingBy(link -> link.getScenario().getId(), LinkedHashMap::new,
                        Collectors.mapping(link -> new ConditionSpec(
                                link.getSensor().getId(),
                                link.getCondition().getType(),
                                link.getCondition().getOperation(),
                                link.getCondition().getValue()
                        ), Collectors.toList())));

        Map<Long, List<ActionSpec>> actionSpecsByScenarioId = scenarioActionRepository.findAllByScenarioIds(scenarioIds).stream()
                .collect(Collectors.groupingBy(link -> link.getScenario().getId(), LinkedHashMap::new,
                        Collectors.mapping(link -> new ActionSpec(
                                link.getSensor().getId(),
                                link.getAction().getType(),
                                link.getAction().getValue()
                        ), Collectors.toList())));

        return scenarios.stream()
                .map(scenario -> new ScenarioDefinition(
                        scenario.getHubId(),
                        scenario.getName(),
                        conditionSpecsByScenarioId.getOrDefault(scenario.getId(), List.of()),
                        actionSpecsByScenarioId.getOrDefault(scenario.getId(), List.of())
                ))
                .toList();
    }

    private void upsertScenario(String hubId, ScenarioAddedEventAvro event) {
        Scenario scenario = scenarioRepository.findByHubIdAndName(hubId, event.getName())
                .orElseGet(() -> scenarioRepository.save(new Scenario(hubId, event.getName())));

        replaceScenarioConditions(scenario, hubId, event.getConditions());
        replaceScenarioActions(scenario, hubId, event.getActions());
    }

    private void replaceScenarioConditions(Scenario scenario,
                                           String hubId,
                                           Collection<ScenarioConditionAvro> conditions) {
        List<ScenarioConditionLink> existingLinks = scenarioConditionRepository.findDetailedByScenarioId(scenario.getId());
        scenarioConditionRepository.deleteByScenario_Id(scenario.getId());
        scenarioConditionRepository.flush();
        deleteConditions(existingLinks);

        for (ScenarioConditionAvro conditionEvent : conditions) {
            Sensor sensor = upsertSensor(conditionEvent.getSensorId(), hubId);
            Condition condition = conditionRepository.save(new Condition(
                    conditionEvent.getType(),
                    conditionEvent.getOperation(),
                    extractConditionValue(conditionEvent)
            ));
            ScenarioConditionLink link = new ScenarioConditionLink(
                    new ScenarioConditionId(scenario.getId(), sensor.getId(), condition.getId()),
                    scenario,
                    sensor,
                    condition
            );
            scenarioConditionRepository.save(link);
        }
    }

    private void replaceScenarioActions(Scenario scenario,
                                        String hubId,
                                        Collection<DeviceActionAvro> actions) {
        List<ScenarioActionLink> existingLinks = scenarioActionRepository.findDetailedByScenarioId(scenario.getId());
        scenarioActionRepository.deleteByScenario_Id(scenario.getId());
        scenarioActionRepository.flush();
        deleteActions(existingLinks);

        for (DeviceActionAvro actionEvent : actions) {
            Sensor sensor = upsertSensor(actionEvent.getSensorId(), hubId);
            Action action = actionRepository.save(new Action(
                    actionEvent.getType(),
                    actionEvent.getValue() == null ? null : actionEvent.getValue()
            ));
            ScenarioActionLink link = new ScenarioActionLink(
                    new ScenarioActionId(scenario.getId(), sensor.getId(), action.getId()),
                    scenario,
                    sensor,
                    action
            );
            scenarioActionRepository.save(link);
        }
    }

    private void removeScenario(String hubId, String scenarioName) {
        Optional<Scenario> scenarioOptional = scenarioRepository.findByHubIdAndName(hubId, scenarioName);
        if (scenarioOptional.isEmpty()) {
            return;
        }

        removeScenario(scenarioOptional.get());
    }

    private void removeScenario(Scenario scenario) {
        List<ScenarioConditionLink> conditionLinks = scenarioConditionRepository.findDetailedByScenarioId(scenario.getId());
        List<ScenarioActionLink> actionLinks = scenarioActionRepository.findDetailedByScenarioId(scenario.getId());
        scenarioConditionRepository.deleteByScenario_Id(scenario.getId());
        scenarioActionRepository.deleteByScenario_Id(scenario.getId());
        scenarioConditionRepository.flush();
        scenarioActionRepository.flush();
        deleteConditions(conditionLinks);
        deleteActions(actionLinks);
        scenarioRepository.delete(scenario);
    }

    private Sensor upsertSensor(String sensorId, String hubId) {
        // The course schema keys sensors by id, so a cross-hub collision is treated as invalid input.
        Optional<Sensor> sensorOptional = sensorRepository.findById(sensorId);
        if (sensorOptional.isPresent()) {
            Sensor sensor = sensorOptional.get();
            if (!Objects.equals(sensor.getHubId(), hubId)) {
                throw new IllegalStateException("Sensor " + sensorId + " is already linked to hub " + sensor.getHubId());
            }
            return sensor;
        }

        return sensorRepository.save(new Sensor(sensorId, hubId));
    }

    private void removeSensor(String hubId, String sensorId) {
        Optional<Sensor> sensorOptional = sensorRepository.findByIdAndHubId(sensorId, hubId);
        if (sensorOptional.isEmpty()) {
            return;
        }

        List<ScenarioConditionLink> conditionLinks = scenarioConditionRepository.findDetailedBySensorId(sensorId);
        List<ScenarioActionLink> actionLinks = scenarioActionRepository.findDetailedBySensorId(sensorId);

        Set<Long> affectedScenarioIds = Stream.concat(
                        conditionLinks.stream().map(link -> link.getScenario().getId()),
                        actionLinks.stream().map(link -> link.getScenario().getId()))
                .collect(Collectors.toSet());

        if (!affectedScenarioIds.isEmpty()) {
            scenarioRepository.findAllById(affectedScenarioIds)
                    .forEach(this::removeScenario);
        }

        sensorRepository.delete(sensorOptional.get());
    }

    private void deleteConditions(List<ScenarioConditionLink> links) {
        List<Long> conditionIds = links.stream()
                .map(link -> link.getCondition().getId())
                .toList();
        if (!conditionIds.isEmpty()) {
            conditionRepository.deleteAllByIdInBatch(conditionIds);
        }
    }

    private void deleteActions(List<ScenarioActionLink> links) {
        List<Long> actionIds = links.stream()
                .map(link -> link.getAction().getId())
                .toList();
        if (!actionIds.isEmpty()) {
            actionRepository.deleteAllByIdInBatch(actionIds);
        }
    }

    private Integer extractConditionValue(ScenarioConditionAvro conditionEvent) {
        Object rawValue = conditionEvent.getValue();
        if (rawValue instanceof Integer integerValue) {
            return integerValue;
        }
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue ? 1 : 0;
        }
        return null;
    }
}
