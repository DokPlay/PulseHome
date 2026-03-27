package ru.yandex.practicum.telemetry.analyzer.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.ConditionOperationAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ScenarioDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotAnalyzerServiceTest {

    @Test
    void shouldDispatchActionsWhenAllConditionsMatch() {
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        DeviceActionDispatcher dispatcher = mock(DeviceActionDispatcher.class);
        SnapshotAnalyzerService service = new SnapshotAnalyzerService(hubConfigurationService, dispatcher);
        SensorsSnapshotAvro snapshot = snapshot();

        when(hubConfigurationService.getScenarios("hub-1")).thenReturn(List.of(
                new ScenarioDefinition(
                        "hub-1",
                        "hall-light",
                        List.of(
                                new ConditionSpec("sensor.light.1", ConditionTypeAvro.LUMINOSITY, ConditionOperationAvro.LOWER_THAN, 20),
                                new ConditionSpec("sensor.motion.1", ConditionTypeAvro.MOTION, ConditionOperationAvro.EQUALS, 1)
                        ),
                        List.of(new ActionSpec("switch.1", ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro.ACTIVATE, 1))
                )
        ));

        service.analyze(snapshot);

        verify(dispatcher).dispatch("hub-1", "hall-light", snapshot.getTimestamp(),
                new ActionSpec("switch.1", ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro.ACTIVATE, 1));
    }

    @Test
    void shouldSkipScenarioWhenConditionDoesNotMatch() {
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        DeviceActionDispatcher dispatcher = mock(DeviceActionDispatcher.class);
        SnapshotAnalyzerService service = new SnapshotAnalyzerService(hubConfigurationService, dispatcher);
        SensorsSnapshotAvro snapshot = snapshot();

        when(hubConfigurationService.getScenarios("hub-1")).thenReturn(List.of(
                new ScenarioDefinition(
                        "hub-1",
                        "hall-light",
                        List.of(new ConditionSpec("sensor.light.1", ConditionTypeAvro.LUMINOSITY, ConditionOperationAvro.GREATER_THAN, 20)),
                        List.of(new ActionSpec("switch.1", ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro.ACTIVATE, 1))
                )
        ));

        service.analyze(snapshot);

        verify(dispatcher, never()).dispatch("hub-1", "hall-light", snapshot.getTimestamp(),
                new ActionSpec("switch.1", ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro.ACTIVATE, 1));
    }

    private SensorsSnapshotAvro snapshot() {
        return SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
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
