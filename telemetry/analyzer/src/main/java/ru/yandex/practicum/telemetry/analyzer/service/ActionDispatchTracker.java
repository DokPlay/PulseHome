package ru.yandex.practicum.telemetry.analyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public boolean isAlreadyDispatched(String hubId, String scenarioName, long snapshotVersion, ActionSpec actionSpec) {
        return actionDispatchRepository.existsDispatch(
                hubId,
                scenarioName,
                snapshotVersion,
                actionSpec.sensorId(),
                actionSpec.type(),
                actionSpec.value()
        );
    }

    @Transactional
    public boolean claimDispatch(String hubId,
                                 String scenarioName,
                                 Instant snapshotTimestamp,
                                 long snapshotVersion,
                                 ActionSpec actionSpec) {
        return actionDispatchRepository.insertIgnore(
                hubId,
                scenarioName,
                snapshotTimestamp,
                snapshotVersion,
                actionSpec.sensorId(),
                actionSpec.type().name(),
                actionSpec.value()
        ) > 0;
    }

    @Transactional
    public void releaseDispatchClaim(String hubId,
                                     String scenarioName,
                                     Instant snapshotTimestamp,
                                     long snapshotVersion,
                                     ActionSpec actionSpec) {
        actionDispatchRepository.deleteDispatch(
                hubId,
                scenarioName,
                snapshotTimestamp,
                snapshotVersion,
                actionSpec.sensorId(),
                actionSpec.type(),
                actionSpec.value()
        );
    }

    @Transactional
    public void pruneOlderSnapshots(String hubId, long snapshotVersion) {
        actionDispatchRepository.deleteByHubIdAndSnapshotVersionLessThan(hubId, snapshotVersion);
    }
}
