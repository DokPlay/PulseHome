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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class CollectorEventService {

    private static final Logger log = LoggerFactory.getLogger(CollectorEventService.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final CollectorKafkaProperties properties;
    private final SensorEventAvroMapper sensorEventAvroMapper;
    private final HubEventAvroMapper hubEventAvroMapper;
    private final AvroBinarySerializer avroBinarySerializer;

    public CollectorEventService(KafkaTemplate<String, byte[]> kafkaTemplate,
                                 CollectorKafkaProperties properties,
                                 SensorEventAvroMapper sensorEventAvroMapper,
                                 HubEventAvroMapper hubEventAvroMapper,
                                 AvroBinarySerializer avroBinarySerializer) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.sensorEventAvroMapper = sensorEventAvroMapper;
        this.hubEventAvroMapper = hubEventAvroMapper;
        this.avroBinarySerializer = avroBinarySerializer;
    }

    public void collectSensorEvent(SensorEvent event) {
        SensorEventAvro avroEvent = sensorEventAvroMapper.toAvro(event);
        byte[] payload = avroBinarySerializer.serialize(avroEvent);
        publish(properties.getTopics().getSensors(), event.getHubId(), payload);
    }

    public void collectHubEvent(HubEvent event) {
        HubEventAvro avroEvent = hubEventAvroMapper.toAvro(event);
        byte[] payload = avroBinarySerializer.serialize(avroEvent);
        publish(properties.getTopics().getHubs(), event.getHubId(), payload);
    }

    private void publish(String topic, String key, byte[] payload) {
        try {
            // Collector confirms the HTTP request only after Kafka acknowledges the write.
            // This keeps ingestion deterministic for Hub Router checks in Sprint 19.
            kafkaTemplate.send(topic, key, payload)
                    .get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Kafka publish interrupted. topic={}, key={}, payloadBytes={}", topic, key, payload.length, exception);
            throw new EventPublishException(buildFailureMessage("Kafka publish interrupted", topic, key, exception), exception);
        } catch (ExecutionException | TimeoutException exception) {
            log.error("Kafka publish failed. topic={}, key={}, payloadBytes={}", topic, key, payload.length, exception);
            throw new EventPublishException(buildFailureMessage("Failed to publish event to Kafka", topic, key, exception), exception);
        }
    }

    private String buildFailureMessage(String prefix, String topic, String key, Exception exception) {
        String causeMessage = exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage();
        return "%s. topic=%s, key=%s, cause=%s".formatted(prefix, topic, key, causeMessage);
    }
}
