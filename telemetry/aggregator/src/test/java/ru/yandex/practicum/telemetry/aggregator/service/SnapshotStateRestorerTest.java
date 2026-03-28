package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotStateRestorerTest {

    @Test
    void shouldRestoreSnapshotsFromBeginningUntilEndOffsets() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorsSnapshotAvro> consumer = mock(Consumer.class);
        SnapshotAggregationService aggregationService = mock(SnapshotAggregationService.class);
        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        SnapshotStateRestorer restorer = new SnapshotStateRestorer(consumer, properties, aggregationService);

        TopicPartition partition = new TopicPartition("telemetry.snapshots.v1", 0);
        SensorsSnapshotAvro firstSnapshot = snapshot("hub-1", 3L);
        SensorsSnapshotAvro secondSnapshot = snapshot("hub-2", 5L);
        ConsumerRecords<String, SensorsSnapshotAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(
                        new ConsumerRecord<>("telemetry.snapshots.v1", 0, 0L, "hub-1", firstSnapshot),
                        new ConsumerRecord<>("telemetry.snapshots.v1", 0, 1L, "hub-2", secondSnapshot)
                )
        ));

        when(consumer.partitionsFor("telemetry.snapshots.v1")).thenReturn(List.of(new PartitionInfo("telemetry.snapshots.v1", 0, null, null, null)));
        when(consumer.endOffsets(List.of(partition))).thenReturn(Map.of(partition, 2L));
        when(consumer.position(partition)).thenReturn(0L, 2L);
        when(consumer.poll(properties.getPollTimeout())).thenReturn(records);

        restorer.restorePublishedSnapshots();

        verify(consumer).assign(List.of(partition));
        verify(consumer).seekToBeginning(List.of(partition));
        verify(aggregationService).restoreSnapshot(firstSnapshot);
        verify(aggregationService).restoreSnapshot(secondSnapshot);
        verify(consumer).close();
    }

    @Test
    void shouldSkipBootstrapWhenSnapshotsTopicHasNoPartitions() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorsSnapshotAvro> consumer = mock(Consumer.class);
        SnapshotAggregationService aggregationService = mock(SnapshotAggregationService.class);
        SnapshotStateRestorer restorer = new SnapshotStateRestorer(consumer, new AggregatorKafkaProperties(), aggregationService);

        when(consumer.partitionsFor("telemetry.snapshots.v1")).thenReturn(List.of());

        restorer.restorePublishedSnapshots();

        verify(aggregationService, never()).restoreSnapshot(org.mockito.ArgumentMatchers.any());
        verify(consumer).close();
    }

    @Test
    void shouldFailBootstrapWhenRestoreTimeoutIsExceeded() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorsSnapshotAvro> consumer = mock(Consumer.class);
        SnapshotAggregationService aggregationService = mock(SnapshotAggregationService.class);
        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        properties.setSnapshotRestoreTimeout(Duration.ofSeconds(2));
        MutableClock clock = new MutableClock(Instant.parse("2024-08-06T15:11:24.157Z"));
        SnapshotStateRestorer restorer = new SnapshotStateRestorer(consumer, properties, aggregationService, clock);

        TopicPartition partition = new TopicPartition("telemetry.snapshots.v1", 0);
        when(consumer.partitionsFor("telemetry.snapshots.v1"))
                .thenReturn(List.of(new PartitionInfo("telemetry.snapshots.v1", 0, null, null, null)));
        when(consumer.endOffsets(List.of(partition))).thenReturn(Map.of(partition, 1L));
        when(consumer.position(partition)).thenReturn(0L);
        when(consumer.poll(properties.getPollTimeout())).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(1));
            return ConsumerRecords.empty();
        });

        assertThatThrownBy(restorer::restorePublishedSnapshots)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out while restoring published snapshots");

        verify(consumer).close();
        verify(aggregationService, never()).restoreSnapshot(org.mockito.ArgumentMatchers.any());
    }

    private SensorsSnapshotAvro snapshot(String hubId, long version) {
        return SensorsSnapshotAvro.newBuilder()
                .setHubId(hubId)
                .setVersion(version)
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setSensorsState(Map.of(
                        "sensor.light.1", SensorStateAvro.newBuilder()
                                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                                .setData(LightSensorAvro.newBuilder()
                                        .setLinkQuality(77)
                                        .setLuminosity(320)
                                        .build())
                                .build()
                ))
                .build();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
