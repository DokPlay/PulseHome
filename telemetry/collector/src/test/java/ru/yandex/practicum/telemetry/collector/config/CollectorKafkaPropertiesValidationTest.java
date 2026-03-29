package ru.yandex.practicum.telemetry.collector.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CollectorKafkaPropertiesValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            validator = validatorFactory.getValidator();
        }
    }

    @Test
    void shouldRejectTooSmallSendTimeout() {
        CollectorKafkaProperties properties = new CollectorKafkaProperties();
        properties.setSendTimeout(Duration.ZERO);

        Set<ConstraintViolation<CollectorKafkaProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("sendTimeout");
    }

    @Test
    void shouldRejectMissingNestedConfigurationBlocks() {
        CollectorKafkaProperties properties = new CollectorKafkaProperties();
        properties.setProducer(null);
        properties.setTopics(null);

        Set<ConstraintViolation<CollectorKafkaProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("producer", "topics");
    }

    @Test
    void shouldRejectProducerLingerThatConsumesEntireSendTimeout() {
        CollectorKafkaProperties properties = new CollectorKafkaProperties();
        properties.setSendTimeout(Duration.ofMillis(1_000));
        properties.getProducer().setLingerMs(1_000);

        Set<ConstraintViolation<CollectorKafkaProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("collector.kafka.sendTimeout must be greater than collector.kafka.producer.lingerMs");
    }

    @Test
    void shouldRejectTooManyInFlightRequestsWhenIdempotenceIsEnabled() {
        CollectorKafkaProperties properties = new CollectorKafkaProperties();
        properties.getProducer().setEnableIdempotence(true);
        properties.getProducer().setMaxInFlightRequestsPerConnection(6);

        Set<ConstraintViolation<CollectorKafkaProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("collector.kafka.producer.maxInFlightRequestsPerConnection must be <= 5 when idempotence is enabled");
    }
}
