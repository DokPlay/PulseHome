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

    @NotNull
    private Duration topicBootstrapTimeout = Duration.ofSeconds(10);

    private boolean topicBootstrapEnabled;

    @NotNull
    @Valid
    private Ssl ssl = new Ssl();

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

    public Duration getTopicBootstrapTimeout() {
        return topicBootstrapTimeout;
    }

    public void setTopicBootstrapTimeout(Duration topicBootstrapTimeout) {
        this.topicBootstrapTimeout = topicBootstrapTimeout;
    }

    public boolean isTopicBootstrapEnabled() {
        return topicBootstrapEnabled;
    }

    public void setTopicBootstrapEnabled(boolean topicBootstrapEnabled) {
        this.topicBootstrapEnabled = topicBootstrapEnabled;
    }

    public Ssl getSsl() {
        return ssl;
    }

    public void setSsl(Ssl ssl) {
        this.ssl = ssl;
    }

    public static class Topics {

        @NotBlank
        private String hubs = "telemetry.hubs.v1";

        @NotBlank
        private String hubsDlq = "telemetry.hubs.dlq.v1";

        @NotBlank
        private String snapshots = "telemetry.snapshots.v1";

        @NotBlank
        private String snapshotsDlq = "telemetry.snapshots.dlq.v1";

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

        public String getSnapshotsDlq() {
            return snapshotsDlq;
        }

        public void setSnapshotsDlq(String snapshotsDlq) {
            this.snapshotsDlq = snapshotsDlq;
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

        @Min(1)
        private int maxPollIntervalMs = (int) Duration.ofMinutes(5).toMillis();

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

        public int getMaxPollIntervalMs() {
            return maxPollIntervalMs;
        }

        public void setMaxPollIntervalMs(int maxPollIntervalMs) {
            this.maxPollIntervalMs = maxPollIntervalMs;
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

    public static class Ssl {

        private String securityProtocol = "PLAINTEXT";
        private String truststoreLocation = "";
        private String truststorePassword = "";
        private String keystoreLocation = "";
        private String keystorePassword = "";
        private String keyPassword = "";

        public boolean isEnabled() {
            return "SSL".equalsIgnoreCase(securityProtocol);
        }

        public String getSecurityProtocol() {
            return securityProtocol;
        }

        public void setSecurityProtocol(String securityProtocol) {
            this.securityProtocol = securityProtocol;
        }

        public String getTruststoreLocation() {
            return truststoreLocation;
        }

        public void setTruststoreLocation(String truststoreLocation) {
            this.truststoreLocation = truststoreLocation;
        }

        public String getTruststorePassword() {
            return truststorePassword;
        }

        public void setTruststorePassword(String truststorePassword) {
            this.truststorePassword = truststorePassword;
        }

        public String getKeystoreLocation() {
            return keystoreLocation;
        }

        public void setKeystoreLocation(String keystoreLocation) {
            this.keystoreLocation = keystoreLocation;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public void setKeystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
        }
    }
}
