package ru.yandex.practicum.telemetry.collector.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, byte[]> producerFactory(CollectorKafkaProperties properties) {
        int sendTimeoutMs = Math.toIntExact(properties.getSendTimeout().toMillis());
        CollectorKafkaProperties.Producer producer = properties.getProducer();
        // Keep producer-level timeouts inside the HTTP-facing send timeout so Sprint 19
        // responds only after a definitive Kafka outcome without lingering producer retries.
        int maxRequestTimeoutMs = Math.max(1, sendTimeoutMs - producer.getLingerMs() - 1);
        int requestTimeoutMs = Math.min(Math.max(1_000, sendTimeoutMs - 1_000), maxRequestTimeoutMs);

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configuration.put(ProducerConfig.ACKS_CONFIG, producer.getAcks());
        configuration.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, producer.isEnableIdempotence());
        configuration.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producer.getCompressionType());
        configuration.put(ProducerConfig.LINGER_MS_CONFIG, producer.getLingerMs());
        configuration.put(ProducerConfig.BATCH_SIZE_CONFIG, producer.getBatchSize());
        configuration.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, producer.getMaxInFlightRequestsPerConnection());
        configuration.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, sendTimeoutMs);
        configuration.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        configuration.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, sendTimeoutMs);
        return new DefaultKafkaProducerFactory<String, byte[]>(configuration);
    }

    @Bean
    public KafkaTemplate<String, byte[]> kafkaTemplate(ProducerFactory<String, byte[]> producerFactory) {
        ProducerFactory<String, byte[]> nonNullProducerFactory =
                Objects.requireNonNull(producerFactory, "producerFactory must not be null");
        return new KafkaTemplate<>(nonNullProducerFactory);
    }
}
