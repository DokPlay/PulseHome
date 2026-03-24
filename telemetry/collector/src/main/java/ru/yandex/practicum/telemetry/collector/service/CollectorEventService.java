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
            // Producer-level max.block.ms and delivery.timeout.ms already bound the wait time,
            // so we do not add a second client-side timeout that could race with Kafka delivery.
            kafkaTemplate.send(topic, key, payload)
                    .get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Kafka publish interrupted. topic={}, key={}, payloadBytes={}", topic, key, payload.length, exception);
            throw new EventPublishException(buildFailureMessage("Kafka publish interrupted", topic, key, exception), exception);
        } catch (ExecutionException exception) {
            log.error("Kafka publish failed. topic={}, key={}, payloadBytes={}", topic, key, payload.length, exception);
            throw new EventPublishException(buildFailureMessage("Failed to publish event to Kafka", topic, key, exception), exception);
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
