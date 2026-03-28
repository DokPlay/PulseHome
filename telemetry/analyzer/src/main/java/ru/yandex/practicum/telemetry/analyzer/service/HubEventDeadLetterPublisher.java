package ru.yandex.practicum.telemetry.analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HubEventDeadLetterPublisher(@Qualifier("hubEventDlqProducer") Producer<String, String> producer,
                                       AnalyzerKafkaProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    public void publish(ConsumerRecord<String, HubEventAvro> record, Exception exception) {
        String topic = properties.getTopics().getHubsDlq();
        String key = record.key() != null ? record.key() : record.value().getHubId();
        String payload = serialize(record, exception);

        try {
            producer.send(new ProducerRecord<>(topic, key, payload)).get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw publishFailure(topic, key, interruptedException);
        } catch (ExecutionException executionException) {
            throw publishFailure(topic, key, executionException);
        } catch (RuntimeException runtimeException) {
            throw publishFailure(topic, key, runtimeException);
        }
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
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new HubEventDeadLetterPublishException("Failed to serialize hub event for DLQ", jsonProcessingException);
        }
    }

    private HubEventDeadLetterPublishException publishFailure(String topic, String key, Exception exception) {
        log.error("Failed to publish hub event to DLQ. topic={}, key={}", topic, key, exception);
        return new HubEventDeadLetterPublishException(
                "Failed to publish hub event to DLQ. topic=%s, key=%s, cause=%s"
                        .formatted(topic, key, resolveMessage(exception)),
                exception
        );
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
