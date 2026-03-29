package ru.yandex.practicum.telemetry.analyzer.service;

public class HubEventDeadLetterPublishException extends RuntimeException {

    public HubEventDeadLetterPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
