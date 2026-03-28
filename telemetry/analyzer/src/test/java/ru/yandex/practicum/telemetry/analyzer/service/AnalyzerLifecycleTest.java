package ru.yandex.practicum.telemetry.analyzer.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyzerLifecycleTest {

    @Test
    void shouldStartProcessorsAndStopGracefully() throws Exception {
        HubEventProcessor hubEventProcessor = mock(HubEventProcessor.class);
        SnapshotProcessor snapshotProcessor = mock(SnapshotProcessor.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        AnalyzerLifecycle lifecycle = new AnalyzerLifecycle(hubEventProcessor, snapshotProcessor, context);
        CountDownLatch hubStarted = new CountDownLatch(1);
        CountDownLatch snapshotStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(2);

        doAnswer(invocation -> {
            hubStarted.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(hubEventProcessor).run();
        doAnswer(invocation -> {
            snapshotStarted.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(snapshotProcessor).start();
        doAnswer(invocation -> {
            release.countDown();
            return null;
        }).when(hubEventProcessor).stop();
        doAnswer(invocation -> {
            release.countDown();
            return null;
        }).when(snapshotProcessor).stop();

        lifecycle.start();
        assertThat(hubStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(snapshotStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(lifecycle.isRunning()).isTrue();

        lifecycle.stop();

        verify(hubEventProcessor).run();
        verify(snapshotProcessor).start();
        verify(hubEventProcessor).stop();
        verify(snapshotProcessor).stop();
        verify(context, never()).close();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void shouldCloseContextWhenProcessorThreadFails() {
        HubEventProcessor hubEventProcessor = mock(HubEventProcessor.class);
        SnapshotProcessor snapshotProcessor = mock(SnapshotProcessor.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        AnalyzerLifecycle lifecycle = new AnalyzerLifecycle(hubEventProcessor, snapshotProcessor, context);

        doAnswer(invocation -> {
            throw new IllegalStateException("hub failure");
        }).when(hubEventProcessor).run();
        when(context.isActive()).thenReturn(true);

        lifecycle.start();
        waitFor(() -> !lifecycle.isRunning());

        verify(context).close();
    }

    private void waitFor(Check check) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (check.evaluate()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for condition", exception);
            }
        }
        throw new AssertionError("Condition was not met in time");
    }

    @FunctionalInterface
    private interface Check {
        boolean evaluate();
    }
}
