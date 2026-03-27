package ru.yandex.practicum.telemetry.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import ru.yandex.practicum.telemetry.analyzer.service.HubEventProcessor;
import ru.yandex.practicum.telemetry.analyzer.service.SnapshotProcessor;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AnalyzerApplication {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerApplication.class);

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
            log.error("Hub event processor crashed and Analyzer will shut down", exception);
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
