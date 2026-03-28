package ru.yandex.practicum.pulsehome.infra.hubrouterstub;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;

@Service
public class HubRouterStubService extends HubRouterControllerGrpc.HubRouterControllerImplBase {

    private static final Logger log = LoggerFactory.getLogger(HubRouterStubService.class);

    @Override
    public void handleDeviceAction(DeviceActionRequest request, StreamObserver<Empty> responseObserver) {
        DeviceActionProto action = request.getAction();
        String value = action.hasValue() ? Integer.toString(action.getValue()) : "null";

        log.info(
                "Received device action. hubId={}, scenario={}, sensorId={}, actionType={}, value={}, timestampSeconds={}",
                request.getHubId(),
                request.getScenarioName(),
                action.getSensorId(),
                action.getType(),
                value,
                request.getTimestamp().getSeconds()
        );

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
