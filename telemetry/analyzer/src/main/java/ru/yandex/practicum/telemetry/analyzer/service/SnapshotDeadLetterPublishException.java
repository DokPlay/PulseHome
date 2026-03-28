package ru.yandex.practicum.telemetry.analyzer.service;

public class SnapshotDeadLetterPublishException extends RuntimeException {

    public SnapshotDeadLetterPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
