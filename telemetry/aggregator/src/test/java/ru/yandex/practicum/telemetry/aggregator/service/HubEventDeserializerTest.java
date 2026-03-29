package ru.yandex.practicum.telemetry.aggregator.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.telemetry.serialization.AvroBinarySerializer;
import ru.yandex.practicum.telemetry.serialization.HubEventDeserializer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HubEventDeserializerTest {

    @Test
    void shouldDeserializeSingleObjectEncodedHubEvent() {
        HubEventAvro event = hubEvent();
        byte[] bytes = AvroBinarySerializer.serialize(event);

        try (HubEventDeserializer deserializer = new HubEventDeserializer()) {
            HubEventAvro deserialized = deserializer.deserialize("telemetry.hubs.v1", bytes);

            assertThat(deserialized.getHubId()).isEqualTo("hub-1");
            assertThat(deserialized.getTimestamp()).isEqualTo(event.getTimestamp());
            assertThat(deserialized.getPayload()).isInstanceOf(DeviceAddedEventAvro.class);
            DeviceAddedEventAvro payload = (DeviceAddedEventAvro) deserialized.getPayload();
            assertThat(payload.getId()).isEqualTo("sensor.motion.1");
            assertThat(payload.getType()).isEqualTo(DeviceTypeAvro.MOTION_SENSOR);
        }
    }

    @Test
    void shouldDeserializeLegacyRawHubEvent() {
        HubEventAvro event = hubEvent();
        byte[] bytes = AvroBinarySerializer.serializeLegacy(event);

        try (HubEventDeserializer deserializer = new HubEventDeserializer()) {
            HubEventAvro deserialized = deserializer.deserialize("telemetry.hubs.v1", bytes);

            assertThat(deserialized.getHubId()).isEqualTo("hub-1");
            assertThat(deserialized.getTimestamp()).isEqualTo(event.getTimestamp());
            assertThat(deserialized.getPayload()).isInstanceOf(DeviceAddedEventAvro.class);
            DeviceAddedEventAvro payload = (DeviceAddedEventAvro) deserialized.getPayload();
            assertThat(payload.getId()).isEqualTo("sensor.motion.1");
            assertThat(payload.getType()).isEqualTo(DeviceTypeAvro.MOTION_SENSOR);
        }
    }

    private HubEventAvro hubEvent() {
        return HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2024-08-06T16:54:03.129Z"))
                .setPayload(DeviceAddedEventAvro.newBuilder()
                        .setId("sensor.motion.1")
                        .setType(DeviceTypeAvro.MOTION_SENSOR)
                        .build())
                .build();
    }
}
