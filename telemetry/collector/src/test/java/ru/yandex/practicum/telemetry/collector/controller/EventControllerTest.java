package ru.yandex.practicum.telemetry.collector.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.telemetry.collector.exception.InvalidScenarioConditionValueException;
import ru.yandex.practicum.telemetry.collector.service.CollectorEventService;
import ru.yandex.practicum.telemetry.collector.service.EventPublishException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CollectorEventService collectorEventService;

    @Test
    void shouldAcceptSensorEvents() throws Exception {
        String payload = """
                {
                  "id": "sensor.light.3",
                  "hubId": "hub-2",
                  "timestamp": "2024-08-06T16:54:03.129Z",
                  "type": "LIGHT_SENSOR_EVENT",
                  "linkQuality": 75,
                  "luminosity": 59
                }
                """;

        mockMvc.perform(post("/events/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(collectorEventService).collectSensorEvent(any());
    }

    @Test
    void shouldRejectLightSensorEventWithoutRequiredMetrics() throws Exception {
        String payload = """
                {
                  "id": "sensor.light.3",
                  "hubId": "hub-2",
                  "timestamp": "2024-08-06T16:54:03.129Z",
                  "type": "LIGHT_SENSOR_EVENT",
                  "linkQuality": 75
                }
                """;

        mockMvc.perform(post("/events/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAcceptHubEvents() throws Exception {
        String payload = """
                {
                  "hubId": "hub-1",
                  "timestamp": "2024-08-06T15:11:24.157Z",
                  "type": "DEVICE_ADDED",
                  "id": "sensor.light.3",
                  "deviceType": "MOTION_SENSOR"
                }
                """;

        mockMvc.perform(post("/events/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(collectorEventService).collectHubEvent(any());
    }

    @Test
    void shouldRejectInvalidScenarioWithoutName() throws Exception {
        String payload = """
                {
                  "hubId": "hub-1",
                  "timestamp": "2024-08-06T15:11:24.157Z",
                  "type": "SCENARIO_ADDED",
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

        mockMvc.perform(post("/events/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUnknownSensorType() throws Exception {
        String payload = """
                {
                  "id": "sensor.light.3",
                  "hubId": "hub-2",
                  "timestamp": "2024-08-06T16:54:03.129Z",
                  "type": "UNKNOWN_SENSOR_EVENT",
                  "linkQuality": 75,
                  "luminosity": 59
                }
                """;

        mockMvc.perform(post("/events/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request body"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenKafkaPublishFails() throws Exception {
        String payload = """
                {
                  "id": "sensor.light.3",
                  "hubId": "hub-2",
                  "timestamp": "2024-08-06T16:54:03.129Z",
                  "type": "LIGHT_SENSOR_EVENT",
                  "linkQuality": 75,
                  "luminosity": 59
                }
                """;

        doThrow(new EventPublishException("Failed to publish event to Kafka. topic=telemetry.sensors.v1, key=hub-2, cause=broker timeout", null))
                .when(collectorEventService)
                .collectSensorEvent(any());

        mockMvc.perform(post("/events/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to publish event to Kafka. topic=telemetry.sensors.v1, key=hub-2, cause=broker timeout"));
    }

    @Test
    void shouldReturnBadRequestForInvalidBooleanLikeConditionValue() throws Exception {
        String payload = """
                {
                  "hubId": "hub-1",
                  "timestamp": "2024-08-06T15:11:24.157Z",
                  "type": "SCENARIO_ADDED",
                  "name": "Night light",
                  "conditions": [
                    {
                      "sensorId": "sensor.motion.1",
                      "type": "SWITCH",
                      "operation": "EQUALS",
                      "value": 2
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

        doThrow(new InvalidScenarioConditionValueException("Boolean-like condition value must be 0 or 1"))
                .when(collectorEventService)
                .collectHubEvent(any());

        mockMvc.perform(post("/events/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Boolean-like condition value must be 0 or 1"));
    }

    @Test
    void shouldReturnInternalServerErrorForUnexpectedFailures() throws Exception {
        String payload = """
                {
                  "id": "sensor.light.3",
                  "hubId": "hub-2",
                  "timestamp": "2024-08-06T16:54:03.129Z",
                  "type": "LIGHT_SENSOR_EVENT",
                  "linkQuality": 75,
                  "luminosity": 59
                }
                """;

        doThrow(new RuntimeException("unexpected")).when(collectorEventService).collectSensorEvent(any());

        mockMvc.perform(post("/events/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }
}
