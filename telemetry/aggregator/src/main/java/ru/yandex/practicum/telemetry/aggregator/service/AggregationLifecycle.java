package ru.yandex.practicum.telemetry.aggregator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AggregationLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AggregationLifecycle.class);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private final AggregationStarter aggregationStarter;
    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Thread workerThread;

    public AggregationLifecycle(AggregationStarter aggregationStarter,
                                ConfigurableApplicationContext applicationContext) {
        this.aggregationStarter = aggregationStarter;
        this.applicationContext = applicationContext;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                aggregationStarter.start();
            } finally {
                workerThread = null;
                running.set(false);
            }
        }, "aggregation-main");
        thread.setUncaughtExceptionHandler((currentThread, exception) -> {
            log.error("Aggregation lifecycle thread failed and application will shut down", exception);
            closeContextIfActive();
        });
        workerThread = thread;
        thread.start();
    }

    @Override
    public void stop() {
        aggregationStarter.stop();
        joinWorkerThread();
        running.set(false);
    }

    @Override
    public void stop(@NonNull Runnable callback) {
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

    private void joinWorkerThread() {
        Thread thread = workerThread;
        if (thread == null) {
            return;
        }

        try {
            thread.join(STOP_TIMEOUT.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for aggregation thread to stop", exception);
        }

        if (thread.isAlive()) {
            log.warn("Aggregation thread did not stop within {}", STOP_TIMEOUT);
        }
    }

    private void closeContextIfActive() {
        if (applicationContext.isActive()) {
            applicationContext.close();
        }
    }
}
