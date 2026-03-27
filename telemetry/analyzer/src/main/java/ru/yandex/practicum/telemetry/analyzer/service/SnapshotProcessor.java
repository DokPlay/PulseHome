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
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SnapshotProcessor {

    private static final Logger log = LoggerFactory.getLogger(SnapshotProcessor.class);

    private final Consumer<String, SensorsSnapshotAvro> consumer;
    private final AnalyzerKafkaProperties properties;
    private final SnapshotAnalyzerService snapshotAnalyzerService;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public SnapshotProcessor(@Qualifier("snapshotConsumer") Consumer<String, SensorsSnapshotAvro> consumer,
                             AnalyzerKafkaProperties properties,
                             SnapshotAnalyzerService snapshotAnalyzerService) {
        this.consumer = consumer;
        this.properties = properties;
        this.snapshotAnalyzerService = snapshotAnalyzerService;
    }

    public void start() {
        try {
            consumer.subscribe(List.of(properties.getTopics().getSnapshots()));

            while (active.get()) {
                ConsumerRecords<String, SensorsSnapshotAvro> records = consumer.poll(properties.getSnapshotsConsumer().getPollTimeout());
                if (records.isEmpty()) {
                    continue;
                }

                process(records);
            }
        } catch (WakeupException ignored) {
            // Shutdown uses wakeup to break the poll loop.
        } catch (Exception exception) {
            log.error("Error while processing snapshots", exception);
            throw new IllegalStateException("Snapshot processor stopped after a fatal error", exception);
        } finally {
            log.info("Closing snapshot consumer");
            consumer.close();
        }
    }

    public void stop() {
        active.set(false);
        consumer.wakeup();
    }

    private void process(ConsumerRecords<String, SensorsSnapshotAvro> records) {
        Map<TopicPartition, OffsetAndMetadata> processedOffsets = new HashMap<>();

        try {
            for (TopicPartition partition : records.partitions()) {
                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records.records(partition)) {
                    if (record.value() == null) {
                        trackRecord(processedOffsets, record);
                        continue;
                    }

                    try {
                        snapshotAnalyzerService.analyze(record.value());
                        trackRecord(processedOffsets, record);
                    } catch (RetryableActionDispatchException exception) {
                        log.warn("Retryable snapshot action dispatch failure. topic={}, partition={}, offset={}",
                                record.topic(), record.partition(), record.offset(), exception);
                        break;
                    }
                }
            }
        } finally {
            commitProcessedOffsets(processedOffsets);
        }
    }

    private void trackRecord(Map<TopicPartition, OffsetAndMetadata> processedOffsets,
                             ConsumerRecord<String, SensorsSnapshotAvro> record) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        processedOffsets.put(partition, new OffsetAndMetadata(record.offset() + 1));
    }

    private void commitProcessedOffsets(Map<TopicPartition, OffsetAndMetadata> processedOffsets) {
        if (!processedOffsets.isEmpty()) {
            consumer.commitSync(processedOffsets);
        }
    }
}
