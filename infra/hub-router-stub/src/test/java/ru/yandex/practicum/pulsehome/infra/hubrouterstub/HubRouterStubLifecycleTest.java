package ru.yandex.practicum.pulsehome.infra.hubrouterstub;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HubRouterStubLifecycleTest {

    @Test
    void shouldStartStubAndHandleDeviceAction() throws Exception {
        HubRouterStubProperties properties = new HubRouterStubProperties();
        properties.setPort(findFreePort());
        HubRouterStubLifecycle lifecycle = new HubRouterStubLifecycle(properties, new HubRouterStubService());

        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", properties.getPort())
                .usePlaintext()
                .build();
        try {
            Empty response = HubRouterControllerGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS)
                    .handleDeviceAction(DeviceActionRequest.newBuilder()
                            .setHubId("hub-1")
                            .setScenarioName("night-light")
                            .setTimestamp(Timestamp.newBuilder().setSeconds(1L).build())
                            .setAction(DeviceActionProto.newBuilder()
                                    .setSensorId("sensor.light.1")
                                    .setType(ActionTypeProto.ACTIVATE)
                                    .build())
                            .build());

            assertThat(response).isEqualTo(Empty.getDefaultInstance());
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(3, TimeUnit.SECONDS);
            lifecycle.stop();
        }

        assertThat(lifecycle.isRunning()).isFalse();
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
