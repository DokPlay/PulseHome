package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;
import ru.yandex.practicum.telemetry.serialization.SensorsSnapshotDeserializer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotPublisherTest {

    @Test
    void shouldPublishSnapshotAsAvroBinary() {
        Producer<String, byte[]> producer = mockProducer();
        SnapshotPublisher publisher = new SnapshotPublisher(producer, new AggregatorKafkaProperties());
        SensorsSnapshotAvro snapshot = snapshot();

        publisher.publish(snapshot);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(recordCaptor.capture());

        ProducerRecord<String, byte[]> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("telemetry.snapshots.v1");
        assertThat(record.key()).isEqualTo("hub-1");

        SensorsSnapshotAvro decoded = new SensorsSnapshotDeserializer().deserialize(record.topic(), record.value());
        assertThat(decoded.getHubId()).isEqualTo("hub-1");
        assertThat(decoded.getSensorsState()).containsKey("sensor.light.1");
    }

    @Test
    void shouldWrapRuntimeSendFailure() {
        @SuppressWarnings("unchecked")
        Producer<String, byte[]> producer = mock(Producer.class);
        when(producer.send(any())).thenThrow(new IllegalStateException("producer closed"));

        SnapshotPublisher publisher = new SnapshotPublisher(producer, new AggregatorKafkaProperties());

        assertThatThrownBy(() -> publisher.publish(snapshot()))
                .isInstanceOf(SnapshotPublishException.class)
                .hasMessageContaining("topic=telemetry.snapshots.v1")
                .hasMessageContaining("key=hub-1")
                .hasMessageContaining("cause=producer closed");
    }

    @SuppressWarnings("unchecked")
    private Producer<String, byte[]> mockProducer() {
        Producer<String, byte[]> producer = mock(Producer.class);
        when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        return producer;
    }

    private SensorsSnapshotAvro snapshot() {
        Map<String, SensorStateAvro> stateBySensorId = new HashMap<>();
        stateBySensorId.put("sensor.light.1", SensorStateAvro.newBuilder()
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setData(LightSensorAvro.newBuilder()
                        .setLinkQuality(78)
                        .setLuminosity(40)
                        .build())
                .build());

        return SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(1)
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setSensorsState(stateBySensorId)
                .build();
    }
}
