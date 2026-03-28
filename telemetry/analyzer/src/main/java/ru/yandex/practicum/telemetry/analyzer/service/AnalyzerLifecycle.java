package ru.yandex.practicum.telemetry.analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AnalyzerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerLifecycle.class);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private final HubEventProcessor hubEventProcessor;
    private final SnapshotProcessor snapshotProcessor;
    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Thread hubEventsThread;
    private volatile Thread snapshotThread;

    public AnalyzerLifecycle(HubEventProcessor hubEventProcessor,
                             SnapshotProcessor snapshotProcessor,
                             ConfigurableApplicationContext applicationContext) {
        this.hubEventProcessor = hubEventProcessor;
        this.snapshotProcessor = snapshotProcessor;
        this.applicationContext = applicationContext;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        hubEventsThread = new Thread(hubEventProcessor, "analyzer-hub-events");
        snapshotThread = new Thread(snapshotProcessor::start, "analyzer-snapshots");

        hubEventsThread.setUncaughtExceptionHandler((thread, exception) ->
                handleProcessorFailure("Hub event processor", exception));
        snapshotThread.setUncaughtExceptionHandler((thread, exception) ->
                handleProcessorFailure("Snapshot processor", exception));

        hubEventsThread.start();
        snapshotThread.start();
    }

    @Override
    public void stop() {
        hubEventProcessor.stop();
        snapshotProcessor.stop();
        joinThread(hubEventsThread, "hub event");
        joinThread(snapshotThread, "snapshot");
        hubEventsThread = null;
        snapshotThread = null;
        running.set(false);
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private void joinThread(Thread thread, String threadName) {
        if (thread == null) {
            return;
        }

        try {
            thread.join(STOP_TIMEOUT.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for {} processor thread to stop", threadName, exception);
        }

        if (thread.isAlive()) {
            log.warn("{} processor thread did not stop within {}", threadName, STOP_TIMEOUT);
        }
    }

    private void handleProcessorFailure(String processorName, Throwable exception) {
        log.error("{} failed and analyzer will shut down", processorName, exception);
        closeContextIfActive();
    }

    private void closeContextIfActive() {
        if (applicationContext.isActive()) {
            applicationContext.close();
        }
    }
}
