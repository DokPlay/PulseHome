package ru.yandex.practicum.telemetry.collector.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceAddedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceRemovedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioAddedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioRemovedEvent;

import static org.assertj.core.api.Assertions.assertThat;

class HubEventDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldDeserializeDeviceAddedEvent() throws Exception {
        String payload = """
                {
                  "hubId": "hub.12345",
                  "timestamp": "2024-08-06T15:11:24.157Z",
                  "type": "DEVICE_ADDED",
                  "id": "sensor.light.3",
                  "deviceType": "MOTION_SENSOR"
                }
                """;

        HubEvent event = objectMapper.readValue(payload, HubEvent.class);

        assertThat(event).isInstanceOf(DeviceAddedEvent.class);
        DeviceAddedEvent deviceAddedEvent = (DeviceAddedEvent) event;
        assertThat(deviceAddedEvent.getHubId()).isEqualTo("hub.12345");
        assertThat(deviceAddedEvent.getId()).isEqualTo("sensor.light.3");
        assertThat(deviceAddedEvent.getDeviceType().name()).isEqualTo("MOTION_SENSOR");
    }

    @Test
    void shouldDeserializeScenarioAddedEvent() throws Exception {
        String payload = """
                {
                  "hubId": "hub-1",
                  "timestamp": "2024-08-06T15:11:24.157Z",
                  "type": "SCENARIO_ADDED",
                  "name": "Night light",
                  "conditions": [
                    {
                      "sensorId": "sensor.motion.1",
                      "type": "MOTION",
                      "operation": "EQUALS",
                      "value": 1
                    }
                  ],
                  "actions": [
                    {
                      "sensorId": "sensor.switch.1",
                      "type": "ACTIVATE"
                    }
                  ]
                }
                """;

        HubEvent event = objectMapper.readValue(payload, HubEvent.class);

        assertThat(event).isInstanceOf(ScenarioAddedEvent.class);
        ScenarioAddedEvent scenarioAddedEvent = (ScenarioAddedEvent) event;
        assertThat(scenarioAddedEvent.getName()).isEqualTo("Night light");
        assertThat(scenarioAddedEvent.getConditions()).hasSize(1);
        assertThat(scenarioAddedEvent.getActions()).hasSize(1);
    }

    @Test
    void shouldDeserializeDeviceRemovedEvent() throws Exception {
        String payload = """
                {
                  "hubId": "hub.12345",
                  "timestamp": "2024-08-06T15:11:24.157Z",
                  "type": "DEVICE_REMOVED",
                  "id": "sensor.light.3"
                }
                """;

        HubEvent event = objectMapper.readValue(payload, HubEvent.class);

        assertThat(event).isInstanceOf(DeviceRemovedEvent.class);
        DeviceRemovedEvent deviceRemovedEvent = (DeviceRemovedEvent) event;
        assertThat(deviceRemovedEvent.getId()).isEqualTo("sensor.light.3");
    }

    @Test
    void shouldDeserializeScenarioRemovedEvent() throws Exception {
        String payload = """
                {
                  "hubId": "hub-1",
                  "timestamp": "2024-08-06T15:11:24.157Z",
                  "type": "SCENARIO_REMOVED",
                  "name": "Night light"
                }
                """;

        HubEvent event = objectMapper.readValue(payload, HubEvent.class);

        assertThat(event).isInstanceOf(ScenarioRemovedEvent.class);
        ScenarioRemovedEvent scenarioRemovedEvent = (ScenarioRemovedEvent) event;
        assertThat(scenarioRemovedEvent.getName()).isEqualTo("Night light");
    }
}
