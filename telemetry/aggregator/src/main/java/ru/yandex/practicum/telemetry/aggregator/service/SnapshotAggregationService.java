package ru.yandex.practicum.telemetry.aggregator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class SnapshotAggregationService {

    private static final int DEFAULT_MAX_TRACKED_HUBS = 10_000;
    private static final Logger log = LoggerFactory.getLogger(SnapshotAggregationService.class);

    private final Map<String, SensorsSnapshotAvro> snapshots;
    private final Map<String, EvictedSnapshotMetadata> evictedSnapshots;

    public SnapshotAggregationService() {
        this(DEFAULT_MAX_TRACKED_HUBS);
    }

    SnapshotAggregationService(int maxTrackedHubs) {
        this.evictedSnapshots = createEvictedSnapshotCache(maxTrackedHubs);
        this.snapshots = createSnapshotCache(maxTrackedHubs);
    }

    public synchronized void restoreSnapshot(SensorsSnapshotAvro snapshot) {
        SensorsSnapshotAvro restoredSnapshot = copySnapshot(snapshot);
        SensorsSnapshotAvro currentSnapshot = snapshots.get(restoredSnapshot.getHubId());
        if (currentSnapshot != null && isCurrentSnapshotNewerOrEqual(currentSnapshot, restoredSnapshot)) {
            return;
        }

        snapshots.put(restoredSnapshot.getHubId(), restoredSnapshot);
        evictedSnapshots.remove(restoredSnapshot.getHubId());
    }

    public synchronized Optional<SensorsSnapshotAvro> updateState(SensorEventAvro event) {
        if (event.getTimestamp() == null) {
            log.warn("Received sensor event without timestamp. hubId={}, sensorId={}",
                    event.getHubId(), event.getId());
        }
        Instant eventTimestamp = normalizeTimestamp(event.getTimestamp());
        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(event.getHubId(),
                hubId -> createInitialSnapshot(hubId, eventTimestamp));

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

    private SensorsSnapshotAvro createInitialSnapshot(String hubId, Instant timestamp) {
        EvictedSnapshotMetadata evictedMetadata = evictedSnapshots.remove(hubId);
        if (evictedMetadata == null) {
            return createEmptySnapshot(hubId, timestamp);
        }

        Instant restoredTimestamp = maxTimestamp(evictedMetadata.timestamp(), timestamp);
        log.warn("Rebuilding snapshot after hub state eviction. hubId={}, lastKnownVersion={}, lastKnownSensors={}, lastKnownTimestamp={}",
                hubId, evictedMetadata.version(), evictedMetadata.sensorCount(), evictedMetadata.timestamp());
        return SensorsSnapshotAvro.newBuilder()
                .setHubId(hubId)
                .setVersion(evictedMetadata.version())
                .setTimestamp(restoredTimestamp)
                .setSensorsState(new HashMap<>())
                .build();
    }

    private SensorsSnapshotAvro createEmptySnapshot(String hubId, Instant timestamp) {
        return SensorsSnapshotAvro.newBuilder()
                .setHubId(hubId)
                .setVersion(0)
                .setTimestamp(timestamp)
                .setSensorsState(new HashMap<>())
                .build();
    }

    private SensorsSnapshotAvro copySnapshot(SensorsSnapshotAvro snapshot) {
        return SensorsSnapshotAvro.newBuilder(snapshot)
                .setSensorsState(new HashMap<>(snapshot.getSensorsState()))
                .build();
    }

    private boolean isCurrentSnapshotNewerOrEqual(SensorsSnapshotAvro currentSnapshot,
                                                  SensorsSnapshotAvro candidateSnapshot) {
        if (currentSnapshot.getVersion() > candidateSnapshot.getVersion()) {
            return true;
        }
        return currentSnapshot.getVersion() == candidateSnapshot.getVersion()
                && !currentSnapshot.getTimestamp().isBefore(candidateSnapshot.getTimestamp());
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

    private Map<String, SensorsSnapshotAvro> createSnapshotCache(int maxTrackedHubs) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SensorsSnapshotAvro> eldestEntry) {
                boolean shouldEvict = size() > maxTrackedHubs;
                if (shouldEvict) {
                    SensorsSnapshotAvro snapshot = eldestEntry.getValue();
                    evictedSnapshots.put(eldestEntry.getKey(), new EvictedSnapshotMetadata(
                            snapshot.getVersion(),
                            snapshot.getTimestamp(),
                            snapshot.getSensorsState().size()
                    ));
                    log.warn("Evicting hub snapshot from LRU cache. hubId={}, version={}, sensorCount={}, timestamp={}, maxTrackedHubs={}",
                            eldestEntry.getKey(), snapshot.getVersion(), snapshot.getSensorsState().size(),
                            snapshot.getTimestamp(), maxTrackedHubs);
                }
                return shouldEvict;
            }
        };
    }

    private Map<String, EvictedSnapshotMetadata> createEvictedSnapshotCache(int maxTrackedHubs) {
        return new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, EvictedSnapshotMetadata> eldestEntry) {
                return size() > maxTrackedHubs;
            }
        };
    }

    private record EvictedSnapshotMetadata(long version, Instant timestamp, int sensorCount) {
    }
}
