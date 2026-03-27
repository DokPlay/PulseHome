package ru.yandex.practicum.telemetry.analyzer.service;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerGrpcProperties;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc.HubRouterControllerBlockingStub;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeviceActionDispatcher.class);

    private final HubRouterControllerBlockingStub hubRouterClient;
    private final AnalyzerGrpcProperties grpcProperties;

    public DeviceActionDispatcher(@GrpcClient("hub-router") HubRouterControllerBlockingStub hubRouterClient,
                                  AnalyzerGrpcProperties grpcProperties) {
        this.hubRouterClient = hubRouterClient;
        this.grpcProperties = grpcProperties;
    }

    public void dispatch(String hubId, String scenarioName, Instant timestamp, ActionSpec actionSpec) {
        DeviceActionRequest request = DeviceActionRequest.newBuilder()
                .setHubId(hubId)
                .setScenarioName(scenarioName)
                .setTimestamp(toTimestamp(timestamp))
                .setAction(DeviceActionProto.newBuilder()
                        .setSensorId(actionSpec.sensorId())
                        .setType(ActionTypeProto.valueOf(actionSpec.type().name()))
                        .setValue(actionSpec.value() == null ? 0 : actionSpec.value())
                        .build())
                .build();

        try {
            hubRouterClient.withDeadlineAfter(grpcProperties.getHubRouter().getActionTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .handleDeviceAction(request);
            log.info("Dispatched device action. hubId={}, scenario={}, sensorId={}, actionType={}",
                    hubId, scenarioName, actionSpec.sensorId(), actionSpec.type());
        } catch (StatusRuntimeException exception) {
            if (isRetryable(exception)) {
                throw new RetryableActionDispatchException(
                        "Failed to dispatch device action for scenario " + scenarioName + " due to a retryable gRPC error",
                        exception
                );
            }
            throw new IllegalStateException("Failed to dispatch device action for scenario " + scenarioName, exception);
        }
    }

    private boolean isRetryable(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
