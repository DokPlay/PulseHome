package ru.yandex.practicum.telemetry.aggregator.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.serialization.AvroMessageSerializer;
import ru.yandex.practicum.telemetry.serialization.SensorEventDeserializer;
import ru.yandex.practicum.telemetry.serialization.SensorsSnapshotDeserializer;

@Configuration
public class KafkaClientConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaClientConfig.class);

    /**
     * Appends PQC / TLS SSL properties to a Kafka client config map when
     * {@code aggregator.kafka.ssl.security-protocol} is set to {@code SSL}.
     */
    private static void applySslProperties(Map<String, Object> config, AggregatorKafkaProperties properties) {
        AggregatorKafkaProperties.Ssl ssl = properties.getSsl();
        if (!ssl.isEnabled()) {
            return;
        }
        config.put("security.protocol", ssl.getSecurityProtocol());
        config.put("ssl.protocol", "TLSv1.3");
        config.put("ssl.engine.factory.class",
                "ru.yandex.practicum.telemetry.aggregator.config.pqc.HybridPqcSslEngineFactory");
        config.put("ssl.pqc.require", String.valueOf(ssl.isPqcRequire()));
        config.put("ssl.truststore.location", ssl.getTruststoreLocation());
        config.put("ssl.truststore.password", ssl.getTruststorePassword());
        config.put("ssl.keystore.location", ssl.getKeystoreLocation());
        config.put("ssl.keystore.password", ssl.getKeystorePassword());
        config.put("ssl.key.password", ssl.getKeyPassword());
    }

    @Bean(destroyMethod = "")
    public Consumer<String, SensorEventAvro> sensorEventConsumer(AggregatorKafkaProperties properties) {
        AggregatorKafkaProperties.Consumer consumer = properties.getConsumer();

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configuration.put(ConsumerConfig.GROUP_ID_CONFIG, consumer.getGroupId());
        configuration.put(ConsumerConfig.CLIENT_ID_CONFIG, consumer.getClientId());
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SensorEventDeserializer.class);
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumer.getAutoOffsetReset());
        configuration.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getMaxPollRecords());
        applySslProperties(configuration, properties);
        return new KafkaConsumer<>(configuration);
    }

    @Bean(destroyMethod = "")
    public Consumer<String, SensorsSnapshotAvro> snapshotStateConsumer(AggregatorKafkaProperties properties) {
        AggregatorKafkaProperties.Consumer consumer = properties.getConsumer();

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configuration.put(ConsumerConfig.CLIENT_ID_CONFIG, consumer.getClientId() + "-snapshot-bootstrap-" + UUID.randomUUID());
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SensorsSnapshotDeserializer.class);
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configuration.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getMaxPollRecords());
        applySslProperties(configuration, properties);
        return new KafkaConsumer<>(configuration);
    }

    @Bean(destroyMethod = "")
    public Producer<String, SensorsSnapshotAvro> snapshotProducer(AggregatorKafkaProperties properties) {
        int sendTimeoutMs = Math.toIntExact(properties.getSendTimeout().toMillis());
        AggregatorKafkaProperties.Producer producer = properties.getProducer();
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
        return new KafkaProducer<>(configuration);
    }

    @Bean
    public ApplicationRunner aggregatorTopicsBootstrap(AggregatorKafkaProperties properties) {
        return args -> {
            if (!properties.isTopicBootstrapEnabled()) {
                return;
            }

            List<NewTopic> topics = List.of(
                    new NewTopic(properties.getTopics().getSensors(), Optional.empty(), Optional.empty()),
                    new NewTopic(properties.getTopics().getSnapshots(), Optional.empty(), Optional.empty())
            );
            createTopics(properties, properties.getTopicBootstrapTimeout().toMillis(), topics);
        };
    }

    private void createTopics(AggregatorKafkaProperties properties, long timeoutMs, List<NewTopic> topics) throws Exception {
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
