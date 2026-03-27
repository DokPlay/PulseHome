package ru.yandex.practicum.telemetry.analyzer.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzerKafkaPropertiesValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldRejectZeroPollTimeout() {
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();
        properties.getSnapshotsConsumer().setPollTimeout(Duration.ZERO);

        Set<ConstraintViolation<AnalyzerKafkaProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("pollTimeout must be positive");
    }
}
