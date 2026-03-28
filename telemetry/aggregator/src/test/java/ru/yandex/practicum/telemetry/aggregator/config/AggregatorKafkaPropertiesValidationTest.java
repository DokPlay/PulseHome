package ru.yandex.practicum.telemetry.aggregator.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AggregatorKafkaPropertiesValidationTest {

    @Test
    void shouldRejectSendTimeoutThatDoesNotExceedLinger() {
        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        properties.setSendTimeout(Duration.ofMillis(5));
        properties.getProducer().setLingerMs(5);

        Set<ConstraintViolation<AggregatorKafkaProperties>> violations = validate(properties);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("aggregator.kafka.sendTimeout must be greater than aggregator.kafka.producer.lingerMs");
    }

    @Test
    void shouldRejectTooManyInFlightRequestsWhenIdempotenceEnabled() {
        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        properties.getProducer().setEnableIdempotence(true);
        properties.getProducer().setMaxInFlightRequestsPerConnection(6);

        Set<ConstraintViolation<AggregatorKafkaProperties>> violations = validate(properties);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("aggregator.kafka.producer.maxInFlightRequestsPerConnection must be <= 5 when idempotence is enabled");
    }

    @Test
    void shouldRejectUnsupportedAutoOffsetResetValue() {
        AggregatorKafkaProperties properties = new AggregatorKafkaProperties();
        properties.getConsumer().setAutoOffsetReset("random");

        Set<ConstraintViolation<AggregatorKafkaProperties>> violations = validate(properties);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("aggregator.kafka.consumer.autoOffsetReset must be one of: earliest, latest, none");
    }

    private Set<ConstraintViolation<AggregatorKafkaProperties>> validate(AggregatorKafkaProperties properties) {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            return validator.validate(properties);
        }
    }
}
