package ru.yandex.practicum.telemetry.aggregator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotAggregationServiceTest {

    private SnapshotAggregationService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotAggregationService();
    }

    @Test
    void shouldCreateSnapshotForFirstEventInHub() {
        SensorEventAvro event = motionEvent("hub-1", "sensor.motion.1", Instant.parse("2024-08-06T15:11:24.157Z"), true);

        Optional<SensorsSnapshotAvro> snapshot = service.updateState(event);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().getHubId()).isEqualTo("hub-1");
        assertThat(snapshot.orElseThrow().getVersion()).isEqualTo(1);
        assertThat(snapshot.orElseThrow().getSensorsState()).containsKey("sensor.motion.1");
    }

    @Test
    void shouldIgnoreOutdatedEvent() {
        SensorEventAvro freshEvent = motionEvent("hub-1", "sensor.motion.1", Instant.parse("2024-08-06T15:11:25.157Z"), true);
        SensorEventAvro staleEvent = motionEvent("hub-1", "sensor.motion.1", Instant.parse("2024-08-06T15:11:24.157Z"), false);

        service.updateState(freshEvent);
        Optional<SensorsSnapshotAvro> snapshot = service.updateState(staleEvent);

        assertThat(snapshot).isEmpty();
    }

    @Test
    void shouldIgnoreDuplicateSensorPayload() {
        Instant timestamp = Instant.parse("2024-08-06T15:11:24.157Z");
        SensorEventAvro first = lightEvent("hub-1", "sensor.light.1", timestamp, 60);
        SensorEventAvro duplicate = lightEvent("hub-1", "sensor.light.1", timestamp.plusSeconds(10), 60);

        service.updateState(first);
        Optional<SensorsSnapshotAvro> snapshot = service.updateState(duplicate);

        assertThat(snapshot).isEmpty();
    }

    @Test
    void shouldUpdateSnapshotWhenPayloadChanges() {
        SensorEventAvro first = lightEvent("hub-1", "sensor.light.1", Instant.parse("2024-08-06T15:11:24.157Z"), 60);
        SensorEventAvro second = lightEvent("hub-1", "sensor.light.1", Instant.parse("2024-08-06T15:11:25.157Z"), 12);

        service.updateState(first);
        SensorsSnapshotAvro snapshot = service.updateState(second).orElseThrow();

        assertThat(snapshot.getVersion()).isEqualTo(2);
        assertThat(snapshot.getTimestamp()).isEqualTo(Instant.parse("2024-08-06T15:11:25.157Z"));
        LightSensorAvro state = (LightSensorAvro) snapshot.getSensorsState().get("sensor.light.1").getData();
        assertThat(state.getLuminosity()).isEqualTo(12);
    }

    @Test
    void shouldAddSecondSensorToExistingSnapshot() {
        SensorEventAvro light = lightEvent("hub-1", "sensor.light.1", Instant.parse("2024-08-06T15:11:24.157Z"), 60);
        SensorEventAvro motion = motionEvent("hub-1", "sensor.motion.1", Instant.parse("2024-08-06T15:11:25.157Z"), true);

        service.updateState(light);
        SensorsSnapshotAvro snapshot = service.updateState(motion).orElseThrow();

        assertThat(snapshot.getVersion()).isEqualTo(2);
        assertThat(snapshot.getSensorsState()).hasSize(2);
        assertThat(snapshot.getSensorsState()).containsKeys("sensor.light.1", "sensor.motion.1");
    }

    @Test
    void shouldKeepHubSnapshotTimestampMonotonicWhenOlderSensorAppears() {
        SensorEventAvro freshLight = lightEvent("hub-1", "sensor.light.1", Instant.parse("2024-08-06T15:11:25.157Z"), 60);
        SensorEventAvro olderMotion = motionEvent("hub-1", "sensor.motion.1", Instant.parse("2024-08-06T15:11:24.157Z"), true);

        service.updateState(freshLight);
        SensorsSnapshotAvro snapshot = service.updateState(olderMotion).orElseThrow();

        assertThat(snapshot.getVersion()).isEqualTo(2);
        assertThat(snapshot.getTimestamp()).isEqualTo(Instant.parse("2024-08-06T15:11:25.157Z"));
        assertThat(snapshot.getSensorsState()).containsKeys("sensor.light.1", "sensor.motion.1");
    }

    @Test
    void shouldEvictLeastRecentlyUpdatedHubWhenCacheLimitIsReached() {
        SnapshotAggregationService limitedService = new SnapshotAggregationService(2);

        limitedService.updateState(lightEvent("hub-1", "sensor.light.1", Instant.parse("2024-08-06T15:11:24.157Z"), 60));
        limitedService.updateState(lightEvent("hub-2", "sensor.light.2", Instant.parse("2024-08-06T15:11:25.157Z"), 61));
        limitedService.updateState(lightEvent("hub-3", "sensor.light.3", Instant.parse("2024-08-06T15:11:26.157Z"), 62));

        SensorsSnapshotAvro recreatedSnapshot = limitedService.updateState(
                lightEvent("hub-1", "sensor.light.1", Instant.parse("2024-08-06T15:11:27.157Z"), 63)
        ).orElseThrow();

        assertThat(recreatedSnapshot.getVersion()).isEqualTo(1);
        assertThat(recreatedSnapshot.getTimestamp()).isEqualTo(Instant.parse("2024-08-06T15:11:27.157Z"));
    }

    private SensorEventAvro motionEvent(String hubId, String sensorId, Instant timestamp, boolean motion) {
        return SensorEventAvro.newBuilder()
                .setHubId(hubId)
                .setId(sensorId)
                .setTimestamp(timestamp)
                .setPayload(MotionSensorAvro.newBuilder()
                        .setLinkQuality(87)
                        .setMotion(motion)
                        .setVoltage(220)
                        .build())
                .build();
    }

    private SensorEventAvro lightEvent(String hubId, String sensorId, Instant timestamp, int luminosity) {
        return SensorEventAvro.newBuilder()
                .setHubId(hubId)
                .setId(sensorId)
                .setTimestamp(timestamp)
                .setPayload(LightSensorAvro.newBuilder()
                        .setLinkQuality(80)
                        .setLuminosity(luminosity)
                        .build())
                .build();
    }
}
