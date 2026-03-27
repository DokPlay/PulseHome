# Collector Code Review Notes

## Status
- Sprint 19 `Collector` implementation passed full local verification with `mvn -q test`.
- Current collector test suite covers 28 tests across controller, deserialization, mapper, and service behavior.

## Review Outcome
- No new blocking defects were found after the latest pass.
- Previously identified contract and diagnostics issues were addressed:
  - `LightSensorEvent` now requires both `linkQuality` and `luminosity`
  - the mapper no longer fabricates `0` values for missing light metrics
  - Kafka publish failures now log contextual metadata and return a structured API error
  - all sensor and hub event variants are covered by tests

## Accepted Design Decisions

### Synchronous Kafka Publish
- `CollectorEventService` uses `kafkaTemplate.send(...).get(...)`.
- This is intentionally synchronous for Sprint 19 so the HTTP response is only returned after Kafka acknowledges the event.
- The approach is less scalable than an async ingestion pipeline, but it is simpler and more deterministic for Hub Router verification.

### Temperature Payload Duplication
- `TemperatureSensorAvro` duplicates `id`, `hubId`, and `timestamp` even though the outer `SensorEventAvro` already contains them.
- This is preserved because the sprint technical specification explicitly requires that shape.

### Mixed Field Naming in Avro
- `SensorEventAvro` uses `hubId`, while hub-related records use fields like `hub_id` and `sensor_id`.
- The inconsistency is preserved because it mirrors the required contracts from the task description.

## Deferred Improvements
- `AvroBinarySerializer` could be optimized with cached writers and buffers if throughput becomes a bottleneck.
- DTO `toString()` methods can be added later if runtime diagnostics become more object-centric.
- Request-size and collection-size limits should be introduced only when backed by a confirmed product or infrastructure requirement.
- A future async publish model should be considered together with explicit delivery guarantees, retry policy, and backpressure design.
