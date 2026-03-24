package ru.yandex.practicum.telemetry.collector.service;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.config.CollectorKafkaProperties;
import ru.yandex.practicum.telemetry.collector.dto.enums.ActionType;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionOperation;
import ru.yandex.practicum.telemetry.collector.dto.enums.ConditionType;
import ru.yandex.practicum.telemetry.collector.dto.enums.SensorEventType;
import ru.yandex.practicum.telemetry.collector.dto.hub.DeviceAction;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioAddedEvent;
import ru.yandex.practicum.telemetry.collector.dto.hub.ScenarioCondition;
import ru.yandex.practicum.telemetry.collector.dto.sensor.MotionSensorEvent;
import ru.yandex.practicum.telemetry.collector.mapper.HubEventAvroMapper;
import ru.yandex.practicum.telemetry.collector.mapper.SensorEventAvroMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorEventServiceTest {

    private KafkaTemplate<String, byte[]> kafkaTemplate;
    private CollectorEventService service;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        CollectorKafkaProperties properties = new CollectorKafkaProperties();
        service = new CollectorEventService(
                kafkaTemplate,
                properties,
                new SensorEventAvroMapper(),
                new HubEventAvroMapper(),
                new AvroBinarySerializer()
        );
    }

    @Test
    void shouldPublishSensorEventAsAvroBinary() throws IOException {
        MotionSensorEvent event = new MotionSensorEvent();
        event.setId("sensor.motion.1");
        event.setHubId("hub-1");
        event.setType(SensorEventType.MOTION_SENSOR_EVENT);
        event.setLinkQuality(87);
        event.setMotion(true);
        event.setVoltage(220);

        service.collectSensorEvent(event);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

        SensorEventAvro avroEvent = decode(payloadCaptor.getValue(), new SpecificDatumReader<>(SensorEventAvro.getClassSchema()));
        assertThat(avroEvent.getId()).isEqualTo("sensor.motion.1");
        assertThat(avroEvent.getHubId()).isEqualTo("hub-1");
    }

    @Test
    void shouldPublishHubEventAsAvroBinary() throws IOException {
        ScenarioAddedEvent event = new ScenarioAddedEvent();
        event.setHubId("hub-3");
        event.setName("Night light");
        event.setConditions(List.of(condition()));
        event.setActions(List.of(action()));

        service.collectHubEvent(event);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

        HubEventAvro avroEvent = decode(payloadCaptor.getValue(), new SpecificDatumReader<>(HubEventAvro.getClassSchema()));
        assertThat(avroEvent.getHubId()).isEqualTo("hub-3");
        assertThat(avroEvent.getPayload()).isInstanceOf(ScenarioAddedEventAvro.class);
        ScenarioAddedEventAvro payload = (ScenarioAddedEventAvro) avroEvent.getPayload();
        assertThat(payload.getConditions()).hasSize(1);
        assertThat(payload.getConditions().get(0).getValue()).isEqualTo(true);
    }

    private <T> T decode(byte[] payload, SpecificDatumReader<T> reader) throws IOException {
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
        return reader.read(null, decoder);
    }

    private ScenarioCondition condition() {
        ScenarioCondition condition = new ScenarioCondition();
        condition.setSensorId("sensor.motion.1");
        condition.setType(ConditionType.MOTION);
        condition.setOperation(ConditionOperation.EQUALS);
        condition.setValue(1);
        return condition;
    }

    private DeviceAction action() {
        DeviceAction action = new DeviceAction();
        action.setSensorId("sensor.switch.1");
        action.setType(ActionType.ACTIVATE);
        return action;
    }
}
