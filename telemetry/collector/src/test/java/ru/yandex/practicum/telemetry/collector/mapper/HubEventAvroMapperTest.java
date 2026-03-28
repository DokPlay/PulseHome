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
        ScenarioAddedEvent event = new ScenarioAddedEvent(
                "hub-2",
                Instant.parse("2024-08-06T16:54:03.129Z"),
                "Night light",
                List.of(motionCondition(), temperatureCondition()),
                List.of(action())
        );

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
        ScenarioAddedEvent event = new ScenarioAddedEvent(
                "hub-2",
                null,
                "Broken scenario",
                List.of(invalidSwitchCondition()),
                List.of(action())
        );

        assertThatThrownBy(() -> mapper.toAvro(event))
                .isInstanceOf(InvalidScenarioConditionValueException.class)
                .hasMessageContaining("0 or 1");
    }

    @Test
    void shouldMapDeviceAddedEvent() {
        DeviceAddedEvent event = new DeviceAddedEvent("hub-1", null, "sensor.motion.1", DeviceType.MOTION_SENSOR);

        DeviceAddedEventAvro payload = (DeviceAddedEventAvro) mapper.toAvro(event).getPayload();

        assertThat(payload.getId()).isEqualTo("sensor.motion.1");
        assertThat(payload.getType().name()).isEqualTo("MOTION_SENSOR");
    }

    @Test
    void shouldMapDeviceRemovedEvent() {
        DeviceRemovedEvent event = new DeviceRemovedEvent("hub-1", null, "sensor.motion.1");

        DeviceRemovedEventAvro payload = (DeviceRemovedEventAvro) mapper.toAvro(event).getPayload();

        assertThat(payload.getId()).isEqualTo("sensor.motion.1");
    }

    @Test
    void shouldMapScenarioRemovedEvent() {
        ScenarioRemovedEvent event = new ScenarioRemovedEvent("hub-1", null, "Night light");

        ScenarioRemovedEventAvro payload = (ScenarioRemovedEventAvro) mapper.toAvro(event).getPayload();

        assertThat(payload.getName()).isEqualTo("Night light");
    }

    private ScenarioCondition motionCondition() {
        return new ScenarioCondition("sensor.motion.1", ConditionType.MOTION, ConditionOperation.EQUALS, 1);
    }

    private ScenarioCondition temperatureCondition() {
        return new ScenarioCondition("sensor.temperature.1", ConditionType.TEMPERATURE, ConditionOperation.GREATER_THAN, 23);
    }

    private ScenarioCondition invalidSwitchCondition() {
        return new ScenarioCondition("sensor.switch.1", ConditionType.SWITCH, ConditionOperation.EQUALS, 2);
    }

    private DeviceAction action() {
        return new DeviceAction("sensor.lamp.1", ActionType.SET_VALUE, 75);
    }
}
