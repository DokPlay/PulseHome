package ru.yandex.practicum.pulsehome.infra.hubrouterstub;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HubRouterStubLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(HubRouterStubLifecycle.class);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private final HubRouterStubProperties properties;
    private final HubRouterStubService hubRouterStubService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Server server;

    public HubRouterStubLifecycle(HubRouterStubProperties properties,
                                  HubRouterStubService hubRouterStubService) {
        this.properties = properties;
        this.hubRouterStubService = hubRouterStubService;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            server = NettyServerBuilder.forPort(properties.getPort())
                    .addService(hubRouterStubService)
                    .build()
                    .start();
            log.info("Hub Router stub started on port {}", properties.getPort());
        } catch (IOException exception) {
            running.set(false);
            throw new IllegalStateException("Failed to start Hub Router stub", exception);
        }
    }

    @Override
    public void stop() {
        Server currentServer = server;
        server = null;

        if (currentServer == null) {
            running.set(false);
            return;
        }

        currentServer.shutdown();
        try {
            if (!currentServer.awaitTermination(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Hub Router stub did not stop within {}, forcing shutdown", STOP_TIMEOUT);
                currentServer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while stopping Hub Router stub", exception);
            currentServer.shutdownNow();
        } finally {
            running.set(false);
        }
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
}
