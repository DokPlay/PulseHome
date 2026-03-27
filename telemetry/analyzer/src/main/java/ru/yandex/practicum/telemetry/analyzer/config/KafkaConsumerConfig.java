package ru.yandex.practicum.telemetry.analyzer.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.serialization.HubEventDeserializer;
import ru.yandex.practicum.telemetry.serialization.SensorsSnapshotDeserializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
public class KafkaConsumerConfig {

    @Bean(name = "hubEventConsumer")
    public Consumer<String, HubEventAvro> hubEventConsumer(AnalyzerKafkaProperties properties) {
        return new KafkaConsumer<>(
                baseConfiguration(properties, properties.getHubsConsumer()),
                new StringDeserializer(),
                new HubEventDeserializer()
        );
    }

    @Bean(name = "snapshotConsumer")
    public Consumer<String, SensorsSnapshotAvro> snapshotConsumer(AnalyzerKafkaProperties properties) {
        return new KafkaConsumer<>(
                baseConfiguration(properties, properties.getSnapshotsConsumer()),
                new StringDeserializer(),
                new SensorsSnapshotDeserializer()
        );
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
        return configuration;
    }
}
