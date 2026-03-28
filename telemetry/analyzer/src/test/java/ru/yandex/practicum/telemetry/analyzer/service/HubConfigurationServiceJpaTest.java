package ru.yandex.practicum.telemetry.analyzer.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionOperationAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceActionAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioConditionAvro;
import ru.yandex.practicum.telemetry.analyzer.model.ActionType;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionOperation;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionType;
import ru.yandex.practicum.telemetry.analyzer.model.ScenarioDefinition;
import ru.yandex.practicum.telemetry.analyzer.repository.SensorRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.locations=classpath:db/test-migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:analyzer-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE"
})
@Import(HubConfigurationService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class HubConfigurationServiceJpaTest {

    @Autowired
    private HubConfigurationService hubConfigurationService;

    @Autowired
    private SensorRepository sensorRepository;

    @Test
    void shouldPersistScenarioAndLinkedSensorsFromHubEvents() {
        hubConfigurationService.handleHubEvent(deviceAddedEvent("hub-1", "sensor.light.1", DeviceTypeAvro.LIGHT_SENSOR));

        HubEventAvro scenarioAddedEvent = HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(ScenarioAddedEventAvro.newBuilder()
                        .setName("hall-light")
                        .setConditions(List.of(
                                ScenarioConditionAvro.newBuilder()
                                        .setSensorId("sensor.light.1")
                                        .setType(ConditionTypeAvro.LUMINOSITY)
                                        .setOperation(ConditionOperationAvro.LOWER_THAN)
                                        .setValue(20)
                                        .build()
                        ))
                        .setActions(List.of(
                                DeviceActionAvro.newBuilder()
                                        .setSensorId("switch.1")
                                        .setType(ActionTypeAvro.ACTIVATE)
                                        .setValue(1)
                                        .build()
                        ))
                        .build())
                .build();

        hubConfigurationService.handleHubEvent(scenarioAddedEvent);

        List<ScenarioDefinition> scenarios = hubConfigurationService.getScenarios("hub-1");

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.getFirst().conditions()).hasSize(1);
        assertThat(scenarios.getFirst().actions()).hasSize(1);
        assertThat(scenarios.getFirst().conditions().getFirst().type()).isEqualTo(ConditionType.LUMINOSITY);
        assertThat(scenarios.getFirst().conditions().getFirst().operation()).isEqualTo(ConditionOperation.LOWER_THAN);
        assertThat(scenarios.getFirst().actions().getFirst().type()).isEqualTo(ActionType.ACTIVATE);
        assertThat(scenarios.getFirst().actions().getFirst().value()).isEqualTo(1);
        assertThat(sensorRepository.findById("switch.1")).isPresent();
    }

    @Test
    void shouldRemoveSensorAndUnlinkScenarioParts() {
        hubConfigurationService.handleHubEvent(deviceAddedEvent("hub-1", "sensor.motion.1", DeviceTypeAvro.MOTION_SENSOR));
        hubConfigurationService.handleHubEvent(HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(ScenarioAddedEventAvro.newBuilder()
                        .setName("alarm")
                        .setConditions(List.of(
                                ScenarioConditionAvro.newBuilder()
                                        .setSensorId("sensor.motion.1")
                                        .setType(ConditionTypeAvro.MOTION)
                                        .setOperation(ConditionOperationAvro.EQUALS)
                                        .setValue(true)
                                        .build()
                        ))
                        .setActions(List.of(
                                DeviceActionAvro.newBuilder()
                                        .setSensorId("sensor.motion.1")
                                        .setType(ActionTypeAvro.INVERSE)
                                        .build()
                        ))
                        .build())
                .build());

        hubConfigurationService.handleHubEvent(HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(DeviceRemovedEventAvro.newBuilder()
                        .setId("sensor.motion.1")
                        .build())
                .build());

        List<ScenarioDefinition> scenarios = hubConfigurationService.getScenarios("hub-1");

        assertThat(sensorRepository.findById("sensor.motion.1")).isEmpty();
        assertThat(scenarios).isEmpty();
    }

    @Test
    void shouldPreserveNullScenarioActionValue() {
        hubConfigurationService.handleHubEvent(deviceAddedEvent("hub-1", "sensor.motion.1", DeviceTypeAvro.MOTION_SENSOR));
        hubConfigurationService.handleHubEvent(HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(ScenarioAddedEventAvro.newBuilder()
                        .setName("alarm")
                        .setConditions(List.of(
                                ScenarioConditionAvro.newBuilder()
                                        .setSensorId("sensor.motion.1")
                                        .setType(ConditionTypeAvro.MOTION)
                                        .setOperation(ConditionOperationAvro.EQUALS)
                                        .setValue(true)
                                        .build()
                        ))
                        .setActions(List.of(
                                DeviceActionAvro.newBuilder()
                                        .setSensorId("sensor.motion.1")
                                        .setType(ActionTypeAvro.INVERSE)
                                        .build()
                        ))
                        .build())
                .build());

        List<ScenarioDefinition> scenarios = hubConfigurationService.getScenarios("hub-1");

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.getFirst().actions()).hasSize(1);
        assertThat(scenarios.getFirst().actions().getFirst().value()).isNull();
    }

    @Test
    void shouldUpsertExistingScenarioDefinition() {
        hubConfigurationService.handleHubEvent(deviceAddedEvent("hub-1", "sensor.light.1", DeviceTypeAvro.LIGHT_SENSOR));
        hubConfigurationService.handleHubEvent(deviceAddedEvent("hub-1", "switch.1", DeviceTypeAvro.SWITCH_SENSOR));
        hubConfigurationService.handleHubEvent(HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(ScenarioAddedEventAvro.newBuilder()
                        .setName("hall-light")
                        .setConditions(List.of(
                                ScenarioConditionAvro.newBuilder()
                                        .setSensorId("sensor.light.1")
                                        .setType(ConditionTypeAvro.LUMINOSITY)
                                        .setOperation(ConditionOperationAvro.LOWER_THAN)
                                        .setValue(20)
                                        .build()
                        ))
                        .setActions(List.of(
                                DeviceActionAvro.newBuilder()
                                        .setSensorId("switch.1")
                                        .setType(ActionTypeAvro.ACTIVATE)
                                        .setValue(1)
                                        .build()
                        ))
                        .build())
                .build());

        hubConfigurationService.handleHubEvent(HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:12:24.157Z"))
                .setPayload(ScenarioAddedEventAvro.newBuilder()
                        .setName("hall-light")
                        .setConditions(List.of(
                                ScenarioConditionAvro.newBuilder()
                                        .setSensorId("sensor.light.1")
                                        .setType(ConditionTypeAvro.LUMINOSITY)
                                        .setOperation(ConditionOperationAvro.GREATER_THAN)
                                        .setValue(50)
                                        .build()
                        ))
                        .setActions(List.of(
                                DeviceActionAvro.newBuilder()
                                        .setSensorId("switch.1")
                                        .setType(ActionTypeAvro.DEACTIVATE)
                                        .build()
                        ))
                        .build())
                .build());

        List<ScenarioDefinition> scenarios = hubConfigurationService.getScenarios("hub-1");

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.getFirst().name()).isEqualTo("hall-light");
        assertThat(scenarios.getFirst().conditions()).singleElement()
                .satisfies(condition -> {
                    assertThat(condition.type()).isEqualTo(ConditionType.LUMINOSITY);
                    assertThat(condition.operation()).isEqualTo(ConditionOperation.GREATER_THAN);
                    assertThat(condition.value()).isEqualTo(50);
                });
        assertThat(scenarios.getFirst().actions()).singleElement()
                .satisfies(action -> {
                    assertThat(action.type()).isEqualTo(ActionType.DEACTIVATE);
                    assertThat(action.value()).isNull();
                });
    }

    private HubEventAvro deviceAddedEvent(String hubId, String sensorId, DeviceTypeAvro type) {
        return HubEventAvro.newBuilder()
                .setHubId(hubId)
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(DeviceAddedEventAvro.newBuilder()
                        .setId(sensorId)
                        .setType(type)
                        .build())
                .build();
    }
}
