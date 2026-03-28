package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Component
public class SnapshotPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnapshotPublisher.class);

    private final Producer<String, SensorsSnapshotAvro> producer;
    private final AggregatorKafkaProperties properties;

    public SnapshotPublisher(Producer<String, SensorsSnapshotAvro> producer,
                             AggregatorKafkaProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    public PendingSnapshotPublish publish(SensorsSnapshotAvro snapshot) {
        String topic = properties.getTopics().getSnapshots();
        String key = snapshot.getHubId();

        try {
            Future<RecordMetadata> publishFuture = producer.send(new ProducerRecord<>(topic, key, snapshot));
            return new PendingSnapshotPublish(topic, key, snapshot.getVersion(), publishFuture);
        } catch (RuntimeException exception) {
            log.error("Snapshot publish failed before producer accepted the record. topic={}, key={}, version={}",
                    topic, key, snapshot.getVersion(), exception);
            throw new SnapshotPublishException(buildFailureMessage("Failed to publish snapshot to Kafka", topic, key, exception), exception);
        }
    }

    public void awaitPublications(List<PendingSnapshotPublish> pendingPublishes) {
        for (PendingSnapshotPublish pendingPublish : pendingPublishes) {
            awaitPublication(pendingPublish);
        }
    }

    public void flush() {
        producer.flush();
    }

    public void close() {
        producer.close();
    }

    private void awaitPublication(PendingSnapshotPublish pendingPublish) {
        try {
            pendingPublish.publishFuture().get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Snapshot publish interrupted. topic={}, key={}, version={}",
                    pendingPublish.topic(), pendingPublish.key(), pendingPublish.snapshotVersion(), exception);
            throw new SnapshotPublishException(
                    buildFailureMessage("Snapshot publish interrupted", pendingPublish.topic(), pendingPublish.key(), exception),
                    exception
            );
        } catch (ExecutionException exception) {
            log.error("Snapshot publish failed. topic={}, key={}, version={}",
                    pendingPublish.topic(), pendingPublish.key(), pendingPublish.snapshotVersion(), exception);
            throw new SnapshotPublishException(
                    buildFailureMessage("Failed to publish snapshot to Kafka", pendingPublish.topic(), pendingPublish.key(), exception),
                    exception
            );
        }
    }

    private String buildFailureMessage(String prefix, String topic, String key, Exception exception) {
        String causeMessage = resolveCauseMessage(exception);
        return "%s. topic=%s, key=%s, cause=%s".formatted(prefix, topic, key, causeMessage);
    }

    private String resolveCauseMessage(Exception exception) {
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        if (cause != null) {
            return cause.getClass().getSimpleName();
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    public record PendingSnapshotPublish(
            String topic,
            String key,
            long snapshotVersion,
            Future<RecordMetadata> publishFuture
    ) {
    }
}
