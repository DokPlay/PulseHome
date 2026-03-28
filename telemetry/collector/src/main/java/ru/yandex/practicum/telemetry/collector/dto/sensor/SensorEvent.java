package ru.yandex.practicum.telemetry.collector.dto.sensor;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

import java.time.Instant;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ClimateSensorEvent.class, name = "CLIMATE_SENSOR_EVENT"),
        @JsonSubTypes.Type(value = LightSensorEvent.class, name = "LIGHT_SENSOR_EVENT"),
        @JsonSubTypes.Type(value = MotionSensorEvent.class, name = "MOTION_SENSOR_EVENT"),
        @JsonSubTypes.Type(value = SwitchSensorEvent.class, name = "SWITCH_SENSOR_EVENT"),
        @JsonSubTypes.Type(value = TemperatureSensorEvent.class, name = "TEMPERATURE_SENSOR_EVENT")
})
public sealed interface SensorEvent permits ClimateSensorEvent, LightSensorEvent, MotionSensorEvent, SwitchSensorEvent, TemperatureSensorEvent {

    String id();

    String hubId();

    Instant timestamp();

    SensorEventType type();

    default String getId() {
        return id();
    }

    default String getHubId() {
        return hubId();
    }

    default Instant getTimestamp() {
        return timestamp();
    }

    default SensorEventType getType() {
        return type();
    }
}
