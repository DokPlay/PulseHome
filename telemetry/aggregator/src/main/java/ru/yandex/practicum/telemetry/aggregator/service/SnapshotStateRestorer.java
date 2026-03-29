package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.common.errors.WakeupException;

@Component
public class SnapshotStateRestorer {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStateRestorer.class);

    private final Consumer<String, SensorsSnapshotAvro> snapshotConsumer;
    private final AggregatorKafkaProperties properties;
    private final SnapshotAggregationService aggregationService;
    private final Clock clock;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean consumerClosed = new AtomicBoolean(false);

    @Autowired
    public SnapshotStateRestorer(Consumer<String, SensorsSnapshotAvro> snapshotConsumer,
                                 AggregatorKafkaProperties properties,
                                 SnapshotAggregationService aggregationService) {
        this(snapshotConsumer, properties, aggregationService, Clock.systemUTC());
    }

    SnapshotStateRestorer(Consumer<String, SensorsSnapshotAvro> snapshotConsumer,
                          AggregatorKafkaProperties properties,
                          SnapshotAggregationService aggregationService,
                          Clock clock) {
        this.snapshotConsumer = snapshotConsumer;
        this.properties = properties;
        this.aggregationService = aggregationService;
        this.clock = clock;
    }

    public int restorePublishedSnapshots() {
        String snapshotsTopic = properties.getTopics().getSnapshots();
        cancelled.set(false);
        consumerClosed.set(false);
        try (Consumer<String, SensorsSnapshotAvro> managedConsumer = snapshotConsumer) {
            List<TopicPartition> partitions = managedConsumer.partitionsFor(snapshotsTopic).stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                    .toList();
            if (partitions.isEmpty()) {
                log.info("Skipping snapshot bootstrap because topic has no partitions. topic={}", snapshotsTopic);
                return 0;
            }

            managedConsumer.assign(partitions);
            managedConsumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> endOffsets = managedConsumer.endOffsets(partitions);
            if (endOffsets.values().stream().allMatch(offset -> offset == 0L)) {
                log.info("Skipping snapshot bootstrap because topic is empty. topic={}", snapshotsTopic);
                return 0;
            }

            int restoredSnapshots = 0;
            Instant deadline = clock.instant().plus(properties.getSnapshotRestoreTimeout());
            while (!isCaughtUp(managedConsumer, endOffsets)) {
                if (cancelled.get()) {
                    log.info("Snapshot bootstrap was cancelled before catch-up completed. topic={}, restoredSnapshots={}",
                            snapshotsTopic, restoredSnapshots);
                    return restoredSnapshots;
                }
                if (!clock.instant().isBefore(deadline)) {
                    throw restoreTimeout(snapshotsTopic, restoredSnapshots);
                }
                ConsumerRecords<String, SensorsSnapshotAvro> records;
                try {
                    records = managedConsumer.poll(properties.getPollTimeout());
                } catch (RecordDeserializationException exception) {
                    skipPoisonedRecord(managedConsumer, exception);
                    continue;
                } catch (WakeupException exception) {
                    if (cancelled.get()) {
                        log.info("Snapshot bootstrap interrupted by shutdown. topic={}, restoredSnapshots={}",
                                snapshotsTopic, restoredSnapshots);
                        return restoredSnapshots;
                    }
                    throw exception;
                }
                for (TopicPartition partition : records.partitions()) {
                    for (var record : records.records(partition)) {
                        if (record.value() == null) {
                            continue;
                        }
                        aggregationService.restoreSnapshot(record.value());
                        restoredSnapshots++;
                    }
                }
            }

            log.info("Restored published hub snapshots before sensor replay. topic={}, restoredSnapshots={}",
                    snapshotsTopic, restoredSnapshots);
            return restoredSnapshots;
        } finally {
            consumerClosed.set(true);
        }
    }

    public void cancelRestore() {
        cancelled.set(true);
        if (consumerClosed.get()) {
            return;
        }
        try {
            snapshotConsumer.wakeup();
        } catch (IllegalStateException exception) {
            log.debug("Snapshot restore consumer was already closed during shutdown", exception);
        }
    }

    private void skipPoisonedRecord(Consumer<String, SensorsSnapshotAvro> consumer,
                                    RecordDeserializationException exception) {
        TopicPartition topicPartition = exception.topicPartition();
        long nextOffset = exception.offset() + 1;
        log.error(
                "Skipping poisoned snapshot during bootstrap restore. topic={}, partition={}, offset={}",
                topicPartition.topic(),
                topicPartition.partition(),
                exception.offset(),
                exception
        );
        consumer.seek(topicPartition, nextOffset);
        consumer.commitSync(Map.of(topicPartition, new OffsetAndMetadata(nextOffset)));
    }

    private IllegalStateException restoreTimeout(String topic, int restoredSnapshots) {
        String message = "Timed out while restoring published snapshots before sensor replay. topic=%s, timeout=%s, restoredSnapshots=%d"
                .formatted(topic, properties.getSnapshotRestoreTimeout(), restoredSnapshots);
        log.error(message);
        return new IllegalStateException(message);
    }

    private boolean isCaughtUp(Consumer<String, SensorsSnapshotAvro> consumer,
                               Map<TopicPartition, Long> endOffsets) {
        return endOffsets.entrySet().stream()
                .allMatch(entry -> consumer.position(entry.getKey()) >= entry.getValue());
    }
}
