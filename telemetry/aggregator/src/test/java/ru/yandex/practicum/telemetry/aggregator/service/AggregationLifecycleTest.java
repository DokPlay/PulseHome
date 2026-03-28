package ru.yandex.practicum.telemetry.aggregator.service;

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

class AggregationLifecycleTest {

    @Test
    void shouldStartWorkerAndStopGracefully() throws Exception {
        AggregationStarter aggregationStarter = mock(AggregationStarter.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        AggregationLifecycle lifecycle = new AggregationLifecycle(aggregationStarter, context);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        doAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(aggregationStarter).start();
        doAnswer(invocation -> {
            release.countDown();
            return null;
        }).when(aggregationStarter).stop();

        lifecycle.start();
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(lifecycle.isRunning()).isTrue();

        lifecycle.stop();

        verify(aggregationStarter).start();
        verify(aggregationStarter).stop();
        verify(context, never()).close();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void shouldCloseApplicationContextWhenWorkerFails() {
        AggregationStarter aggregationStarter = mock(AggregationStarter.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        AggregationLifecycle lifecycle = new AggregationLifecycle(aggregationStarter, context);

        doAnswer(invocation -> {
            throw new IllegalStateException("boom");
        }).when(aggregationStarter).start();
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
