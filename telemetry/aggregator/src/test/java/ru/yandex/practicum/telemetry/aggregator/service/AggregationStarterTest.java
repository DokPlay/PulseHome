package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AggregationStarterTest {

    @Test
    void shouldProcessRecordsFlushAndCommitOffsets() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorEventAvro> consumer = mock(Consumer.class);
        SnapshotAggregationService aggregationService = mock(SnapshotAggregationService.class);
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        SnapshotStateRestorer snapshotStateRestorer = mock(SnapshotStateRestorer.class);

        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        SensorEventAvro event = sensorEvent();
        SensorsSnapshotAvro snapshot = SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(1)
                .setTimestamp(event.getTimestamp())
                .setSensorsState(Map.of())
                .build();

        TopicPartition partition = new TopicPartition("telemetry.sensors.v1", 0);
        ConsumerRecords<String, SensorEventAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(new ConsumerRecord<>("telemetry.sensors.v1", 0, 0L, "hub-1", event))
        ));

        when(consumer.poll(properties.getPollTimeout())).thenReturn(records).thenThrow(new WakeupException());
        when(aggregationService.updateState(event)).thenReturn(Optional.of(snapshot));
        SnapshotPublisher.PendingSnapshotPublish pendingPublish = new SnapshotPublisher.PendingSnapshotPublish(
                "telemetry.snapshots.v1",
                "hub-1",
                42,
                CompletableFuture.completedFuture(null)
        );
        when(publisher.publish(snapshot)).thenReturn(pendingPublish);

        AggregationStarter starter = new AggregationStarter(
                consumer,
                properties,
                aggregationService,
                publisher,
                snapshotStateRestorer
        );
        starter.start();

        verify(snapshotStateRestorer).restorePublishedSnapshots();
        verify(consumer).subscribe(List.of("telemetry.sensors.v1"));
        verify(aggregationService).updateState(event);
        verify(publisher).publish(snapshot);
        verify(publisher).awaitPublications(List.of(pendingPublish));
        verify(publisher, times(2)).flush();
        verify(consumer).commitSync();
        verify(consumer).close();
        verify(publisher).close();
        InOrder inOrder = inOrder(snapshotStateRestorer, publisher, consumer);
        inOrder.verify(snapshotStateRestorer).restorePublishedSnapshots();
        inOrder.verify(publisher).flush();
        inOrder.verify(publisher).awaitPublications(List.of(pendingPublish));
        inOrder.verify(consumer).commitSync();
    }

    @Test
    void shouldFailFastWhenAggregationLoopThrowsFatalException() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorEventAvro> consumer = mock(Consumer.class);
        SnapshotAggregationService aggregationService = mock(SnapshotAggregationService.class);
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        SnapshotStateRestorer snapshotStateRestorer = mock(SnapshotStateRestorer.class);

        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        SensorEventAvro event = sensorEvent();
        TopicPartition partition = new TopicPartition("telemetry.sensors.v1", 0);
        ConsumerRecords<String, SensorEventAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(new ConsumerRecord<>("telemetry.sensors.v1", 0, 0L, "hub-1", event))
        ));

        when(consumer.poll(properties.getPollTimeout())).thenReturn(records);
        when(aggregationService.updateState(event)).thenThrow(new IllegalArgumentException("broken"));

        AggregationStarter starter = new AggregationStarter(
                consumer,
                properties,
                aggregationService,
                publisher,
                snapshotStateRestorer
        );

        assertThatThrownBy(starter::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fatal error while aggregating sensor events")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(snapshotStateRestorer).restorePublishedSnapshots();
        verify(consumer, never()).commitSync();
        verify(consumer).close();
        verify(publisher).close();
    }

    @Test
    void shouldSkipPoisonedRecordAndContinuePolling() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorEventAvro> consumer = mock(Consumer.class);
        SnapshotAggregationService aggregationService = mock(SnapshotAggregationService.class);
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        SnapshotStateRestorer snapshotStateRestorer = mock(SnapshotStateRestorer.class);

        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        SensorEventAvro event = sensorEvent();
        SensorsSnapshotAvro snapshot = SensorsSnapshotAvro.newBuilder()
                .setHubId("hub-1")
                .setVersion(1)
                .setTimestamp(event.getTimestamp())
                .setSensorsState(Map.of())
                .build();
        SnapshotPublisher.PendingSnapshotPublish pendingPublish = new SnapshotPublisher.PendingSnapshotPublish(
                "telemetry.snapshots.v1",
                "hub-1",
                42,
                CompletableFuture.completedFuture(null)
        );
        TopicPartition partition = new TopicPartition("telemetry.sensors.v1", 0);
        RecordDeserializationException poisonedRecord = new RecordDeserializationException(
                partition,
                7L,
                "bad payload",
                new IllegalArgumentException("broken bytes")
        );
        ConsumerRecords<String, SensorEventAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(new ConsumerRecord<>("telemetry.sensors.v1", 0, 8L, "hub-1", event))
        ));

        when(consumer.poll(properties.getPollTimeout()))
                .thenThrow(poisonedRecord)
                .thenReturn(records)
                .thenThrow(new WakeupException());
        when(aggregationService.updateState(event)).thenReturn(Optional.of(snapshot));
        when(publisher.publish(snapshot)).thenReturn(pendingPublish);

        AggregationStarter starter = new AggregationStarter(
                consumer,
                properties,
                aggregationService,
                publisher,
                snapshotStateRestorer
        );

        starter.start();

        verify(consumer).seek(partition, 8L);
        verify(consumer).commitSync(Map.of(partition, new OffsetAndMetadata(8L)));
        verify(aggregationService).updateState(event);
        verify(publisher).publish(snapshot);
        verify(publisher).awaitPublications(List.of(pendingPublish));
        verify(consumer).close();
        verify(publisher).close();
    }

    @Test
    void shouldCancelSnapshotRestoreWhenStopping() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorEventAvro> consumer = mock(Consumer.class);
        SnapshotAggregationService aggregationService = mock(SnapshotAggregationService.class);
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        SnapshotStateRestorer snapshotStateRestorer = mock(SnapshotStateRestorer.class);

        AggregationStarter starter = new AggregationStarter(
                consumer,
                new AggregatorKafkaProperties(),
                aggregationService,
                publisher,
                snapshotStateRestorer
        );

        starter.stop();

        verify(snapshotStateRestorer).cancelRestore();
        verify(consumer).wakeup();
    }

    private SensorEventAvro sensorEvent() {
        return SensorEventAvro.newBuilder()
                .setId("sensor.motion.1")
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(MotionSensorAvro.newBuilder()
                        .setLinkQuality(90)
                        .setMotion(true)
                        .setVoltage(220)
                        .build())
                .build();
    }
}
