# Collector Sprint 19 Technical Design

## Status
Draft for planning only. No implementation yet.

## Sources
- User-provided sprint description and architecture notes in this task.
- OpenAPI spec: [Smart Home Technologies API](https://code.s3.yandex.net/Java/project19/http-api-spec.json?etag=636f8795094ed0aee7b6adf854eb6fc8)

## Goal
Implement the `Collector` microservice for Sprint 19. The service must receive smart-home telemetry from `Hub router` over HTTP in JSON format, convert the payload to Avro, and publish it into Kafka.

## Scope for Sprint 19
- HTTP only.
- No gRPC yet.
- No Aggregator or Analyzer implementation yet.
- No business execution of smart-home scenarios yet.
- Focus on intake, validation, normalization, Avro serialization, and Kafka publish.

## System Context
High-level flow for the current sprint:

`Hub router -> Collector -> Kafka`

Future flow after later sprints:

`Hub router -> Collector -> Kafka -> Aggregator -> Kafka -> Analyzer -> Hub router`

## Required Kafka Topics
- `telemetry.sensors.v1` for sensor/device telemetry events.
- `telemetry.hubs.v1` for hub and scenario events.

Naming format:

`<domain>.<event-type>.<version>`

Current interpretation:
- `telemetry`: telemetry-processing domain.
- `sensors` / `hubs`: event family.
- `v1`: first compatible contract version.

## Required HTTP API
According to the OpenAPI spec, Collector exposes exactly two `POST` endpoints:

### `POST /events/sensors`
Accepts one of:
- `ClimateSensorEvent`
- `LightSensorEvent`
- `MotionSensorEvent`
- `SwitchSensorEvent`
- `TemperatureSensorEvent`

Response documented in the spec:
- `200 OK`

### `POST /events/hubs`
Accepts one of:
- `DeviceAddedEvent`
- `DeviceRemovedEvent`
- `ScenarioAddedEvent`
- `ScenarioRemovedEvent`

Response documented in the spec:
- `200 OK`

## API Contract Summary

### Shared sensor-event fields
- `id`: sensor identifier.
- `hubId`: hub identifier.
- `timestamp`: ISO date-time, optional in the HTTP contract, should be normalized by the service if missing.
- `type`: one of:
  - `MOTION_SENSOR_EVENT`
  - `TEMPERATURE_SENSOR_EVENT`
  - `LIGHT_SENSOR_EVENT`
  - `CLIMATE_SENSOR_EVENT`
  - `SWITCH_SENSOR_EVENT`

### Shared hub-event fields
- `hubId`: hub identifier.
- `timestamp`: ISO date-time, optional in the HTTP contract, should be normalized by the service if missing.
- `type`: one of:
  - `DEVICE_ADDED`
  - `DEVICE_REMOVED`
  - `SCENARIO_ADDED`
  - `SCENARIO_REMOVED`

### Sensor event payload details
- `ClimateSensorEvent`: `temperatureC`, `humidity`, `co2Level`
- `LightSensorEvent`: `linkQuality`, `luminosity`
- `MotionSensorEvent`: `linkQuality`, `motion`, `voltage`
- `SwitchSensorEvent`: `state`
- `TemperatureSensorEvent`: `temperatureC`, `temperatureF`

### Hub event payload details
- `DeviceAddedEvent`: `id`, `deviceType`
- `DeviceRemovedEvent`: `id`
- `ScenarioAddedEvent`: `name`, `conditions[]`, `actions[]`
- `ScenarioRemovedEvent`: `name`

### Scenario enums from the task
- Device types:
  - `MOTION_SENSOR`
  - `TEMPERATURE_SENSOR`
  - `LIGHT_SENSOR`
  - `CLIMATE_SENSOR`
  - `SWITCH_SENSOR`
- Condition types:
  - `MOTION`
  - `LUMINOSITY`
  - `SWITCH`
  - `TEMPERATURE`
  - `CO2LEVEL`
  - `HUMIDITY`
- Condition operations:
  - `EQUALS`
  - `GREATER_THAN`
  - `LOWER_THAN`
- Action types:
  - `ACTIVATE`
  - `DEACTIVATE`
  - `INVERSE`
  - `SET_VALUE`

## Avro Contract
All Avro schemas must use namespace:

`ru.yandex.practicum.kafka.telemetry.event`

### Sensor-side Avro records
- `ClimateSensorAvro`
- `LightSensorAvro`
- `MotionSensorAvro`
- `SwitchSensorAvro`
- `TemperatureSensorAvro`
- `SensorEventAvro`

`SensorEventAvro` contains:
- `id`
- `hubId`
- `timestamp`
- `payload` as a union of:
  - `ClimateSensorAvro`
  - `LightSensorAvro`
  - `MotionSensorAvro`
  - `SwitchSensorAvro`
  - `TemperatureSensorAvro`

### Hub-side Avro records
- `DeviceTypeAvro`
- `ConditionTypeAvro`
- `ConditionOperationAvro`
- `ActionTypeAvro`
- `DeviceAddedEventAvro`
- `DeviceRemovedEventAvro`
- `ScenarioConditionAvro`
- `DeviceActionAvro`
- `ScenarioAddedEventAvro`
- `ScenarioRemovedEventAvro`
- `HubEventAvro`

`HubEventAvro` contains:
- `hub_id`
- `timestamp`
- `payload` as a union of:
  - `DeviceAddedEventAvro`
  - `DeviceRemovedEventAvro`
  - `ScenarioAddedEventAvro`
  - `ScenarioRemovedEventAvro`

## Mapping Rules

### Sensor endpoint mapping
- `CLIMATE_SENSOR_EVENT` -> `SensorEventAvro.payload = ClimateSensorAvro`
- `LIGHT_SENSOR_EVENT` -> `SensorEventAvro.payload = LightSensorAvro`
- `MOTION_SENSOR_EVENT` -> `SensorEventAvro.payload = MotionSensorAvro`
- `SWITCH_SENSOR_EVENT` -> `SensorEventAvro.payload = SwitchSensorAvro`
- `TEMPERATURE_SENSOR_EVENT` -> `SensorEventAvro.payload = TemperatureSensorAvro`
- Kafka topic: `telemetry.sensors.v1`

### Hub endpoint mapping
- `DEVICE_ADDED` -> `HubEventAvro.payload = DeviceAddedEventAvro`
- `DEVICE_REMOVED` -> `HubEventAvro.payload = DeviceRemovedEventAvro`
- `SCENARIO_ADDED` -> `HubEventAvro.payload = ScenarioAddedEventAvro`
- `SCENARIO_REMOVED` -> `HubEventAvro.payload = ScenarioRemovedEventAvro`
- Kafka topic: `telemetry.hubs.v1`

## Normalization Rules
- If `timestamp` is absent in incoming JSON, Collector should assign the current server time before Avro serialization.
- `ScenarioAddedEvent.name` and `ScenarioRemovedEvent.name` must respect the HTTP contract minimum length of 3 characters.
- `conditions` and `actions` for scenario creation are required and should be treated as non-empty collections.
- `ScenarioCondition.value` deserves special handling:
  - OpenAPI describes it as integer.
  - The sprint text says scenario values can represent numeric or boolean semantics.
  - Working assumption: HTTP JSON uses integer transport (`1` / `0` for boolean-like meaning), then Collector maps it into the Avro union form required by the scenario condition model.

The last point is an inference from the task plus the provided HTTP contract and should be validated before implementation if the training platform enforces a narrower interpretation.

## Recommended Internal Module Structure
Inside `telemetry/collector`:
- `api/controller`
  - HTTP controllers for `/events/sensors` and `/events/hubs`
- `api/dto`
  - Jackson-polymorphic request DTO hierarchy
- `validation`
  - request validation and type-specific rules
- `mapper`
  - JSON DTO -> Avro record mapping
- `producer`
  - Kafka publishing layer
- `config`
  - Spring, Kafka, Jackson, topic configuration
- `service`
  - orchestration for intake, normalization, serialization, and publish

Inside `telemetry/serialization/avro-schemas`:
- Avro schema files
- generated Avro classes

## Recommended Processing Pipeline
1. Receive JSON on the correct HTTP endpoint.
2. Deserialize through a polymorphic base type using `type` as the discriminator.
3. Validate common fields and type-specific fields.
4. Normalize missing timestamp and any transport-specific value conventions.
5. Map the request DTO into the correct Avro envelope record.
6. Publish the Avro payload to the correct Kafka topic.
7. Return success only after the publish operation is accepted by the producer path chosen for the implementation.

## Non-Functional Requirements
- Collector should stay thin and fast: no heavy domain logic beyond validation, normalization, mapping, and publish.
- The service must handle growing event volume from many hubs and sensors.
- Serialization format for Kafka output is Avro, not JSON.
- The implementation should favor low-latency event handoff to Kafka and clear failure diagnostics.

## Error Handling Guidance
The OpenAPI document only lists `200 OK`, but the implementation still needs internal failure behavior:
- Invalid JSON or unknown `type` should be treated as client-side contract violations.
- Kafka publish failures should be logged with enough detail to diagnose topic, event family, and root cause.
- Serialization failures should be logged with the incoming event type and discriminator.

Precise HTTP error mapping can be finalized during implementation if the training tests require a stricter response contract.

## Testing Strategy

### Contract tests
- Deserialize every documented OpenAPI example.
- Verify discriminator-based routing for all supported sensor and hub event types.

### Mapping tests
- Validate each JSON type maps to the correct Avro payload record.
- Validate topic selection by event family.
- Validate timestamp normalization when `timestamp` is omitted.

### Kafka tests
- Verify sensor events publish to `telemetry.sensors.v1`.
- Verify hub events publish to `telemetry.hubs.v1`.

### Local scenario tests
Use Hub Router local scripts for Sprint 19 Collector HTTP mode:
- branch/scenario `1-collector-json-tests`
- confirm HTTP intake
- confirm Kafka publication
- inspect the final execution report

## Out of Scope for This Sprint
- Collector gRPC API
- Aggregated household state
- Analyzer decision logic
- Scenario execution back to Hub router
- PostgreSQL persistence for business entities

## Open Questions to Confirm Before Coding
- Should Collector wait for Kafka broker acknowledgement before returning `200`, or is buffered producer acceptance enough for the tests?
- Should missing `timestamp` always default on the server, or do training tests always send it and only need tolerant parsing?
- For `ScenarioCondition.value`, does the checker expect integer-only transport or mixed boolean/integer handling before Avro conversion?
