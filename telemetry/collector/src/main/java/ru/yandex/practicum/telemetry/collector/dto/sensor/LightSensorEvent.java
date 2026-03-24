package ru.yandex.practicum.telemetry.collector.dto.sensor;

import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

public class LightSensorEvent extends SensorEvent {

    @NotNull
    private Integer linkQuality;

    @NotNull
    private Integer luminosity;

    public LightSensorEvent() {
        super(SensorEventType.LIGHT_SENSOR_EVENT);
    }

    public Integer getLinkQuality() {
        return linkQuality;
    }

    public void setLinkQuality(Integer linkQuality) {
        this.linkQuality = linkQuality;
    }

    public Integer getLuminosity() {
        return luminosity;
    }

    public void setLuminosity(Integer luminosity) {
        this.luminosity = luminosity;
    }
}
