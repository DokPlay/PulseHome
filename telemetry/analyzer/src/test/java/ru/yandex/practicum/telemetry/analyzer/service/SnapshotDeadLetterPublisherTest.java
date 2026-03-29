package ru.yandex.practicum.telemetry.analyzer.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotDeadLetterPublisherTest {

    @Test
    void shouldPublishDlqMessageWithHubFallbackKey() throws Exception {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();
        SnapshotDeadLetterPublisher publisher = new SnapshotDeadLetterPublisher(producer, properties);

        SensorsSnapshotAvro snapshot = snapshot();
        ConsumerRecord<String, SensorsSnapshotAvro> record =
                new ConsumerRecord<>("telemetry.snapshots.v1", 1, 42L, null, snapshot);
        CompletableFuture<RecordMetadata> sendResult = CompletableFuture.completedFuture(mock(RecordMetadata.class));

        @SuppressWarnings("unchecked")
        Class<ProducerRecord<String, String>> producerRecordClass =
                (Class<ProducerRecord<String, String>>) (Class<?>) ProducerRecord.class;
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(producerRecordClass);
        when(producer.send(recordCaptor.capture())).thenAnswer(invocation -> sendResult);

        boolean published = publisher.publish(record, new IllegalStateException("invalid snapshot"));

        verify(producer).send(recordCaptor.getValue());
        ProducerRecord<String, String> publishedRecord = recordCaptor.getValue();
        assertThat(published).isTrue();
        assertThat(publishedRecord.topic()).isEqualTo("telemetry.snapshots.dlq.v1");
        assertThat(publishedRecord.key()).isEqualTo("hub-1");
        assertThat(publishedRecord.value()).contains("\"sourceTopic\":\"telemetry.snapshots.v1\"");
        assertThat(publishedRecord.value()).contains("\"sourceOffset\":42");
        assertThat(publishedRecord.value()).contains("\"snapshotVersion\":9");
        assertThat(publishedRecord.value()).contains("\"errorType\":\"java.lang.IllegalStateException\"");
        assertThat(publishedRecord.value()).contains("\"errorMessage\":\"invalid snapshot\"");
    }

    @Test
    void shouldReturnFalseWhenDlqBrokerIsUnavailable() throws Exception {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();
        SnapshotDeadLetterPublisher publisher = new SnapshotDeadLetterPublisher(producer, properties);

        CompletableFuture<RecordMetadata> failedResult = new CompletableFuture<>();
        failedResult.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(producer.send(any())).thenAnswer(invocation -> failedResult);

        boolean published = publisher.publish(
                new ConsumerRecord<>("telemetry.snapshots.v1", 1, 42L, null, snapshot()),
                new IllegalStateException("invalid snapshot")
        );

        assertThat(published).isFalse();
        verify(producer).send(any());
    }

    private SensorsSnapshotAvro snapshot() {
        Instant timestamp = Instant.parse("2024-08-06T15:11:24.157Z");
        return SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(9)
                .setTimestamp(timestamp)
                .setSensorsState(Map.of(
                        "sensor.light.1", SensorStateAvro.newBuilder()
                                .setTimestamp(timestamp)
                                .setData(LightSensorAvro.newBuilder()
                                        .setLinkQuality(77)
                                        .setLuminosity(320)
                                        .build())
                                .build()
                ))
                .build();
    }
}
