package ru.yandex.practicum.telemetry.analyzer.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HubEventDeadLetterPublisherTest {

    @Test
    void shouldPublishDlqMessageWithHubFallbackKey() throws Exception {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();
        HubEventDeadLetterPublisher publisher = new HubEventDeadLetterPublisher(producer, properties);

        HubEventAvro event = HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(DeviceAddedEventAvro.newBuilder()
                        .setId("sensor.light.1")
                        .setType(DeviceTypeAvro.MOTION_SENSOR)
                        .build())
                .build();
        ConsumerRecord<String, HubEventAvro> record =
                new ConsumerRecord<>("telemetry.hubs.v1", 1, 42L, null, event);
        CompletableFuture<RecordMetadata> sendResult = CompletableFuture.completedFuture(mock(RecordMetadata.class));

        @SuppressWarnings("unchecked")
        Class<ProducerRecord<String, String>> producerRecordClass =
                (Class<ProducerRecord<String, String>>) (Class<?>) ProducerRecord.class;
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(producerRecordClass);
        when(producer.send(recordCaptor.capture())).thenAnswer(invocation -> sendResult);

        boolean published = publisher.publish(record, new IllegalStateException("invalid scenario"));

        verify(producer).send(recordCaptor.getValue());
        ProducerRecord<String, String> publishedRecord = recordCaptor.getValue();
        assertThat(published).isTrue();

        assertThat(publishedRecord.topic()).isEqualTo("telemetry.hubs.dlq.v1");
        assertThat(publishedRecord.key()).isEqualTo("hub-1");
        assertThat(publishedRecord.value()).contains("\"sourceTopic\":\"telemetry.hubs.v1\"");
        assertThat(publishedRecord.value()).contains("\"sourceOffset\":42");
        assertThat(publishedRecord.value()).contains("\"errorType\":\"java.lang.IllegalStateException\"");
        assertThat(publishedRecord.value()).contains("\"errorMessage\":\"invalid scenario\"");
    }

    @Test
    void shouldReturnFalseWhenDlqBrokerIsUnavailable() throws Exception {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();
        HubEventDeadLetterPublisher publisher = new HubEventDeadLetterPublisher(producer, properties);

        HubEventAvro event = HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T15:11:24.157Z"))
                .setPayload(DeviceAddedEventAvro.newBuilder()
                        .setId("sensor.light.1")
                        .setType(DeviceTypeAvro.MOTION_SENSOR)
                        .build())
                .build();
        ConsumerRecord<String, HubEventAvro> record =
                new ConsumerRecord<>("telemetry.hubs.v1", 1, 42L, null, event);

        CompletableFuture<RecordMetadata> failedResult = new CompletableFuture<>();
        failedResult.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(producer.send(any())).thenAnswer(invocation -> failedResult);

        boolean published = publisher.publish(record, new IllegalStateException("invalid scenario"));

        assertThat(published).isFalse();
        verify(producer).send(any());
    }
}
