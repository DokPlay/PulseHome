package ru.yandex.practicum.telemetry.collector.mapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class TimestampNormalizer {

    private TimestampNormalizer() {
    }

    static Instant normalize(Instant timestamp) {
        Instant actualTimestamp = timestamp == null ? Instant.now() : timestamp;
        return actualTimestamp.truncatedTo(ChronoUnit.MILLIS);
    }
}
