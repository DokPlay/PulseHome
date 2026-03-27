package ru.yandex.practicum.telemetry.analyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.telemetry.analyzer.entity.ActionDispatch;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.repository.ActionDispatchRepository;

import java.time.Instant;

@Service
public class ActionDispatchTracker {

    private final ActionDispatchRepository actionDispatchRepository;

    public ActionDispatchTracker(ActionDispatchRepository actionDispatchRepository) {
        this.actionDispatchRepository = actionDispatchRepository;
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyDispatched(String hubId, String scenarioName, Instant snapshotTimestamp, ActionSpec actionSpec) {
        return actionDispatchRepository.existsByHubIdAndScenarioNameAndSnapshotTimestampAndSensorIdAndActionTypeAndActionValue(
                hubId,
                scenarioName,
                snapshotTimestamp,
                actionSpec.sensorId(),
                actionSpec.type(),
                normalizeActionValue(actionSpec)
        );
    }

    @Transactional
    public void markDispatched(String hubId, String scenarioName, Instant snapshotTimestamp, ActionSpec actionSpec) {
        if (isAlreadyDispatched(hubId, scenarioName, snapshotTimestamp, actionSpec)) {
            return;
        }

        actionDispatchRepository.save(new ActionDispatch(
                hubId,
                scenarioName,
                snapshotTimestamp,
                actionSpec.sensorId(),
                actionSpec.type(),
                normalizeActionValue(actionSpec)
        ));
    }

    @Transactional
    public void pruneOlderSnapshots(String hubId, Instant snapshotTimestamp) {
        actionDispatchRepository.deleteByHubIdAndSnapshotTimestampBefore(hubId, snapshotTimestamp);
    }

    private Integer normalizeActionValue(ActionSpec actionSpec) {
        return actionSpec.value() == null ? 0 : actionSpec.value();
    }
}
