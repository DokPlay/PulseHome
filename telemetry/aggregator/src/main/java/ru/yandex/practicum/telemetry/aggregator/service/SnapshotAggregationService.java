package ru.yandex.practicum.telemetry.aggregator.service;

import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class SnapshotAggregationService {

    private final Map<String, SensorsSnapshotAvro> snapshots = new HashMap<>();

    public Optional<SensorsSnapshotAvro> updateState(SensorEventAvro event) {
        Instant eventTimestamp = normalizeTimestamp(event.getTimestamp());
        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(event.getHubId(),
                hubId -> createEmptySnapshot(hubId, eventTimestamp));

        SensorStateAvro oldState = snapshot.getSensorsState().get(event.getId());
        if (oldState != null) {
            if (oldState.getTimestamp().isAfter(eventTimestamp)) {
                return Optional.empty();
            }
            if (oldState.getData().equals(event.getPayload())) {
                return Optional.empty();
            }
        }

        // Update only when the incoming event advances the sensor state.
        SensorStateAvro newState = SensorStateAvro.newBuilder()
                .setTimestamp(eventTimestamp)
                .setData(event.getPayload())
                .build();

        Map<String, SensorStateAvro> updatedStates = new HashMap<>(snapshot.getSensorsState());
        updatedStates.put(event.getId(), newState);

        Instant updatedSnapshotTimestamp = maxTimestamp(snapshot.getTimestamp(), eventTimestamp);
        SensorsSnapshotAvro updatedSnapshot = SensorsSnapshotAvro.newBuilder(snapshot)
                .setVersion(snapshot.getVersion() + 1)
                .setTimestamp(updatedSnapshotTimestamp)
                .setSensorsState(updatedStates)
                .build();
        snapshots.put(event.getHubId(), updatedSnapshot);
        return Optional.of(updatedSnapshot);
    }

    private SensorsSnapshotAvro createEmptySnapshot(String hubId, Instant timestamp) {
        return SensorsSnapshotAvro.newBuilder()
                .setHubId(hubId)
                .setVersion(0)
                .setTimestamp(timestamp)
                .setSensorsState(new HashMap<>())
                .build();
    }

    private Instant maxTimestamp(Instant currentTimestamp, Instant candidateTimestamp) {
        if (currentTimestamp.isAfter(candidateTimestamp)) {
            return currentTimestamp;
        }
        return candidateTimestamp;
    }

    private Instant normalizeTimestamp(Instant timestamp) {
        Instant actualTimestamp = timestamp == null ? Instant.now() : timestamp;
        return actualTimestamp.truncatedTo(ChronoUnit.MILLIS);
    }
}
