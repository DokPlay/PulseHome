package ru.yandex.practicum.telemetry.analyzer.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.yandex.practicum.telemetry.analyzer.model.ActionSpec;
import ru.yandex.practicum.telemetry.analyzer.model.ActionType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(ActionDispatchTracker.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PostgresSchemaParityTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ActionDispatchTracker actionDispatchTracker;

    @Test
    void shouldRejectJoinTableUpdatesThatBreakHubConsistency() {
        long scenarioId = insertScenario("hub-1", "alarm");
        insertSensor("sensor.motion.1", "hub-1");
        insertSensor("sensor.motion.2", "hub-2");
        long conditionId = insertCondition("MOTION", "EQUALS", 1);
        jdbcTemplate.update(
                "insert into scenario_conditions (scenario_id, sensor_id, condition_id) values (?, ?, ?)",
                scenarioId,
                "sensor.motion.1",
                conditionId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update scenario_conditions set sensor_id = ? where scenario_id = ? and sensor_id = ? and condition_id = ?",
                "sensor.motion.2",
                scenarioId,
                "sensor.motion.1",
                conditionId
        )).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Hub IDs do not match");
    }

    @Test
    void shouldKeepOnlyOneNullValuedDispatchClaimPerSnapshot() {
        Instant snapshotTimestamp = Instant.parse("2024-08-06T15:11:24.157Z");
        ActionSpec action = new ActionSpec("switch.1", ActionType.ACTIVATE, null);

        assertThat(actionDispatchTracker.claimDispatch("hub-1", "scenario-1", snapshotTimestamp, 7L, action))
                .isTrue();
        assertThat(actionDispatchTracker.claimDispatch("hub-1", "scenario-1", snapshotTimestamp, 7L, action))
                .isFalse();
    }

    @Test
    void shouldEnforceGeneratedAlwaysIdentityColumnsInProductionSchema() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into scenarios (id, hub_id, name) values (?, ?, ?)",
                42L,
                "hub-1",
                "manual-id"
        )).isInstanceOf(DataAccessException.class);
    }

    private long insertScenario(String hubId, String name) {
        Long id = jdbcTemplate.queryForObject(
                "insert into scenarios (hub_id, name) values (?, ?) returning id",
                Long.class,
                hubId,
                name
        );
        return id == null ? -1L : id;
    }

    private void insertSensor(String sensorId, String hubId) {
        jdbcTemplate.update(
                "insert into sensors (id, hub_id) values (?, ?)",
                sensorId,
                hubId
        );
    }

    private long insertCondition(String type, String operation, Integer value) {
        Long id = jdbcTemplate.queryForObject(
                "insert into conditions (type, operation, value) values (?, ?, ?) returning id",
                Long.class,
                type,
                operation,
                value
        );
        return id == null ? -1L : id;
    }
}
