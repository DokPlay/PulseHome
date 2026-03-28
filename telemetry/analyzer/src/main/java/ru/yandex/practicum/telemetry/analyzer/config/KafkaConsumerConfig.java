package ru.yandex.practicum.telemetry.analyzer.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.serialization.HubEventDeserializer;
import ru.yandex.practicum.telemetry.serialization.SensorsSnapshotDeserializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean(name = "hubEventConsumer", destroyMethod = "")
    public Consumer<String, HubEventAvro> hubEventConsumer(AnalyzerKafkaProperties properties) {
        return new KafkaConsumer<>(
                baseConfiguration(properties, properties.getHubsConsumer()),
                new StringDeserializer(),
                new HubEventDeserializer()
        );
    }

    @Bean(name = "snapshotConsumer", destroyMethod = "")
    public Consumer<String, SensorsSnapshotAvro> snapshotConsumer(AnalyzerKafkaProperties properties) {
        return new KafkaConsumer<>(
                baseConfiguration(properties, properties.getSnapshotsConsumer()),
                new StringDeserializer(),
                new SensorsSnapshotDeserializer()
        );
    }

    @Bean(name = "hubEventDlqProducer")
    public Producer<String, String> hubEventDlqProducer(AnalyzerKafkaProperties properties) {
        return dlqProducer(properties, properties.getHubsConsumer().getClientIdPrefix());
    }

    @Bean(name = "snapshotDlqProducer")
    public Producer<String, String> snapshotDlqProducer(AnalyzerKafkaProperties properties) {
        return dlqProducer(properties, properties.getSnapshotsConsumer().getClientIdPrefix());
    }

    private Producer<String, String> dlqProducer(AnalyzerKafkaProperties properties, String clientIdPrefix) {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configuration.put(ProducerConfig.CLIENT_ID_CONFIG, clientIdPrefix + "-dlq-" + UUID.randomUUID());
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(configuration);
    }

    private Map<String, Object> baseConfiguration(AnalyzerKafkaProperties properties,
                                                  AnalyzerKafkaProperties.ConsumerSettings consumerSettings) {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configuration.put(ConsumerConfig.GROUP_ID_CONFIG, consumerSettings.getGroupId());
        configuration.put(ConsumerConfig.CLIENT_ID_CONFIG, consumerSettings.getClientIdPrefix() + "-" + UUID.randomUUID());
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerSettings.getAutoOffsetReset());
        configuration.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerSettings.getMaxPollRecords());
        configuration.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, consumerSettings.getMaxPollIntervalMs());
        return configuration;
    }

    @Bean
    public ApplicationRunner analyzerTopicsBootstrap(AnalyzerKafkaProperties properties) {
        return args -> {
            if (!properties.isTopicBootstrapEnabled()) {
                return;
            }

            List<NewTopic> topics = List.of(
                    new NewTopic(properties.getTopics().getHubs(), Optional.empty(), Optional.empty()),
                    new NewTopic(properties.getTopics().getSnapshots(), Optional.empty(), Optional.empty()),
                    new NewTopic(properties.getTopics().getHubsDlq(), Optional.empty(), Optional.empty()),
                    new NewTopic(properties.getTopics().getSnapshotsDlq(), Optional.empty(), Optional.empty())
            );
            createTopics(properties.getBootstrapServers(), properties.getTopicBootstrapTimeout().toMillis(), topics);
        };
    }

    private void createTopics(String bootstrapServers, long timeoutMs, List<NewTopic> topics) throws Exception {
        Map<String, Object> configuration = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
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
