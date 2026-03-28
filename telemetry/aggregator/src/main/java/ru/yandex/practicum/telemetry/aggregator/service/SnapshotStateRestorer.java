package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.util.List;
import java.util.Map;

@Component
public class SnapshotStateRestorer {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStateRestorer.class);

    private final Consumer<String, SensorsSnapshotAvro> snapshotConsumer;
    private final AggregatorKafkaProperties properties;
    private final SnapshotAggregationService aggregationService;

    public SnapshotStateRestorer(Consumer<String, SensorsSnapshotAvro> snapshotConsumer,
                                 AggregatorKafkaProperties properties,
                                 SnapshotAggregationService aggregationService) {
        this.snapshotConsumer = snapshotConsumer;
        this.properties = properties;
        this.aggregationService = aggregationService;
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
            while (!isCaughtUp(managedConsumer, endOffsets)) {
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

    private boolean isCaughtUp(Consumer<String, SensorsSnapshotAvro> consumer,
                               Map<TopicPartition, Long> endOffsets) {
        return endOffsets.entrySet().stream()
                .allMatch(entry -> consumer.position(entry.getKey()) >= entry.getValue());
    }
}
