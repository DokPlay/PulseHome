package ru.yandex.practicum.telemetry.analyzer.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ActionType;
import ru.yandex.practicum.telemetry.analyzer.repository.ActionDispatchRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionDispatchTrackerTest {

    @Test
    void shouldInsertDispatchMarkerUsingNativeIgnoreQuery() {
        ActionDispatchRepository repository = mock(ActionDispatchRepository.class);
        ActionDispatchTracker tracker = new ActionDispatchTracker(repository);
        Instant timestamp = Instant.parse("2024-08-06T15:11:24.157Z");

        when(repository.insertIgnore(
                eq("hub-1"),
                eq("scenario-1"),
                eq(timestamp),
                eq(7L),
                eq("switch.1"),
                eq("ACTIVATE"),
                eq(1)
        )).thenReturn(1);

        assertThatCode(() -> tracker.markDispatched("hub-1", "scenario-1", timestamp, 7L, actionSpec()))
                .doesNotThrowAnyException();
        verify(repository).insertIgnore("hub-1", "scenario-1", timestamp, 7L, "switch.1", "ACTIVATE", 1);
    }

    @Test
    void shouldCheckDispatchMarkerUsingSnapshotVersion() {
        ActionDispatchRepository repository = mock(ActionDispatchRepository.class);
        ActionDispatchTracker tracker = new ActionDispatchTracker(repository);

        when(repository.existsByHubIdAndScenarioNameAndSnapshotVersionAndSensorIdAndActionTypeAndActionValue(
                "hub-1",
                "scenario-1",
                7L,
                "switch.1",
                ActionType.ACTIVATE,
                1
        )).thenReturn(true);

        tracker.isAlreadyDispatched("hub-1", "scenario-1", 7L, actionSpec());

        verify(repository).existsByHubIdAndScenarioNameAndSnapshotVersionAndSensorIdAndActionTypeAndActionValue(
                "hub-1",
                "scenario-1",
                7L,
                "switch.1",
                ActionType.ACTIVATE,
                1
        );
    }

    @Test
    void shouldPruneDispatchMarkersBySnapshotVersion() {
        ActionDispatchRepository repository = mock(ActionDispatchRepository.class);
        ActionDispatchTracker tracker = new ActionDispatchTracker(repository);

        tracker.pruneOlderSnapshots("hub-1", 7L);

        verify(repository).deleteByHubIdAndSnapshotVersionLessThan("hub-1", 7L);
    }

    private ActionSpec actionSpec() {
        return new ActionSpec("switch.1", ActionType.ACTIVATE, 1);
    }
}
