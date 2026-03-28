package ru.yandex.practicum.telemetry.aggregator.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "aggregator.kafka")
public class AggregatorKafkaProperties {

    @NotBlank
    private String bootstrapServers = "localhost:9092";

    @NotNull
    @DurationMin(millis = 100)
    private Duration pollTimeout = Duration.ofSeconds(1);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration sendTimeout = Duration.ofSeconds(10);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration snapshotRestoreTimeout = Duration.ofSeconds(30);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration topicBootstrapTimeout = Duration.ofSeconds(10);

    private boolean topicBootstrapEnabled;

    @NotNull
    @Valid
    private Consumer consumer = new Consumer();

    @NotNull
    @Valid
    private Producer producer = new Producer();

    @NotNull
    @Valid
    private Topics topics = new Topics();

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public Duration getPollTimeout() {
        return pollTimeout;
    }

    public void setPollTimeout(Duration pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public Duration getSnapshotRestoreTimeout() {
        return snapshotRestoreTimeout;
    }

    public void setSnapshotRestoreTimeout(Duration snapshotRestoreTimeout) {
        this.snapshotRestoreTimeout = snapshotRestoreTimeout;
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

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    public Producer getProducer() {
        return producer;
    }

    public void setProducer(Producer producer) {
        this.producer = producer;
    }

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    @AssertTrue(message = "aggregator.kafka.sendTimeout must be greater than aggregator.kafka.producer.lingerMs")
    public boolean isSendTimeoutCompatibleWithProducerLinger() {
        if (sendTimeout == null || producer == null) {
            return true;
        }
        return sendTimeout.toMillis() > producer.getLingerMs();
    }

    public static class Consumer {

        @NotBlank
        private String groupId = "aggregator";

        @NotBlank
        private String clientId = "aggregator-client";

        @NotBlank
        private String autoOffsetReset = "earliest";

        @Positive
        private int maxPollRecords = 500;

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        public int getMaxPollRecords() {
            return maxPollRecords;
        }

        public void setMaxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
        }
    }

    public static class Producer {

        @NotBlank
        private String acks = "all";

        private boolean enableIdempotence = true;

        @NotBlank
        private String compressionType = "snappy";

        @PositiveOrZero
        private int lingerMs = 5;

        @Positive
        private int batchSize = 32_768;

        @Positive
        private int maxInFlightRequestsPerConnection = 5;

        public String getAcks() {
            return acks;
        }

        public void setAcks(String acks) {
            this.acks = acks;
        }

        public boolean isEnableIdempotence() {
            return enableIdempotence;
        }

        public void setEnableIdempotence(boolean enableIdempotence) {
            this.enableIdempotence = enableIdempotence;
        }

        public String getCompressionType() {
            return compressionType;
        }

        public void setCompressionType(String compressionType) {
            this.compressionType = compressionType;
        }

        public int getLingerMs() {
            return lingerMs;
        }

        public void setLingerMs(int lingerMs) {
            this.lingerMs = lingerMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxInFlightRequestsPerConnection() {
            return maxInFlightRequestsPerConnection;
        }

        public void setMaxInFlightRequestsPerConnection(int maxInFlightRequestsPerConnection) {
            this.maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection;
        }

        @AssertTrue(message = "aggregator.kafka.producer.maxInFlightRequestsPerConnection must be <= 5 when idempotence is enabled")
        public boolean isIdempotenceCompatibleWithMaxInFlight() {
            return !enableIdempotence || maxInFlightRequestsPerConnection <= 5;
        }
    }

    public static class Topics {

        @NotBlank
        private String sensors = "telemetry.sensors.v1";

        @NotBlank
        private String snapshots = "telemetry.snapshots.v1";

        public String getSensors() {
            return sensors;
        }

        public void setSensors(String sensors) {
            this.sensors = sensors;
        }

        public String getSnapshots() {
            return snapshots;
        }

        public void setSnapshots(String snapshots) {
            this.snapshots = snapshots;
        }
    }
}
