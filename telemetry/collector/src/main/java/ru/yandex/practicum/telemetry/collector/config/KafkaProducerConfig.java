package ru.yandex.practicum.telemetry.collector.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import ru.yandex.practicum.telemetry.serialization.AvroMessageSerializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Configuration
public class KafkaProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);
    private static final String SSL_PROTOCOL = "TLSv1.3";
    private static final String SSL_ENGINE_FACTORY_CLASS =
            "ru.yandex.practicum.telemetry.collector.config.pqc.HybridPqcSslEngineFactory";

    @Bean
    public ProducerFactory<String, SpecificRecordBase> producerFactory(CollectorKafkaProperties properties) {
        int sendTimeoutMs = Math.toIntExact(properties.getSendTimeout().toMillis());
        CollectorKafkaProperties.Producer producer = properties.getProducer();
        // Keep producer-level timeouts inside the HTTP-facing send timeout so Sprint 19
        // responds only after a definitive Kafka outcome without lingering producer retries.
        int maxRequestTimeoutMs = Math.max(1, sendTimeoutMs - producer.getLingerMs() - 1);
        int requestTimeoutMs = Math.min(Math.max(1_000, sendTimeoutMs - 1_000), maxRequestTimeoutMs);

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroMessageSerializer.class);
        configuration.put(ProducerConfig.ACKS_CONFIG, producer.getAcks());
        configuration.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, producer.isEnableIdempotence());
        configuration.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producer.getCompressionType());
        configuration.put(ProducerConfig.LINGER_MS_CONFIG, producer.getLingerMs());
        configuration.put(ProducerConfig.BATCH_SIZE_CONFIG, producer.getBatchSize());
        configuration.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, producer.getMaxInFlightRequestsPerConnection());
        configuration.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, sendTimeoutMs);
        configuration.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        configuration.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, sendTimeoutMs);
        applySslProperties(configuration, properties);
        return new DefaultKafkaProducerFactory<>(configuration);
    }

    @Bean
    public KafkaTemplate<String, SpecificRecordBase> kafkaTemplate(ProducerFactory<String, SpecificRecordBase> producerFactory) {
        ProducerFactory<String, SpecificRecordBase> nonNullProducerFactory =
                Objects.requireNonNull(producerFactory, "producerFactory must not be null");
        return new KafkaTemplate<>(nonNullProducerFactory);
    }

    @Bean
    public ApplicationRunner collectorTopicsBootstrap(CollectorKafkaProperties properties) {
        return args -> {
            if (!properties.isTopicBootstrapEnabled()) {
                return;
            }

            List<NewTopic> topics = List.of(
                    new NewTopic(properties.getTopics().getSensors(), Optional.empty(), Optional.empty()),
                    new NewTopic(properties.getTopics().getHubs(), Optional.empty(), Optional.empty())
            );
            createTopics(properties, properties.getTopicBootstrapTimeout().toMillis(), topics);
        };
    }

    static void applySslProperties(Map<String, Object> configuration, CollectorKafkaProperties properties) {
        CollectorKafkaProperties.Ssl ssl = properties.getSsl();
        if (!ssl.isEnabled()) {
            return;
        }

        configuration.put("security.protocol", ssl.getSecurityProtocol());
        configuration.put("ssl.protocol", SSL_PROTOCOL);
        configuration.put("ssl.engine.factory.class", SSL_ENGINE_FACTORY_CLASS);
        configuration.put("ssl.truststore.location", ssl.getTruststoreLocation());
        configuration.put("ssl.truststore.password", ssl.getTruststorePassword());
        configuration.put("ssl.keystore.location", ssl.getKeystoreLocation());
        configuration.put("ssl.keystore.password", ssl.getKeystorePassword());
        configuration.put("ssl.key.password", ssl.getKeyPassword());
    }

    private void createTopics(CollectorKafkaProperties properties, long timeoutMs, List<NewTopic> topics) throws Exception {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        applySslProperties(configuration, properties);
        try (AdminClient adminClient = AdminClient.create(configuration)) {
            var topicResults = adminClient.createTopics(topics).values();
            for (NewTopic topic : topics) {
                try {
                    topicResults.get(topic.name()).get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (ExecutionException exception) {
                    if (!(exception.getCause() instanceof TopicExistsException)) {
                        throw exception;
                    }
                }
            }
        }
        log.info("Ensured Kafka topics exist: {}", topics.stream().map(NewTopic::name).toList());
    }
}
