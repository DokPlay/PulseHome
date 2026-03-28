package ru.yandex.practicum.telemetry.analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;
import ru.yandex.practicum.telemetry.serialization.AvroBinarySerializer;

import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

@Component
public class HubEventDeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(HubEventDeadLetterPublisher.class);

    private final Producer<String, String> producer;
    private final AnalyzerKafkaProperties properties;
    public HubEventDeadLetterPublisher(@Qualifier("hubEventDlqProducer") Producer<String, String> producer,
                                       AnalyzerKafkaProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    public boolean publish(ConsumerRecord<String, HubEventAvro> record, Exception exception) {
        String topic = properties.getTopics().getHubsDlq();
        String key = record.key() != null ? record.key() : record.value().getHubId();

        try {
            String payload = serialize(record, exception);
            producer.send(new ProducerRecord<>(topic, key, payload)).get();
            return true;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            logPublishFailure(topic, key, interruptedException);
        } catch (ExecutionException executionException) {
            logPublishFailure(topic, key, executionException);
        } catch (RuntimeException runtimeException) {
            logPublishFailure(topic, key, runtimeException);
        }
        return false;
    }

    private String serialize(ConsumerRecord<String, HubEventAvro> record, Exception exception) {
        HubEventAvro event = record.value();
        HubEventDeadLetterMessage message = new HubEventDeadLetterMessage(
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                event.getHubId(),
                Instant.now().toString(),
                exception.getClass().getName(),
                resolveMessage(exception),
                Base64.getEncoder().encodeToString(AvroBinarySerializer.serialize(event))
        );
        try {
            return DeadLetterJsonSupport.writeValueAsString(message);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new HubEventDeadLetterPublishException("Failed to serialize hub event for DLQ", jsonProcessingException);
        }
    }

    private void logPublishFailure(String topic, String key, Exception exception) {
        log.error("Failed to publish hub event to DLQ. topic={}, key={}", topic, key, exception);
    }

    private String resolveMessage(Exception exception) {
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private record HubEventDeadLetterMessage(
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String sourceKey,
            String hubId,
            String failedAt,
            String errorType,
            String errorMessage,
            String payloadBase64
    ) {
    }
}
