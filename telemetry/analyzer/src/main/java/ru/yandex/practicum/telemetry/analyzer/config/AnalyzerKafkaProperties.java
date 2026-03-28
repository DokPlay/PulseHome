package ru.yandex.practicum.telemetry.analyzer.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "analyzer.kafka")
public class AnalyzerKafkaProperties {

    @NotBlank
    private String bootstrapServers = "localhost:9092";

    @NotNull
    @Valid
    private Topics topics = new Topics();

    @NotNull
    @Valid
    private ConsumerSettings snapshotsConsumer = new ConsumerSettings("analyzer-snapshots");

    @NotNull
    @Valid
    private ConsumerSettings hubsConsumer = new ConsumerSettings("analyzer-hubs");

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    public ConsumerSettings getSnapshotsConsumer() {
        return snapshotsConsumer;
    }

    public void setSnapshotsConsumer(ConsumerSettings snapshotsConsumer) {
        this.snapshotsConsumer = snapshotsConsumer;
    }

    public ConsumerSettings getHubsConsumer() {
        return hubsConsumer;
    }

    public void setHubsConsumer(ConsumerSettings hubsConsumer) {
        this.hubsConsumer = hubsConsumer;
    }

    public static class Topics {

        @NotBlank
        private String hubs = "telemetry.hubs.v1";

        @NotBlank
        private String hubsDlq = "telemetry.hubs.dlq.v1";

        @NotBlank
        private String snapshots = "telemetry.snapshots.v1";

        public String getHubs() {
            return hubs;
        }

        public void setHubs(String hubs) {
            this.hubs = hubs;
        }

        public String getHubsDlq() {
            return hubsDlq;
        }

        public void setHubsDlq(String hubsDlq) {
            this.hubsDlq = hubsDlq;
        }

        public String getSnapshots() {
            return snapshots;
        }

        public void setSnapshots(String snapshots) {
            this.snapshots = snapshots;
        }
    }

    public static class ConsumerSettings {

        @NotBlank
        private String groupId;

        @NotBlank
        private String clientIdPrefix = "analyzer";

        @NotNull
        private Duration pollTimeout = Duration.ofSeconds(1);

        @Min(1)
        private int maxPollRecords = 100;

        @NotBlank
        private String autoOffsetReset = "earliest";

        public ConsumerSettings() {
        }

        public ConsumerSettings(String groupId) {
            this.groupId = groupId;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getClientIdPrefix() {
            return clientIdPrefix;
        }

        public void setClientIdPrefix(String clientIdPrefix) {
            this.clientIdPrefix = clientIdPrefix;
        }

        public Duration getPollTimeout() {
            return pollTimeout;
        }

        public void setPollTimeout(Duration pollTimeout) {
            this.pollTimeout = pollTimeout;
        }

        public int getMaxPollRecords() {
            return maxPollRecords;
        }

        public void setMaxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
        }

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        @AssertTrue(message = "pollTimeout must be positive")
        public boolean isPollTimeoutPositive() {
            return pollTimeout != null && !pollTimeout.isNegative() && !pollTimeout.isZero();
        }
    }
}
