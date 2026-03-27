package ru.yandex.practicum.telemetry.aggregator.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        AggregationStarter starter = new AggregationStarter(consumer, properties, aggregationService, publisher);
        starter.start();

        verify(consumer).subscribe(List.of("telemetry.sensors.v1"));
        verify(aggregationService).updateState(event);
        verify(publisher).publish(snapshot);
        verify(publisher, times(2)).flush();
        verify(consumer).commitSync();
        verify(consumer).close();
        verify(publisher).close();
    }

    @Test
    void shouldFailFastWhenAggregationLoopThrowsFatalException() {
        @SuppressWarnings("unchecked")
        Consumer<String, SensorEventAvro> consumer = mock(Consumer.class);
        SnapshotAggregationService aggregationService = mock(SnapshotAggregationService.class);
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);

        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        SensorEventAvro event = sensorEvent();
        TopicPartition partition = new TopicPartition("telemetry.sensors.v1", 0);
        ConsumerRecords<String, SensorEventAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(new ConsumerRecord<>("telemetry.sensors.v1", 0, 0L, "hub-1", event))
        ));

        when(consumer.poll(properties.getPollTimeout())).thenReturn(records);
        when(aggregationService.updateState(event)).thenThrow(new IllegalArgumentException("broken"));

        AggregationStarter starter = new AggregationStarter(consumer, properties, aggregationService, publisher);

        assertThatThrownBy(starter::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fatal error while aggregating sensor events")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(consumer, never()).commitSync();
        verify(consumer).close();
        verify(publisher).close();
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
