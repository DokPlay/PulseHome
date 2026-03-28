package ru.yandex.practicum.telemetry.analyzer.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HubEventProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(HubEventProcessor.class);

    private final Consumer<String, HubEventAvro> consumer;
    private final AnalyzerKafkaProperties properties;
    private final HubConfigurationService hubConfigurationService;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public HubEventProcessor(@Qualifier("hubEventConsumer") Consumer<String, HubEventAvro> consumer,
                             AnalyzerKafkaProperties properties,
                             HubConfigurationService hubConfigurationService) {
        this.consumer = consumer;
        this.properties = properties;
        this.hubConfigurationService = hubConfigurationService;
    }

    @Override
    public void run() {
        try {
            consumer.subscribe(List.of(properties.getTopics().getHubs()));

            while (active.get()) {
                ConsumerRecords<String, HubEventAvro> records = consumer.poll(properties.getHubsConsumer().getPollTimeout());
                if (records.isEmpty()) {
                    continue;
                }

                process(records);
            }
        } catch (WakeupException ignored) {
            // Shutdown uses wakeup to break the poll loop.
        } catch (Exception exception) {
            log.error("Error while processing hub events", exception);
            throw new IllegalStateException("Hub event processor stopped after a fatal error", exception);
        } finally {
            log.info("Closing hub event consumer");
            consumer.close();
        }
    }

    public void stop() {
        active.set(false);
        consumer.wakeup();
    }

    private void process(ConsumerRecords<String, HubEventAvro> records) {
        Map<TopicPartition, OffsetAndMetadata> processedOffsets = new HashMap<>();

        try {
            for (TopicPartition partition : records.partitions()) {
                for (ConsumerRecord<String, HubEventAvro> record : records.records(partition)) {
                    if (record.value() == null) {
                        trackRecord(processedOffsets, record);
                        continue;
                    }
                    try {
                        hubConfigurationService.handleHubEvent(record.value());
                    } catch (Exception exception) {
                        log.error("Skipping hub event after processing failure. topic={}, partition={}, offset={}, key={}",
                                record.topic(), record.partition(), record.offset(), record.key(), exception);
                    }
                    trackRecord(processedOffsets, record);
                }
            }
        } finally {
            commitProcessedOffsets(processedOffsets);
        }
    }

    private void trackRecord(Map<TopicPartition, OffsetAndMetadata> processedOffsets,
                             ConsumerRecord<String, HubEventAvro> record) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        processedOffsets.put(partition, new OffsetAndMetadata(record.offset() + 1));
    }

    private void commitProcessedOffsets(Map<TopicPartition, OffsetAndMetadata> processedOffsets) {
        if (!processedOffsets.isEmpty()) {
            consumer.commitSync(processedOffsets);
        }
    }
}
