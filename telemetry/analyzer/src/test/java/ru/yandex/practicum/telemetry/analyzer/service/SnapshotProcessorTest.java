package ru.yandex.practicum.telemetry.analyzer.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotProcessorTest {

    @Test
    void shouldProcessSnapshotsAndCommitOffsets() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorsSnapshotAvro> consumer = mock(Consumer.class);
        SnapshotAnalyzerService snapshotAnalyzerService = mock(SnapshotAnalyzerService.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();

        SensorsSnapshotAvro snapshot = SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(1)
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setSensorsState(Map.of(
                        "sensor.light.1", SensorStateAvro.newBuilder()
                                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                                .setData(LightSensorAvro.newBuilder()
                                        .setLinkQuality(90)
                                        .setLuminosity(20)
                                        .build())
                                .build()
                ))
                .build();

        TopicPartition partition = new TopicPartition("telemetry.snapshots.v1", 0);
        ConsumerRecords<String, SensorsSnapshotAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(new ConsumerRecord<>("telemetry.snapshots.v1", 0, 0L, "hub-1", snapshot))
        ));

        when(consumer.poll(properties.getSnapshotsConsumer().getPollTimeout())).thenReturn(records).thenThrow(new WakeupException());

        SnapshotProcessor processor = new SnapshotProcessor(consumer, properties, snapshotAnalyzerService);
        processor.start();

        verify(consumer).subscribe(List.of("telemetry.snapshots.v1"));
        verify(snapshotAnalyzerService).analyze(snapshot);
        verify(consumer, times(1)).commitSync(Map.of(partition, new OffsetAndMetadata(1L)));
        verify(consumer).close();
    }

    @Test
    void shouldCommitOnlySuccessfulOffsetsWhenRetryableDispatchFails() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorsSnapshotAvro> consumer = mock(Consumer.class);
        SnapshotAnalyzerService snapshotAnalyzerService = mock(SnapshotAnalyzerService.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();

        SensorsSnapshotAvro firstSnapshot = SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(1)
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setSensorsState(Map.of())
                .build();
        SensorsSnapshotAvro secondSnapshot = SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(2)
                .setTimestamp(Instant.parse("2024-08-06T15:12:24.157Z"))
                .setSensorsState(Map.of())
                .build();

        TopicPartition partition = new TopicPartition("telemetry.snapshots.v1", 0);
        ConsumerRecords<String, SensorsSnapshotAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(
                        new ConsumerRecord<>("telemetry.snapshots.v1", 0, 0L, "hub-1", firstSnapshot),
                        new ConsumerRecord<>("telemetry.snapshots.v1", 0, 1L, "hub-1", secondSnapshot)
                )
        ));

        when(consumer.poll(properties.getSnapshotsConsumer().getPollTimeout())).thenReturn(records).thenThrow(new WakeupException());
        org.mockito.Mockito.doThrow(new RetryableActionDispatchException("retryable", new RuntimeException()))
                .when(snapshotAnalyzerService).analyze(secondSnapshot);

        SnapshotProcessor processor = new SnapshotProcessor(consumer, properties, snapshotAnalyzerService);
        processor.start();

        verify(snapshotAnalyzerService).analyze(firstSnapshot);
        verify(snapshotAnalyzerService).analyze(secondSnapshot);
        verify(consumer).commitSync(Map.of(partition, new OffsetAndMetadata(1L)));
        verify(consumer, never()).commitSync(Map.of(partition, new OffsetAndMetadata(2L)));
    }
}
