package ru.yandex.practicum.telemetry.analyzer.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "analyzer.grpc")
public class AnalyzerGrpcProperties {

    @NotNull
    @Valid
    private HubRouter hubRouter = new HubRouter();

    public HubRouter getHubRouter() {
        return hubRouter;
    }

    public void setHubRouter(HubRouter hubRouter) {
        this.hubRouter = hubRouter;
    }

    public static class HubRouter {

        @NotNull
        private Duration actionTimeout = Duration.ofSeconds(3);

        public Duration getActionTimeout() {
            return actionTimeout;
        }

        public void setActionTimeout(Duration actionTimeout) {
            this.actionTimeout = actionTimeout;
        }

        @AssertTrue(message = "actionTimeout must be positive")
        public boolean isActionTimeoutPositive() {
            return actionTimeout != null && !actionTimeout.isNegative() && !actionTimeout.isZero();
        }
    }
}
