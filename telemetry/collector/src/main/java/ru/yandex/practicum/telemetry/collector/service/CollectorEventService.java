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
import java.util.concurrent.ExecutionException;

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

    public void collectSensorEvent(SensorEvent event) {
        SensorEventAvro avroEvent = sensorEventAvroMapper.toAvro(event);
        byte[] payload = AvroBinarySerializer.serialize(avroEvent);
        String topic = Objects.requireNonNull(properties.getTopics().getSensors(), "Sensor topic must not be null");
        String key = Objects.requireNonNull(event.getHubId(), "Sensor event hubId must not be null");
        publish(topic, key, payload);
    }

    public void collectHubEvent(HubEvent event) {
        HubEventAvro avroEvent = hubEventAvroMapper.toAvro(event);
        byte[] payload = AvroBinarySerializer.serialize(avroEvent);
        String topic = Objects.requireNonNull(properties.getTopics().getHubs(), "Hub topic must not be null");
        String key = Objects.requireNonNull(event.getHubId(), "Hub event hubId must not be null");
        publish(topic, key, payload);
    }

    private void publish(String topic, String key, byte[] payload) {
        String nonNullTopic = Objects.requireNonNull(topic, "Kafka topic must not be null");
        String nonNullKey = Objects.requireNonNull(key, "Kafka key must not be null");
        try {
            // Collector confirms the HTTP request only after Kafka acknowledges the write.
            // Producer-level max.block.ms and delivery.timeout.ms already bound the wait time,
            // so we do not add a second client-side timeout that could race with Kafka delivery.
            kafkaTemplate.send(nonNullTopic, nonNullKey, payload)
                    .get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Kafka publish interrupted. topic={}, key={}, payloadBytes={}",
                    nonNullTopic, nonNullKey, payload.length, exception);
            throw new EventPublishException(
                    buildFailureMessage("Kafka publish interrupted", nonNullTopic, nonNullKey, exception),
                    exception
            );
        } catch (ExecutionException exception) {
            log.error("Kafka publish failed. topic={}, key={}, payloadBytes={}",
                    nonNullTopic, nonNullKey, payload.length, exception);
            throw new EventPublishException(
                    buildFailureMessage("Failed to publish event to Kafka", nonNullTopic, nonNullKey, exception),
                    exception
            );
        } catch (RuntimeException exception) {
            log.error("Kafka publish failed before acknowledgement wait. topic={}, key={}, payloadBytes={}",
                    nonNullTopic, nonNullKey, payload.length, exception);
            throw new EventPublishException(
                    buildFailureMessage("Failed to publish event to Kafka", nonNullTopic, nonNullKey, exception),
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
}
