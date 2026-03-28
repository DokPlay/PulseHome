package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AggregationStarter {

    private static final Logger log = LoggerFactory.getLogger(AggregationStarter.class);

    private final Consumer<String, SensorEventAvro> consumer;
    private final AggregatorKafkaProperties properties;
    private final SnapshotAggregationService aggregationService;
    private final SnapshotPublisher snapshotPublisher;
    private final SnapshotStateRestorer snapshotStateRestorer;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean consumerClosed = new AtomicBoolean(false);

    public AggregationStarter(Consumer<String, SensorEventAvro> consumer,
                              AggregatorKafkaProperties properties,
                              SnapshotAggregationService aggregationService,
                              SnapshotPublisher snapshotPublisher,
                              SnapshotStateRestorer snapshotStateRestorer) {
        this.consumer = consumer;
        this.properties = properties;
        this.aggregationService = aggregationService;
        this.snapshotPublisher = snapshotPublisher;
        this.snapshotStateRestorer = snapshotStateRestorer;
    }

    public void start() {
        Consumer<String, SensorEventAvro> managedConsumer = consumer;
        consumerClosed.set(false);
        try {
            snapshotStateRestorer.restorePublishedSnapshots();
            managedConsumer.subscribe(List.of(properties.getTopics().getSensors()));

            while (active.get()) {
                ConsumerRecords<String, SensorEventAvro> records = managedConsumer.poll(properties.getPollTimeout());
                if (records.isEmpty()) {
                    continue;
                }

                List<SnapshotPublisher.PendingSnapshotPublish> pendingPublishes = processRecords(records);
                if (!pendingPublishes.isEmpty()) {
                    snapshotPublisher.flush();
                    snapshotPublisher.awaitPublications(pendingPublishes);
                }
                managedConsumer.commitSync();
            }
        } catch (WakeupException ignored) {
            // Shutdown is expected to interrupt the poll loop through consumer.wakeup().
        } catch (Exception exception) {
            log.error("Error while aggregating sensor events", exception);
            throw new IllegalStateException("Fatal error while aggregating sensor events", exception);
        } finally {
            closeConsumer(managedConsumer);
            try {
                snapshotPublisher.flush();
            } catch (Exception exception) {
                log.warn("Failed to flush producer during shutdown", exception);
            } finally {
                log.info("Closing producer");
                snapshotPublisher.close();
            }
        }
    }

    public void stop() {
        active.set(false);
        if (consumerClosed.get()) {
            return;
        }
        try {
            consumer.wakeup();
        } catch (IllegalStateException exception) {
            log.debug("Aggregation consumer was already closed during shutdown", exception);
        }
    }

    private List<SnapshotPublisher.PendingSnapshotPublish> processRecords(ConsumerRecords<String, SensorEventAvro> records) {
        List<SnapshotPublisher.PendingSnapshotPublish> pendingPublishes = new ArrayList<>();
        for (ConsumerRecord<String, SensorEventAvro> record : records) {
            if (record.value() == null) {
                continue;
            }

            Optional<SensorsSnapshotAvro> updatedSnapshot = aggregationService.updateState(record.value());
            updatedSnapshot.ifPresent(snapshot -> pendingPublishes.add(snapshotPublisher.publish(snapshot)));
        }
        return pendingPublishes;
    }

    private void closeConsumer(Consumer<String, SensorEventAvro> managedConsumer) {
        if (consumerClosed.compareAndSet(false, true)) {
            log.info("Closing aggregation consumer");
            try (managedConsumer) {
                // Consumer is closed through try-with-resources to keep shutdown explicit and IDE-clean.
            }
        }
    }
}
