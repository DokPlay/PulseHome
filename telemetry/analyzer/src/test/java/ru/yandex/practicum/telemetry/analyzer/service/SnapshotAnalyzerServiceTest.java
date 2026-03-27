package ru.yandex.practicum.telemetry.analyzer.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ActionType;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionOperation;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionType;
import ru.yandex.practicum.telemetry.analyzer.model.ScenarioDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotAnalyzerServiceTest {

    @Test
    void shouldPruneDispatchStateEvenWhenNoScenariosExist() {
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        DeviceActionDispatcher dispatcher = mock(DeviceActionDispatcher.class);
        ActionDispatchTracker actionDispatchTracker = mock(ActionDispatchTracker.class);
        SnapshotAnalyzerService service = new SnapshotAnalyzerService(hubConfigurationService, dispatcher, actionDispatchTracker);
        SensorsSnapshotAvro snapshot = snapshot();

        when(hubConfigurationService.getScenarios("hub-1")).thenReturn(List.of());

        service.analyze(snapshot);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(actionDispatchTracker).pruneOlderSnapshots("hub-1", snapshot.getVersion());
    }

    @Test
    void shouldDispatchActionsWhenAllConditionsMatch() {
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        DeviceActionDispatcher dispatcher = mock(DeviceActionDispatcher.class);
        ActionDispatchTracker actionDispatchTracker = mock(ActionDispatchTracker.class);
        SnapshotAnalyzerService service = new SnapshotAnalyzerService(hubConfigurationService, dispatcher, actionDispatchTracker);
        SensorsSnapshotAvro snapshot = snapshot();
        ActionSpec action = new ActionSpec("switch.1", ActionType.ACTIVATE, 1);

        when(hubConfigurationService.getScenarios("hub-1")).thenReturn(List.of(
                new ScenarioDefinition(
                        "hub-1",
                        "hall-light",
                        List.of(
                                new ConditionSpec("sensor.light.1", ConditionType.LUMINOSITY, ConditionOperation.LOWER_THAN, 20),
                                new ConditionSpec("sensor.motion.1", ConditionType.MOTION, ConditionOperation.EQUALS, 1)
                        ),
                        List.of(action)
                )
        ));
        when(actionDispatchTracker.isAlreadyDispatched("hub-1", "hall-light", snapshot.getVersion(), action)).thenReturn(false);

        service.analyze(snapshot);

        verify(dispatcher).dispatch("hub-1", "hall-light", snapshot.getTimestamp(), action);
        verify(actionDispatchTracker).markDispatched("hub-1", "hall-light", snapshot.getTimestamp(), snapshot.getVersion(), action);
        verify(actionDispatchTracker).pruneOlderSnapshots("hub-1", snapshot.getVersion());
    }

    @Test
    void shouldSkipScenarioWhenConditionDoesNotMatch() {
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        DeviceActionDispatcher dispatcher = mock(DeviceActionDispatcher.class);
        ActionDispatchTracker actionDispatchTracker = mock(ActionDispatchTracker.class);
        SnapshotAnalyzerService service = new SnapshotAnalyzerService(hubConfigurationService, dispatcher, actionDispatchTracker);
        SensorsSnapshotAvro snapshot = snapshot();

        when(hubConfigurationService.getScenarios("hub-1")).thenReturn(List.of(
                new ScenarioDefinition(
                        "hub-1",
                        "hall-light",
                        List.of(new ConditionSpec("sensor.light.1", ConditionType.LUMINOSITY, ConditionOperation.GREATER_THAN, 20)),
                        List.of(new ActionSpec("switch.1", ActionType.ACTIVATE, 1))
                )
        ));

        service.analyze(snapshot);

        verify(dispatcher, never()).dispatch("hub-1", "hall-light", snapshot.getTimestamp(),
                new ActionSpec("switch.1", ActionType.ACTIVATE, 1));
        verify(actionDispatchTracker).pruneOlderSnapshots("hub-1", snapshot.getVersion());
    }

    @Test
    void shouldNotRedispatchAlreadyRecordedActionsWhenSnapshotIsRetried() {
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        DeviceActionDispatcher dispatcher = mock(DeviceActionDispatcher.class);
        ActionDispatchTracker actionDispatchTracker = mock(ActionDispatchTracker.class);
        SnapshotAnalyzerService service = new SnapshotAnalyzerService(hubConfigurationService, dispatcher, actionDispatchTracker);
        SensorsSnapshotAvro snapshot = snapshot();
        ActionSpec firstAction = new ActionSpec("switch.1", ActionType.ACTIVATE, 1);
        ActionSpec secondAction = new ActionSpec("switch.2", ActionType.INVERSE, 1);

        when(hubConfigurationService.getScenarios("hub-1")).thenReturn(List.of(
                new ScenarioDefinition(
                        "hub-1",
                        "hall-light",
                        List.of(
                                new ConditionSpec("sensor.light.1", ConditionType.LUMINOSITY, ConditionOperation.LOWER_THAN, 20),
                                new ConditionSpec("sensor.motion.1", ConditionType.MOTION, ConditionOperation.EQUALS, 1)
                        ),
                        List.of(firstAction, secondAction)
                )
        ));
        when(actionDispatchTracker.isAlreadyDispatched("hub-1", "hall-light", snapshot.getVersion(), firstAction))
                .thenReturn(false, true);
        when(actionDispatchTracker.isAlreadyDispatched("hub-1", "hall-light", snapshot.getVersion(), secondAction))
                .thenReturn(false, false);
        org.mockito.Mockito.doThrow(new RetryableActionDispatchException("retryable", new RuntimeException()))
                .doNothing()
                .when(dispatcher).dispatch("hub-1", "hall-light", snapshot.getTimestamp(), secondAction);

        assertThatThrownBy(() -> service.analyze(snapshot))
                .isInstanceOf(RetryableActionDispatchException.class);

        service.analyze(snapshot);

        verify(dispatcher, times(1)).dispatch("hub-1", "hall-light", snapshot.getTimestamp(), firstAction);
        verify(dispatcher, times(2)).dispatch("hub-1", "hall-light", snapshot.getTimestamp(), secondAction);
        verify(actionDispatchTracker, times(1)).markDispatched("hub-1", "hall-light", snapshot.getTimestamp(), snapshot.getVersion(), firstAction);
        verify(actionDispatchTracker, times(1)).markDispatched("hub-1", "hall-light", snapshot.getTimestamp(), snapshot.getVersion(), secondAction);
        verify(actionDispatchTracker, times(2)).pruneOlderSnapshots("hub-1", snapshot.getVersion());
    }

    private SensorsSnapshotAvro snapshot() {
        return SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(7)
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setSensorsState(Map.of(
                        "sensor.light.1", SensorStateAvro.newBuilder()
                                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                                .setData(LightSensorAvro.newBuilder()
                                        .setLinkQuality(80)
                                        .setLuminosity(10)
                                        .build())
                                .build(),
                        "sensor.motion.1", SensorStateAvro.newBuilder()
                                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                                .setData(MotionSensorAvro.newBuilder()
                                        .setLinkQuality(90)
                                        .setMotion(true)
                                        .setVoltage(220)
                                        .build())
                                .build()
                ))
                .build();
    }
}
