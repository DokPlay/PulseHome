package ru.yandex.practicum.telemetry.collector.config;

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
@ConfigurationProperties(prefix = "collector.kafka")
public class CollectorKafkaProperties {

    @NotBlank
    private String bootstrapServers = "localhost:9092";

    @NotNull
    @DurationMin(seconds = 1)
    private Duration sendTimeout = Duration.ofSeconds(10);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration topicBootstrapTimeout = Duration.ofSeconds(10);

    private boolean topicBootstrapEnabled;

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

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
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

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    public Producer getProducer() {
        return producer;
    }

    public void setProducer(Producer producer) {
        this.producer = producer;
    }

    @AssertTrue(message = "collector.kafka.sendTimeout must be greater than collector.kafka.producer.lingerMs")
    public boolean isSendTimeoutCompatibleWithProducerLinger() {
        if (sendTimeout == null || producer == null) {
            return true;
        }
        return sendTimeout.toMillis() > producer.getLingerMs();
    }

    public static class Topics {

        @NotBlank
        private String sensors = "telemetry.sensors.v1";

        @NotBlank
        private String hubs = "telemetry.hubs.v1";

        public String getSensors() {
            return sensors;
        }

        public void setSensors(String sensors) {
            this.sensors = sensors;
        }

        public String getHubs() {
            return hubs;
        }

        public void setHubs(String hubs) {
            this.hubs = hubs;
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

        @AssertTrue(message = "collector.kafka.producer.maxInFlightRequestsPerConnection must be <= 5 when idempotence is enabled")
        public boolean isIdempotenceCompatibleWithMaxInFlight() {
            return !enableIdempotence || maxInFlightRequestsPerConnection <= 5;
        }
    }
}
