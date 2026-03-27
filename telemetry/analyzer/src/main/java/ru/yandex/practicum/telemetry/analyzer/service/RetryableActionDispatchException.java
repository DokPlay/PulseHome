package ru.yandex.practicum.telemetry.analyzer.service;

public class RetryableActionDispatchException extends RuntimeException {

    public RetryableActionDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
