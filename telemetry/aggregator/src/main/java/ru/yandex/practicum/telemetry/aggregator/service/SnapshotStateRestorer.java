package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class SnapshotStateRestorer {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStateRestorer.class);

    private final Consumer<String, SensorsSnapshotAvro> snapshotConsumer;
    private final AggregatorKafkaProperties properties;
    private final SnapshotAggregationService aggregationService;
    private final Clock clock;

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
                if (!clock.instant().isBefore(deadline)) {
                    throw restoreTimeout(snapshotsTopic, restoredSnapshots);
                }
                ConsumerRecords<String, SensorsSnapshotAvro> records = managedConsumer.poll(properties.getPollTimeout());
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
        }
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
