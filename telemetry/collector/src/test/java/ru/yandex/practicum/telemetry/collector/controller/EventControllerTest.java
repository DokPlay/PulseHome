package ru.yandex.practicum.telemetry.collector.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.yandex.practicum.telemetry.collector.exception.InvalidScenarioConditionValueException;
import ru.yandex.practicum.telemetry.collector.service.CollectorEventService;
import ru.yandex.practicum.telemetry.collector.service.EventPublishException;

import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    private static final MediaType JSON_MEDIA_TYPE = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CollectorEventService collectorEventService;

    @SuppressWarnings("null")
    private static MockHttpServletRequestBuilder postJson(String url, String payload) {
        return post(url)
                .contentType(JSON_MEDIA_TYPE)
                .content(payload);
    }

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

        mockMvc.perform(postJson("/events/sensors", payload))
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

        mockMvc.perform(postJson("/events/sensors", payload))
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

        mockMvc.perform(postJson("/events/hubs", payload))
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

        mockMvc.perform(postJson("/events/hubs", payload))
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

        mockMvc.perform(postJson("/events/sensors", payload))
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

        mockMvc.perform(postJson("/events/sensors", payload))
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

        mockMvc.perform(postJson("/events/hubs", payload))
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

        mockMvc.perform(postJson("/events/sensors", payload))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }
}
