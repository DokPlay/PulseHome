package ru.yandex.practicum.telemetry.collector.dto.sensor;

import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;

public class ClimateSensorEvent extends SensorEvent {

    @NotNull
    private Integer temperatureC;

    @NotNull
    private Integer humidity;

    @NotNull
    private Integer co2Level;

    public ClimateSensorEvent() {
        super(SensorEventType.CLIMATE_SENSOR_EVENT);
    }

    public Integer getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(Integer temperatureC) {
        this.temperatureC = temperatureC;
    }

    public Integer getHumidity() {
        return humidity;
    }

    public void setHumidity(Integer humidity) {
        this.humidity = humidity;
    }

    public Integer getCo2Level() {
        return co2Level;
    }

    public void setCo2Level(Integer co2Level) {
        this.co2Level = co2Level;
    }
}
