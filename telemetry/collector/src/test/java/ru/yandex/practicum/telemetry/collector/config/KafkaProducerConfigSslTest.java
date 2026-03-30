package ru.yandex.practicum.telemetry.collector.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigSslTest {

    @Test
    void shouldApplySslSettingsToExplicitKafkaConfigs() {
        CollectorKafkaProperties properties = new CollectorKafkaProperties();
        properties.setBootstrapServers("kafka:9093");
        properties.getSsl().setSecurityProtocol("SSL");
        properties.getSsl().setTruststoreLocation("/tls/client.truststore.p12");
        properties.getSsl().setTruststorePassword("trust-secret");
        properties.getSsl().setKeystoreLocation("/tls/client.keystore.p12");
        properties.getSsl().setKeystorePassword("key-secret");
        properties.getSsl().setKeyPassword("private-secret");

        KafkaProducerConfig kafkaProducerConfig = new KafkaProducerConfig();
        @SuppressWarnings("unchecked")
        DefaultKafkaProducerFactory<String, SpecificRecordBase> producerFactory =
                (DefaultKafkaProducerFactory<String, SpecificRecordBase>) kafkaProducerConfig.producerFactory(properties);

        assertThat(producerFactory.getConfigurationProperties())
                .containsEntry("security.protocol", "SSL")
                .containsEntry("ssl.protocol", "TLSv1.3")
                .containsEntry("ssl.engine.factory.class",
                        "ru.yandex.practicum.telemetry.collector.config.pqc.HybridPqcSslEngineFactory")
                .containsEntry("ssl.truststore.location", "/tls/client.truststore.p12")
                .containsEntry("ssl.keystore.location", "/tls/client.keystore.p12")
                .containsEntry("ssl.key.password", "private-secret");

        Map<String, Object> adminConfiguration = new HashMap<>();
        KafkaProducerConfig.applySslProperties(adminConfiguration, properties);

        assertThat(adminConfiguration)
                .containsEntry("security.protocol", "SSL")
                .containsEntry("ssl.engine.factory.class",
                        "ru.yandex.practicum.telemetry.collector.config.pqc.HybridPqcSslEngineFactory");
    }
}
