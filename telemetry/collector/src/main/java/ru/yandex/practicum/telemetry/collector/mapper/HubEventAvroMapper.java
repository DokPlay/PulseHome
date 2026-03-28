package ru.yandex.practicum.telemetry.collector.mapper;

import org.springframework.stereotype.Component;
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
import ru.yandex.practicum.kafka.telemetry.event.ScenarioRemovedEventAvro;
import ru.yandex.practicum.telemetry.collector.exception.InvalidScenarioConditionValueException;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionType;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceAction;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceAddedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceRemovedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioAddedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioCondition;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioRemovedEvent;

import java.util.List;

@Component
public class HubEventAvroMapper {

    public HubEventAvro toAvro(HubEvent event) {
        return HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(TimestampNormalizer.normalize(event.getTimestamp()))
                .setPayload(mapPayload(event))
                .build();
    }

    private Object mapPayload(HubEvent event) {
        if (event instanceof DeviceAddedEvent deviceAddedEvent) {
            return DeviceAddedEventAvro.newBuilder()
                    .setId(deviceAddedEvent.getId())
                    .setType(DeviceTypeAvro.valueOf(deviceAddedEvent.getDeviceType().name()))
                    .build();
        }
        if (event instanceof DeviceRemovedEvent deviceRemovedEvent) {
            return DeviceRemovedEventAvro.newBuilder()
                    .setId(deviceRemovedEvent.getId())
                    .build();
        }
        if (event instanceof ScenarioAddedEvent scenarioAddedEvent) {
            return ScenarioAddedEventAvro.newBuilder()
                    .setName(scenarioAddedEvent.getName())
                    .setConditions(mapConditions(scenarioAddedEvent.getConditions()))
                    .setActions(mapActions(scenarioAddedEvent.getActions()))
                    .build();
        }
        if (event instanceof ScenarioRemovedEvent scenarioRemovedEvent) {
            return ScenarioRemovedEventAvro.newBuilder()
                    .setName(scenarioRemovedEvent.getName())
                    .build();
        }
        throw new IllegalStateException("Unsupported hub event type: " + event.getClass().getName());
    }

    private List<ScenarioConditionAvro> mapConditions(List<ScenarioCondition> conditions) {
        return conditions.stream()
                .map(this::mapCondition)
                .toList();
    }

    private ScenarioConditionAvro mapCondition(ScenarioCondition condition) {
        return ScenarioConditionAvro.newBuilder()
                .setSensorId(condition.getSensorId())
                .setType(ConditionTypeAvro.valueOf(condition.getType().name()))
                .setOperation(ConditionOperationAvro.valueOf(condition.getOperation().name()))
                .setValue(mapConditionValue(condition))
                .build();
    }

    private Object mapConditionValue(ScenarioCondition condition) {
        if (condition.getValue() == null) {
            return null;
        }
        if (condition.getType() == ConditionType.MOTION || condition.getType() == ConditionType.SWITCH) {
            // Hub Router sends scenario values as integers in JSON, but the Avro contract for
            // boolean-like conditions is boolean. We translate 0/1 explicitly to preserve intent.
            if (condition.getValue() != 0 && condition.getValue() != 1) {
                throw new InvalidScenarioConditionValueException("Boolean-like condition value must be 0 or 1");
            }
            return condition.getValue() == 1;
        }
        return condition.getValue();
    }

    private List<DeviceActionAvro> mapActions(List<DeviceAction> actions) {
        return actions.stream()
                .map(this::mapAction)
                .toList();
    }

    private DeviceActionAvro mapAction(DeviceAction action) {
        return DeviceActionAvro.newBuilder()
                .setSensorId(action.getSensorId())
                .setType(ActionTypeAvro.valueOf(action.getType().name()))
                .setValue(action.getValue())
                .build();
    }

}
