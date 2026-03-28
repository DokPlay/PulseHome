package ru.yandex.practicum.telemetry.collector.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import ru.yandex.practicum.telemetry.collector.config.CollectorSecurityConfig;
import ru.yandex.practicum.telemetry.collector.config.CollectorSecurityProperties;
import ru.yandex.practicum.telemetry.collector.exception.InvalidScenarioConditionValueException;
import ru.yandex.practicum.telemetry.collector.exception.EventPublishException;
import ru.yandex.practicum.telemetry.collector.service.CollectorEventService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = EventController.class, properties = {
        "collector.security.username=test-user",
        "collector.security.password=test-pass"
})
@Import(CollectorSecurityConfig.class)
class EventControllerTest {

    private static final MediaType JSON_MEDIA_TYPE = Objects.requireNonNull(MediaType.APPLICATION_JSON);
    private static final CompletableFuture<Void> COMPLETED_FUTURE = CompletableFuture.completedFuture(null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CollectorEventService collectorEventService;

    @Autowired
    private CollectorSecurityProperties collectorSecurityProperties;

    private ResultActions performJsonPost(String url, String payload) throws Exception {
        return Objects.requireNonNull(mockMvc.perform(post(url)
                .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                        collectorSecurityProperties.getUsername(),
                        collectorSecurityProperties.getPassword()
                ))
                .contentType(JSON_MEDIA_TYPE)
                .content(payload)));
    }

    private ResultActions performUnauthenticatedJsonPost(String url, String payload) throws Exception {
        return Objects.requireNonNull(mockMvc.perform(post(url)
                .contentType(JSON_MEDIA_TYPE)
                .content(payload)));
    }

    private ResultActions performAsyncJsonPost(String url, String payload) throws Exception {
        MvcResult asyncResult = performJsonPost(url, payload)
                .andExpect(request().asyncStarted())
                .andReturn();
        return Objects.requireNonNull(mockMvc.perform(asyncDispatch(asyncResult)));
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

        when(collectorEventService.collectSensorEvent(any())).thenReturn(COMPLETED_FUTURE);

        performAsyncJsonPost("/events/sensors", payload)
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

        performJsonPost("/events/sensors", payload)
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

        when(collectorEventService.collectHubEvent(any())).thenReturn(COMPLETED_FUTURE);

        performAsyncJsonPost("/events/hubs", payload)
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

        performJsonPost("/events/hubs", payload)
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

        performJsonPost("/events/sensors", payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request body"));
    }

    @Test
    void shouldRejectRequestsWithoutAuthentication() throws Exception {
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

        performUnauthenticatedJsonPost("/events/sensors", payload)
                .andExpect(status().isUnauthorized());
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

        when(collectorEventService.collectSensorEvent(any())).thenReturn(
                CompletableFuture.failedFuture(new EventPublishException(
                        "Failed to publish event to Kafka. topic=telemetry.sensors.v1, key=hub-2, cause=broker timeout",
                        null
                ))
        );

        performAsyncJsonPost("/events/sensors", payload)
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

        when(collectorEventService.collectHubEvent(any())).thenReturn(
                CompletableFuture.failedFuture(new InvalidScenarioConditionValueException("Boolean-like condition value must be 0 or 1"))
        );

        performAsyncJsonPost("/events/hubs", payload)
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

        when(collectorEventService.collectSensorEvent(any())).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("unexpected"))
        );

        performAsyncJsonPost("/events/sensors", payload)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }
}
