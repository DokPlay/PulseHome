package ru.yandex.practicum.telemetry.aggregator.service;

public class SnapshotPublishException extends RuntimeException {

    public SnapshotPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
