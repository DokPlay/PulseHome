package ru.yandex.practicum.telemetry.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import ru.yandex.practicum.telemetry.analyzer.service.HubEventProcessor;
import ru.yandex.practicum.telemetry.analyzer.service.SnapshotProcessor;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AnalyzerApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AnalyzerApplication.class, args);

        HubEventProcessor hubEventProcessor = context.getBean(HubEventProcessor.class);
        SnapshotProcessor snapshotProcessor = context.getBean(SnapshotProcessor.class);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            hubEventProcessor.stop();
            snapshotProcessor.stop();
        }, "analyzer-shutdown"));

        Thread hubEventsThread = new Thread(hubEventProcessor, "HubEventHandlerThread");
        hubEventsThread.setUncaughtExceptionHandler((thread, exception) -> {
            snapshotProcessor.stop();
            context.close();
        });
        hubEventsThread.start();

        try {
            snapshotProcessor.start();
        } catch (RuntimeException exception) {
            hubEventProcessor.stop();
            context.close();
            throw exception;
        }
    }
}
