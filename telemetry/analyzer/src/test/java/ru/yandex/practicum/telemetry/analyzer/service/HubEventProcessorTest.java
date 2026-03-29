package ru.yandex.practicum.telemetry.analyzer.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HubEventProcessorTest {

    @Test
    void shouldProcessHubEventsAndCommitOffsets() {
        @SuppressWarnings("unchecked")
        Consumer<String, HubEventAvro> consumer = mock(Consumer.class);
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        HubEventDeadLetterPublisher deadLetterPublisher = mock(HubEventDeadLetterPublisher.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();

        HubEventAvro event = HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(DeviceAddedEventAvro.newBuilder()
                        .setId("sensor.1")
                        .setType(ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro.LIGHT_SENSOR)
                        .build())
                .build();

        TopicPartition partition = new TopicPartition("telemetry.hubs.v1", 0);
        ConsumerRecords<String, HubEventAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(new ConsumerRecord<>("telemetry.hubs.v1", 0, 0L, "hub-1", event))
        ));

        when(consumer.poll(properties.getHubsConsumer().getPollTimeout())).thenReturn(records).thenThrow(new WakeupException());

        HubEventProcessor processor = new HubEventProcessor(consumer, properties, hubConfigurationService, deadLetterPublisher);
        processor.run();

        verify(consumer).subscribe(List.of("telemetry.hubs.v1"));
        verify(hubConfigurationService).handleHubEvent(event);
        verify(deadLetterPublisher, never()).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(consumer, times(1)).commitSync(Map.of(partition, new OffsetAndMetadata(1L)));
        verify(consumer).close();
    }

    @Test
    void shouldSkipPoisonPillAndContinueProcessingRemainingRecords() {
        @SuppressWarnings("unchecked")
        Consumer<String, HubEventAvro> consumer = mock(Consumer.class);
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        HubEventDeadLetterPublisher deadLetterPublisher = mock(HubEventDeadLetterPublisher.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();

        HubEventAvro brokenEvent = HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(DeviceAddedEventAvro.newBuilder()
                        .setId("sensor.broken")
                        .setType(ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro.LIGHT_SENSOR)
                        .build())
                .build();

        HubEventAvro healthyEvent = HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:25.157Z"))
                .setPayload(DeviceAddedEventAvro.newBuilder()
                        .setId("sensor.ok")
                        .setType(ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro.MOTION_SENSOR)
                        .build())
                .build();

        TopicPartition partition = new TopicPartition("telemetry.hubs.v1", 0);
        ConsumerRecords<String, HubEventAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(
                        new ConsumerRecord<>("telemetry.hubs.v1", 0, 0L, "hub-1", brokenEvent),
                        new ConsumerRecord<>("telemetry.hubs.v1", 0, 1L, "hub-1", healthyEvent)
                )
        ));

        doThrow(new IllegalStateException("poison pill"))
                .when(hubConfigurationService)
                .handleHubEvent(brokenEvent);
        when(deadLetterPublisher.publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        when(consumer.poll(properties.getHubsConsumer().getPollTimeout())).thenReturn(records).thenThrow(new WakeupException());

        HubEventProcessor processor = new HubEventProcessor(consumer, properties, hubConfigurationService, deadLetterPublisher);
        processor.run();

        verify(hubConfigurationService).handleHubEvent(brokenEvent);
        verify(hubConfigurationService).handleHubEvent(healthyEvent);
        verify(deadLetterPublisher).publish(
                org.mockito.ArgumentMatchers.argThat(record -> record.offset() == 0L && brokenEvent.equals(record.value())),
                org.mockito.ArgumentMatchers.any(IllegalStateException.class)
        );
        verify(consumer, times(1)).commitSync(Map.of(partition, new OffsetAndMetadata(2L)));
        verify(consumer).close();
    }

    @Test
    void shouldFailFastWhenDlqPublishFails() {
        @SuppressWarnings("unchecked")
        Consumer<String, HubEventAvro> consumer = mock(Consumer.class);
        HubConfigurationService hubConfigurationService = mock(HubConfigurationService.class);
        HubEventDeadLetterPublisher deadLetterPublisher = mock(HubEventDeadLetterPublisher.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();

        HubEventAvro brokenEvent = HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(DeviceAddedEventAvro.newBuilder()
                        .setId("sensor.broken")
                        .setType(ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro.LIGHT_SENSOR)
                        .build())
                .build();

        TopicPartition partition = new TopicPartition("telemetry.hubs.v1", 0);
        ConsumerRecords<String, HubEventAvro> records = new ConsumerRecords<>(Map.of(
                partition, List.of(new ConsumerRecord<>("telemetry.hubs.v1", 0, 0L, "hub-1", brokenEvent))
        ));

        doThrow(new IllegalStateException("poison pill"))
                .when(hubConfigurationService)
                .handleHubEvent(brokenEvent);
        when(deadLetterPublisher.publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        when(consumer.poll(properties.getHubsConsumer().getPollTimeout())).thenReturn(records).thenThrow(new WakeupException());

        HubEventProcessor processor = new HubEventProcessor(consumer, properties, hubConfigurationService, deadLetterPublisher);

        assertThatThrownBy(processor::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hub event processor stopped after a fatal error");

        verify(deadLetterPublisher).publish(
                org.mockito.ArgumentMatchers.argThat(record -> record.offset() == 0L && brokenEvent.equals(record.value())),
                org.mockito.ArgumentMatchers.any(IllegalStateException.class)
        );
        verify(consumer, never()).commitSync(Map.of(partition, new OffsetAndMetadata(1L)));
        verify(consumer).close();
    }
}
