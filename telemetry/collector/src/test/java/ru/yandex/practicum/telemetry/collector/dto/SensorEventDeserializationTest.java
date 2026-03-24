package ru.yandex.practicum.telemetry.collector.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.telemetry.collector.dto.sensor.LightSensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.TemperatureSensorEvent;

import static org.assertj.core.api.Assertions.assertThat;

class SensorEventDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldDeserializeLightSensorEvent() throws Exception {
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

        SensorEvent event = objectMapper.readValue(payload, SensorEvent.class);

        assertThat(event).isInstanceOf(LightSensorEvent.class);
        LightSensorEvent lightSensorEvent = (LightSensorEvent) event;
        assertThat(lightSensorEvent.getId()).isEqualTo("sensor.light.3");
        assertThat(lightSensorEvent.getHubId()).isEqualTo("hub-2");
        assertThat(lightSensorEvent.getLinkQuality()).isEqualTo(75);
        assertThat(lightSensorEvent.getLuminosity()).isEqualTo(59);
    }

    @Test
    void shouldDeserializeTemperatureSensorEvent() throws Exception {
        String payload = """
                {
                  "id": "sensor.temperature.1",
                  "hubId": "hub-9",
                  "timestamp": "2024-08-06T16:54:03.129Z",
                  "type": "TEMPERATURE_SENSOR_EVENT",
                  "temperatureC": 23,
                  "temperatureF": 73
                }
                """;

        SensorEvent event = objectMapper.readValue(payload, SensorEvent.class);

        assertThat(event).isInstanceOf(TemperatureSensorEvent.class);
        TemperatureSensorEvent temperatureSensorEvent = (TemperatureSensorEvent) event;
        assertThat(temperatureSensorEvent.getTemperatureC()).isEqualTo(23);
        assertThat(temperatureSensorEvent.getTemperatureF()).isEqualTo(73);
    }
}
