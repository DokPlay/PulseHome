package ru.yandex.practicum.telemetry.collector.service;

import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.config.CollectorKafkaProperties;
import ru.yandex.practicum.telemetry.collector.dto.enums.ActionType;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionOperation;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionType;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceAction;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioAddedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioCondition;
import ru.yandex.practicum.telemetry.collector.exception.EventPublishException;
import ru.yandex.practicum.telemetry.collector.dto.sensor.MotionSensorEvent;
import ru.yandex.practicum.telemetry.collector.exception.InvalidScenarioConditionValueException;
import ru.yandex.practicum.telemetry.collector.mapper.HubEventAvroMapper;
import ru.yandex.practicum.telemetry.collector.mapper.SensorEventAvroMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorEventServiceTest {

    private KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;
    private CollectorEventService service;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mockKafkaTemplate();
        stubSendResult(kafkaTemplate, CompletableFuture.completedFuture(null));

        CollectorKafkaProperties properties = new CollectorKafkaProperties();
        service = new CollectorEventService(
                kafkaTemplate,
                properties,
                new SensorEventAvroMapper(),
                new HubEventAvroMapper()
        );
    }

    @Test
    void shouldPublishSensorEventAsAvroRecord() {
        MotionSensorEvent event = new MotionSensorEvent("sensor.motion.1", "hub-1", null, 87, true, 220);

        service.collectSensorEvent(event).join();

        ArgumentCaptor<SpecificRecordBase> payloadCaptor = ArgumentCaptor.forClass(SpecificRecordBase.class);
        verifySendCaptured(kafkaTemplate, payloadCaptor);

        SensorEventAvro avroEvent = (SensorEventAvro) payloadCaptor.getValue();
        assertThat(avroEvent.getId()).isEqualTo("sensor.motion.1");
        assertThat(avroEvent.getHubId()).isEqualTo("hub-1");
    }

    @Test
    void shouldPublishHubEventAsAvroRecord() {
        ScenarioAddedEvent event = new ScenarioAddedEvent("hub-3", null, "Night light", List.of(condition()), List.of(action()));

        service.collectHubEvent(event).join();

        ArgumentCaptor<SpecificRecordBase> payloadCaptor = ArgumentCaptor.forClass(SpecificRecordBase.class);
        verifySendCaptured(kafkaTemplate, payloadCaptor);

        HubEventAvro avroEvent = (HubEventAvro) payloadCaptor.getValue();
        assertThat(avroEvent.getHubId()).isEqualTo("hub-3");
        assertThat(avroEvent.getPayload()).isInstanceOf(ScenarioAddedEventAvro.class);
        ScenarioAddedEventAvro payload = (ScenarioAddedEventAvro) avroEvent.getPayload();
        assertThat(payload.getConditions()).hasSize(1);
        assertThat(payload.getConditions().get(0).getValue()).isEqualTo(true);
    }

    @Test
    void shouldIncludeTopicAndKeyInPublishFailureMessage() {
        KafkaTemplate<String, SpecificRecordBase> failingKafkaTemplate = mockKafkaTemplate();
        CompletableFuture<SendResult<String, SpecificRecordBase>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new TimeoutException("broker did not acknowledge"));
        stubSendResult(failingKafkaTemplate, failedFuture);

        CollectorEventService failingService = new CollectorEventService(
                failingKafkaTemplate,
                new CollectorKafkaProperties(),
                new SensorEventAvroMapper(),
                new HubEventAvroMapper()
        );

        MotionSensorEvent event = new MotionSensorEvent("sensor.motion.1", "hub-9", null, 87, true, 220);

        Throwable failure = catchThrowable(() -> failingService.collectSensorEvent(event).join());

        assertThat(failure).isInstanceOf(CompletionException.class);
        assertThat(failure.getCause())
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("topic=telemetry.sensors.v1")
                .hasMessageContaining("key=hub-9");
    }

    @Test
    void shouldFallbackToExceptionTypeWhenPublishFailureCauseMessageIsMissing() {
        KafkaTemplate<String, SpecificRecordBase> failingKafkaTemplate = mockKafkaTemplate();
        CompletableFuture<SendResult<String, SpecificRecordBase>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new TimeoutException());
        stubSendResult(failingKafkaTemplate, failedFuture);

        CollectorEventService failingService = new CollectorEventService(
                failingKafkaTemplate,
                new CollectorKafkaProperties(),
                new SensorEventAvroMapper(),
                new HubEventAvroMapper()
        );

        MotionSensorEvent event = new MotionSensorEvent("sensor.motion.2", "hub-10", null, 90, true, 230);

        Throwable failure = catchThrowable(() -> failingService.collectSensorEvent(event).join());

        assertThat(failure).isInstanceOf(CompletionException.class);
        assertThat(failure.getCause())
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("cause=TimeoutException");
    }

    @Test
    void shouldWrapRuntimeFailureThrownByKafkaSend() {
        KafkaTemplate<String, SpecificRecordBase> failingKafkaTemplate = mockKafkaTemplate();
        stubSendFailure(failingKafkaTemplate, new IllegalStateException("producer factory is not initialized"));

        CollectorEventService failingService = new CollectorEventService(
                failingKafkaTemplate,
                new CollectorKafkaProperties(),
                new SensorEventAvroMapper(),
                new HubEventAvroMapper()
        );

        MotionSensorEvent event = new MotionSensorEvent("sensor.motion.3", "hub-11", null, 91, true, 231);

        Throwable failure = catchThrowable(() -> failingService.collectSensorEvent(event).join());

        assertThat(failure).isInstanceOf(CompletionException.class);
        assertThat(failure.getCause())
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("topic=telemetry.sensors.v1")
                .hasMessageContaining("key=hub-11")
                .hasMessageContaining("cause=producer factory is not initialized");
    }

    @Test
    void shouldFailFastOnInvalidBooleanLikeConditionValue() {
        ScenarioAddedEvent event = new ScenarioAddedEvent("hub-3", null, "Broken scenario", List.of(invalidCondition()), List.of(action()));

        assertThatThrownBy(() -> service.collectHubEvent(event).join())
                .isInstanceOf(InvalidScenarioConditionValueException.class)
                .hasMessageContaining("0 or 1");

        verifySendNeverCalled(kafkaTemplate);
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, SpecificRecordBase> mockKafkaTemplate() {
        return (KafkaTemplate<String, SpecificRecordBase>) mock(KafkaTemplate.class);
    }

    @SuppressWarnings("null")
    private void stubSendResult(KafkaTemplate<String, SpecificRecordBase> kafkaTemplate,
                                CompletableFuture<SendResult<String, SpecificRecordBase>> sendFuture) {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sendFuture);
    }

    @SuppressWarnings("null")
    private void stubSendFailure(KafkaTemplate<String, SpecificRecordBase> kafkaTemplate, RuntimeException exception) {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(exception);
    }

    @SuppressWarnings("null")
    private void verifySendCaptured(KafkaTemplate<String, SpecificRecordBase> kafkaTemplate,
                                    ArgumentCaptor<SpecificRecordBase> payloadCaptor) {
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
    }

    @SuppressWarnings("null")
    private void verifySendNeverCalled(KafkaTemplate<String, SpecificRecordBase> kafkaTemplate) {
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    private ScenarioCondition condition() {
        return new ScenarioCondition("sensor.motion.1", ConditionType.MOTION, ConditionOperation.EQUALS, 1);
    }

    private DeviceAction action() {
        return new DeviceAction("sensor.switch.1", ActionType.ACTIVATE, null);
    }

    private ScenarioCondition invalidCondition() {
        return new ScenarioCondition("sensor.switch.1", ConditionType.SWITCH, ConditionOperation.EQUALS, 2);
    }
}
