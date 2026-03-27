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
    private final AtomicBoolean active = new AtomicBoolean(true);

    public AggregationStarter(Consumer<String, SensorEventAvro> consumer,
                              AggregatorKafkaProperties properties,
                              SnapshotAggregationService aggregationService,
                              SnapshotPublisher snapshotPublisher) {
        this.consumer = consumer;
        this.properties = properties;
        this.aggregationService = aggregationService;
        this.snapshotPublisher = snapshotPublisher;
    }

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "aggregator-shutdown"));

        try {
            consumer.subscribe(List.of(properties.getTopics().getSensors()));

            while (active.get()) {
                ConsumerRecords<String, SensorEventAvro> records = consumer.poll(properties.getPollTimeout());
                if (records.isEmpty()) {
                    continue;
                }

                processRecords(records);
                snapshotPublisher.flush();
                consumer.commitSync();
            }
        } catch (WakeupException ignored) {
            // Shutdown is expected to interrupt the poll loop through consumer.wakeup().
        } catch (Exception exception) {
            log.error("Error while aggregating sensor events", exception);
        } finally {
            try {
                snapshotPublisher.flush();
                consumer.commitSync();
            } catch (Exception exception) {
                log.warn("Failed to flush producer or commit offsets during shutdown", exception);
            } finally {
                log.info("Closing consumer");
                consumer.close();
                log.info("Closing producer");
                snapshotPublisher.close();
            }
        }
    }

    public void stop() {
        active.set(false);
        consumer.wakeup();
    }

    private void processRecords(ConsumerRecords<String, SensorEventAvro> records) {
        for (ConsumerRecord<String, SensorEventAvro> record : records) {
            if (record.value() == null) {
                continue;
            }

            Optional<SensorsSnapshotAvro> snapshot = aggregationService.updateState(record.value());
            snapshot.ifPresent(snapshotPublisher::publish);
        }
    }
}
