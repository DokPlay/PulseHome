package ru.yandex.practicum.telemetry.collector.service;

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
            kafkaTemplate.send(topic, key, payload)
                    .get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Kafka publish interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new EventPublishException("Failed to publish event to Kafka", exception);
        }
    }
}
