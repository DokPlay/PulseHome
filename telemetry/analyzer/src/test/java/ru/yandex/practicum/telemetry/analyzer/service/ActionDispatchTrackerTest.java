package ru.yandex.practicum.telemetry.analyzer.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.repository.ActionDispatchRepository;

import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ActionDispatchTrackerTest {

    @Test
    void shouldIgnoreDuplicateDispatchMarkerViolations() {
        ActionDispatchRepository repository = mock(ActionDispatchRepository.class);
        ActionDispatchTracker tracker = new ActionDispatchTracker(repository);

        doThrow(new DataIntegrityViolationException("duplicate key", new SQLException("duplicate", "23505")))
                .when(repository).saveAndFlush(any());

        assertThatCode(() -> tracker.markDispatched("hub-1", "scenario-1", Instant.now(), actionSpec()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRethrowNonDuplicateIntegrityViolations() {
        ActionDispatchRepository repository = mock(ActionDispatchRepository.class);
        ActionDispatchTracker tracker = new ActionDispatchTracker(repository);

        doThrow(new DataIntegrityViolationException("other integrity violation", new SQLException("check", "23514")))
                .when(repository).saveAndFlush(any());

        assertThatThrownBy(() -> tracker.markDispatched("hub-1", "scenario-1", Instant.now(), actionSpec()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ActionSpec actionSpec() {
        return new ActionSpec("switch.1", ActionTypeAvro.ACTIVATE, 1);
    }
}
