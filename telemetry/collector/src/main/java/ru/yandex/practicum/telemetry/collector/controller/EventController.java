package ru.yandex.practicum.telemetry.collector.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.telemetry.collector.dto.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.dto.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.service.CollectorEventService;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/events")
public class EventController {

    private final CollectorEventService collectorEventService;

    public EventController(CollectorEventService collectorEventService) {
        this.collectorEventService = collectorEventService;
    }

    @PostMapping("/sensors")
    public CompletableFuture<ResponseEntity<Void>> collectSensorEvent(@Valid @RequestBody SensorEvent event) {
        return collectorEventService.collectSensorEvent(event)
                .thenApply(ignored -> ResponseEntity.ok().build());
    }

    @PostMapping("/hubs")
    public CompletableFuture<ResponseEntity<Void>> collectHubEvent(@Valid @RequestBody HubEvent event) {
        return collectorEventService.collectHubEvent(event)
                .thenApply(ignored -> ResponseEntity.ok().build());
    }
}
