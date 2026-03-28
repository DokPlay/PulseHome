package ru.yandex.practicum.pulsehome.infra.hubrouterstub;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hub-router-stub")
public class HubRouterStubProperties {

    @Min(1)
    @Max(65535)
    private int port;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
