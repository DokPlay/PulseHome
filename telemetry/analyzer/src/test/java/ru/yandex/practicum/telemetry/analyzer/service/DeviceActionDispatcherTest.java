package ru.yandex.practicum.telemetry.analyzer.service;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc.HubRouterControllerBlockingStub;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerGrpcProperties;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ActionType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceActionDispatcherTest {

    @Test
    void shouldMapActionSpecToGrpcRequest() {
        HubRouterControllerBlockingStub stub = mock(HubRouterControllerBlockingStub.class);
        HubRouterControllerBlockingStub deadlineStub = mock(HubRouterControllerBlockingStub.class);
        when(stub.withDeadlineAfter(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn(deadlineStub);
        when(deadlineStub.handleDeviceAction(org.mockito.ArgumentMatchers.any())).thenReturn(Empty.getDefaultInstance());

        DeviceActionDispatcher dispatcher = new DeviceActionDispatcher(stub, new AnalyzerGrpcProperties());
        ActionSpec actionSpec = new ActionSpec("switch.1", ActionType.SET_VALUE, 23);

        dispatcher.dispatch("hub-1", "warm-floor", Instant.parse("2024-08-06T15:11:24.157Z"), actionSpec);

        ArgumentCaptor<DeviceActionRequest> requestCaptor = ArgumentCaptor.forClass(DeviceActionRequest.class);
        verify(deadlineStub).handleDeviceAction(requestCaptor.capture());

        DeviceActionRequest request = requestCaptor.getValue();
        assertThat(request.getHubId()).isEqualTo("hub-1");
        assertThat(request.getScenarioName()).isEqualTo("warm-floor");
        assertThat(request.getAction().getSensorId()).isEqualTo("switch.1");
        assertThat(request.getAction().getType()).isEqualTo(ActionTypeProto.SET_VALUE);
        assertThat(request.getAction().getValue()).isEqualTo(23);
        assertThat(request.getAction().hasValue()).isTrue();
        assertThat(request.getTimestamp().getSeconds()).isEqualTo(1722957084L);
    }

    @Test
    void shouldOmitGrpcActionValueWhenAnalyzerActionValueIsNull() {
        HubRouterControllerBlockingStub stub = mock(HubRouterControllerBlockingStub.class);
        HubRouterControllerBlockingStub deadlineStub = mock(HubRouterControllerBlockingStub.class);
        when(stub.withDeadlineAfter(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn(deadlineStub);
        when(deadlineStub.handleDeviceAction(org.mockito.ArgumentMatchers.any())).thenReturn(Empty.getDefaultInstance());

        DeviceActionDispatcher dispatcher = new DeviceActionDispatcher(stub, new AnalyzerGrpcProperties());

        dispatcher.dispatch(
                "hub-1",
                "warm-floor",
                Instant.parse("2024-08-06T15:11:24.157Z"),
                new ActionSpec("switch.1", ActionType.ACTIVATE, null)
        );

        ArgumentCaptor<DeviceActionRequest> requestCaptor = ArgumentCaptor.forClass(DeviceActionRequest.class);
        verify(deadlineStub).handleDeviceAction(requestCaptor.capture());

        DeviceActionRequest request = requestCaptor.getValue();
        assertThat(request.getAction().hasValue()).isFalse();
        assertThat(request.getAction().getValue()).isZero();
    }

    @Test
    void shouldWrapRetryableGrpcErrorsInRetryableException() {
        HubRouterControllerBlockingStub stub = mock(HubRouterControllerBlockingStub.class);
        HubRouterControllerBlockingStub deadlineStub = mock(HubRouterControllerBlockingStub.class);
        when(stub.withDeadlineAfter(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn(deadlineStub);
        when(deadlineStub.handleDeviceAction(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        DeviceActionDispatcher dispatcher = new DeviceActionDispatcher(stub, new AnalyzerGrpcProperties());

        assertThatThrownBy(() -> dispatcher.dispatch(
                "hub-1",
                "warm-floor",
                Instant.parse("2024-08-06T15:11:24.157Z"),
                new ActionSpec("switch.1", ActionType.SET_VALUE, 23)
        )).isInstanceOf(RetryableActionDispatchException.class);
    }

    @Test
    void shouldTreatResourceExhaustedAsRetryableGrpcError() {
        HubRouterControllerBlockingStub stub = mock(HubRouterControllerBlockingStub.class);
        HubRouterControllerBlockingStub deadlineStub = mock(HubRouterControllerBlockingStub.class);
        when(stub.withDeadlineAfter(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn(deadlineStub);
        when(deadlineStub.handleDeviceAction(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));

        DeviceActionDispatcher dispatcher = new DeviceActionDispatcher(stub, new AnalyzerGrpcProperties());

        assertThatThrownBy(() -> dispatcher.dispatch(
                "hub-1",
                "warm-floor",
                Instant.parse("2024-08-06T15:11:24.157Z"),
                new ActionSpec("switch.1", ActionType.SET_VALUE, 23)
        )).isInstanceOf(RetryableActionDispatchException.class);
    }

    @Test
    void shouldTreatInternalAsFatalGrpcError() {
        HubRouterControllerBlockingStub stub = mock(HubRouterControllerBlockingStub.class);
        HubRouterControllerBlockingStub deadlineStub = mock(HubRouterControllerBlockingStub.class);
        when(stub.withDeadlineAfter(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn(deadlineStub);
        when(deadlineStub.handleDeviceAction(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        DeviceActionDispatcher dispatcher = new DeviceActionDispatcher(stub, new AnalyzerGrpcProperties());

        assertThatThrownBy(() -> dispatcher.dispatch(
                "hub-1",
                "warm-floor",
                Instant.parse("2024-08-06T15:11:24.157Z"),
                new ActionSpec("switch.1", ActionType.SET_VALUE, 23)
        )).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(RetryableActionDispatchException.class);
    }
}
