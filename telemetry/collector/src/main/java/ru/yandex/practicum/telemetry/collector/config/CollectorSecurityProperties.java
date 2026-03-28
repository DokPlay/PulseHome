package ru.yandex.practicum.telemetry.collector.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "collector.security")
public class CollectorSecurityProperties {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private boolean requireHttps;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isRequireHttps() {
        return requireHttps;
    }

    public void setRequireHttps(boolean requireHttps) {
        this.requireHttps = requireHttps;
    }
}
