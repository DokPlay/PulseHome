package ru.yandex.practicum.telemetry.collector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.config.CollectorKafkaProperties;
import ru.yandex.practicum.telemetry.collector.dto.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.mapper.HubEventAvroMapper;
import ru.yandex.practicum.telemetry.collector.mapper.SensorEventAvroMapper;
import ru.yandex.practicum.telemetry.serialization.AvroBinarySerializer;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class CollectorEventService {

    private static final Logger log = LoggerFactory.getLogger(CollectorEventService.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final CollectorKafkaProperties properties;
    private final SensorEventAvroMapper sensorEventAvroMapper;
    private final HubEventAvroMapper hubEventAvroMapper;

    public CollectorEventService(KafkaTemplate<String, byte[]> kafkaTemplate,
                                 CollectorKafkaProperties properties,
                                 SensorEventAvroMapper sensorEventAvroMapper,
                                 HubEventAvroMapper hubEventAvroMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.sensorEventAvroMapper = sensorEventAvroMapper;
        this.hubEventAvroMapper = hubEventAvroMapper;
    }

    public CompletableFuture<Void> collectSensorEvent(SensorEvent event) {
        SensorEventAvro avroEvent = sensorEventAvroMapper.toAvro(event);
        byte[] payload = AvroBinarySerializer.serialize(avroEvent);
        String topic = Objects.requireNonNull(properties.getTopics().getSensors(), "Sensor topic must not be null");
        String key = Objects.requireNonNull(event.getHubId(), "Sensor event hubId must not be null");
        return publish(topic, key, payload);
    }

    public CompletableFuture<Void> collectHubEvent(HubEvent event) {
        HubEventAvro avroEvent = hubEventAvroMapper.toAvro(event);
        byte[] payload = AvroBinarySerializer.serialize(avroEvent);
        String topic = Objects.requireNonNull(properties.getTopics().getHubs(), "Hub topic must not be null");
        String key = Objects.requireNonNull(event.getHubId(), "Hub event hubId must not be null");
        return publish(topic, key, payload);
    }

    private CompletableFuture<Void> publish(String topic, String key, byte[] payload) {
        String nonNullTopic = Objects.requireNonNull(topic, "Kafka topic must not be null");
        String nonNullKey = Objects.requireNonNull(key, "Kafka key must not be null");
        try {
            return kafkaTemplate.send(nonNullTopic, nonNullKey, payload)
                    .handle((result, throwable) -> {
                        if (throwable == null) {
                            return null;
                        }

                        Throwable publishFailure = unwrapPublishFailure(throwable);
                        log.error("Kafka publish failed. topic={}, key={}, payloadBytes={}",
                                nonNullTopic, nonNullKey, payload.length, publishFailure);
                        throw new EventPublishException(
                                buildFailureMessage("Failed to publish event to Kafka", nonNullTopic, nonNullKey, publishFailure),
                                publishFailure
                        );
                    });
        } catch (RuntimeException exception) {
            log.error("Kafka publish failed before acknowledgement wait. topic={}, key={}, payloadBytes={}",
                    nonNullTopic, nonNullKey, payload.length, exception);
            return CompletableFuture.failedFuture(new EventPublishException(
                    buildFailureMessage("Failed to publish event to Kafka", nonNullTopic, nonNullKey, exception),
                    exception
            ));
        }
    }

    private Throwable unwrapPublishFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    private String buildFailureMessage(String prefix, String topic, String key, Throwable exception) {
        String causeMessage = resolveCauseMessage(exception);
        return "%s. topic=%s, key=%s, cause=%s".formatted(prefix, topic, key, causeMessage);
    }

    private String resolveCauseMessage(Throwable exception) {
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
}
