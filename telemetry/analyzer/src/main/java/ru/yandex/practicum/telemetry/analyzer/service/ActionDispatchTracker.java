package ru.yandex.practicum.telemetry.analyzer.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.telemetry.analyzer.entity.ActionDispatch;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.repository.ActionDispatchRepository;

import java.sql.SQLException;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispatched(String hubId, String scenarioName, Instant snapshotTimestamp, ActionSpec actionSpec) {
        try {
            actionDispatchRepository.saveAndFlush(new ActionDispatch(
                    hubId,
                    scenarioName,
                    snapshotTimestamp,
                    actionSpec.sensorId(),
                    actionSpec.type(),
                    normalizeActionValue(actionSpec)
            ));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateDispatchMarker(exception)) {
                // A concurrent or repeated retry may race with an already persisted dispatch marker.
                return;
            }
            throw exception;
        }
    }

    @Transactional
    public void pruneOlderSnapshots(String hubId, Instant snapshotTimestamp) {
        actionDispatchRepository.deleteByHubIdAndSnapshotTimestampBefore(hubId, snapshotTimestamp);
    }

    private Integer normalizeActionValue(ActionSpec actionSpec) {
        return actionSpec.value() == null ? 0 : actionSpec.value();
    }

    private boolean isDuplicateDispatchMarker(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
