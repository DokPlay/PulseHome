package ru.yandex.practicum.telemetry.collector.mapper;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioConditionAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioRemovedEventAvro;
import ru.yandex.practicum.telemetry.collector.exception.InvalidScenarioConditionValueException;
import ru.yandex.practicum.telemetry.collector.dto.enums.ActionType;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionOperation;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionType;
import ru.yandex.practicum.telemetry.collector.dto.enums.DeviceType;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceAction;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceAddedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceRemovedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioAddedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioCondition;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioRemovedEvent;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HubEventAvroMapperTest {

    private final HubEventAvroMapper mapper = new HubEventAvroMapper();

    @Test
    void shouldMapScenarioAddedEventAndConvertBooleanLikeConditions() {
        ScenarioAddedEvent event = new ScenarioAddedEvent();
        event.setHubId("hub-2");
        event.setTimestamp(Instant.parse("2024-08-06T16:54:03.129Z"));
        event.setName("Night light");
        event.setConditions(List.of(motionCondition(), temperatureCondition()));
        event.setActions(List.of(action()));

        HubEvent avroEvent = event;
        ScenarioAddedEventAvro payload = (ScenarioAddedEventAvro) mapper.toAvro(avroEvent).getPayload();

        assertThat(payload.getName()).isEqualTo("Night light");
        assertThat(payload.getConditions()).hasSize(2);
        ScenarioConditionAvro motionCondition = payload.getConditions().get(0);
        ScenarioConditionAvro temperatureCondition = payload.getConditions().get(1);
        assertThat(motionCondition.getValue()).isEqualTo(true);
        assertThat(temperatureCondition.getValue()).isEqualTo(23);
        assertThat(payload.getActions()).hasSize(1);
        assertThat(payload.getActions().get(0).getValue()).isEqualTo(75);
    }

    @Test
    void shouldRejectInvalidBooleanLikeConditionValues() {
        ScenarioAddedEvent event = new ScenarioAddedEvent();
        event.setHubId("hub-2");
        event.setName("Broken scenario");
        event.setConditions(List.of(invalidSwitchCondition()));
        event.setActions(List.of(action()));

        assertThatThrownBy(() -> mapper.toAvro(event))
                .isInstanceOf(InvalidScenarioConditionValueException.class)
                .hasMessageContaining("0 or 1");
    }

    @Test
    void shouldMapDeviceAddedEvent() {
        DeviceAddedEvent event = new DeviceAddedEvent();
        event.setHubId("hub-1");
        event.setId("sensor.motion.1");
        event.setDeviceType(DeviceType.MOTION_SENSOR);

        DeviceAddedEventAvro payload = (DeviceAddedEventAvro) mapper.toAvro(event).getPayload();

        assertThat(payload.getId()).isEqualTo("sensor.motion.1");
        assertThat(payload.getType().name()).isEqualTo("MOTION_SENSOR");
    }

    @Test
    void shouldMapDeviceRemovedEvent() {
        DeviceRemovedEvent event = new DeviceRemovedEvent();
        event.setHubId("hub-1");
        event.setId("sensor.motion.1");

        DeviceRemovedEventAvro payload = (DeviceRemovedEventAvro) mapper.toAvro(event).getPayload();

        assertThat(payload.getId()).isEqualTo("sensor.motion.1");
    }

    @Test
    void shouldMapScenarioRemovedEvent() {
        ScenarioRemovedEvent event = new ScenarioRemovedEvent();
        event.setHubId("hub-1");
        event.setName("Night light");

        ScenarioRemovedEventAvro payload = (ScenarioRemovedEventAvro) mapper.toAvro(event).getPayload();

        assertThat(payload.getName()).isEqualTo("Night light");
    }

    private ScenarioCondition motionCondition() {
        ScenarioCondition condition = new ScenarioCondition();
        condition.setSensorId("sensor.motion.1");
        condition.setType(ConditionType.MOTION);
        condition.setOperation(ConditionOperation.EQUALS);
        condition.setValue(1);
        return condition;
    }

    private ScenarioCondition temperatureCondition() {
        ScenarioCondition condition = new ScenarioCondition();
        condition.setSensorId("sensor.temperature.1");
        condition.setType(ConditionType.TEMPERATURE);
        condition.setOperation(ConditionOperation.GREATER_THAN);
        condition.setValue(23);
        return condition;
    }

    private ScenarioCondition invalidSwitchCondition() {
        ScenarioCondition condition = new ScenarioCondition();
        condition.setSensorId("sensor.switch.1");
        condition.setType(ConditionType.SWITCH);
        condition.setOperation(ConditionOperation.EQUALS);
        condition.setValue(2);
        return condition;
    }

    private DeviceAction action() {
        DeviceAction action = new DeviceAction();
        action.setSensorId("sensor.lamp.1");
        action.setType(ActionType.SET_VALUE);
        action.setValue(75);
        return action;
    }
}
