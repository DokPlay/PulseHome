package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
    void shouldPublishSnapshotRecord() {
        Producer<String, SensorsSnapshotAvro> producer = mockProducer();
        SnapshotPublisher publisher = new SnapshotPublisher(producer, new AggregatorKafkaProperties());
        SensorsSnapshotAvro snapshot = snapshot();

        SnapshotPublisher.PendingSnapshotPublish pendingPublish = publisher.publish(snapshot);
        publisher.awaitPublications(List.of(pendingPublish));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, SensorsSnapshotAvro>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(recordCaptor.capture());

        ProducerRecord<String, SensorsSnapshotAvro> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("telemetry.snapshots.v1");
        assertThat(record.key()).isEqualTo("hub-1");
        assertThat(record.value().getHubId()).isEqualTo("hub-1");
        assertThat(record.value().getSensorsState()).containsKey("sensor.light.1");
    }

    @Test
    void shouldWrapRuntimeSendFailure() {
        @SuppressWarnings("unchecked")
        Producer<String, SensorsSnapshotAvro> producer = mock(Producer.class);
        when(producer.send(any())).thenThrow(new IllegalStateException("producer closed"));

        SnapshotPublisher publisher = new SnapshotPublisher(producer, new AggregatorKafkaProperties());

        assertThatThrownBy(() -> publisher.publish(snapshot()))
                .isInstanceOf(SnapshotPublishException.class)
                .hasMessageContaining("topic=telemetry.snapshots.v1")
                .hasMessageContaining("key=hub-1")
                .hasMessageContaining("cause=producer closed");
    }

    @Test
    void shouldWrapAsyncSendFailureWhenAwaitingPublication() {
        @SuppressWarnings("unchecked")
        Producer<String, SensorsSnapshotAvro> producer = mock(Producer.class);
        when(producer.send(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        SnapshotPublisher publisher = new SnapshotPublisher(producer, new AggregatorKafkaProperties());
        SnapshotPublisher.PendingSnapshotPublish pendingPublish = publisher.publish(snapshot());

        assertThatThrownBy(() -> publisher.awaitPublications(List.of(pendingPublish)))
                .isInstanceOf(SnapshotPublishException.class)
                .hasMessageContaining("topic=telemetry.snapshots.v1")
                .hasMessageContaining("key=hub-1")
                .hasMessageContaining("cause=broker unavailable");
    }

    @SuppressWarnings("unchecked")
    private Producer<String, SensorsSnapshotAvro> mockProducer() {
        Producer<String, SensorsSnapshotAvro> producer = mock(Producer.class);
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
