package ru.yandex.practicum.telemetry.analyzer.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigSslTest {

    @Test
    void shouldApplySslSettingsToCustomConsumersAndDlqProducer() {
        AnalyzerKafkaProperties properties = new AnalyzerKafkaProperties();
        properties.setBootstrapServers("kafka:9093");
        properties.getSsl().setSecurityProtocol("SSL");
        properties.getSsl().setTruststoreLocation("/tls/analyzer.truststore.p12");
        properties.getSsl().setTruststorePassword("trust-secret");
        properties.getSsl().setKeystoreLocation("/tls/analyzer.keystore.p12");
        properties.getSsl().setKeystorePassword("key-secret");
        properties.getSsl().setKeyPassword("private-secret");

        Map<String, Object> kafkaConfiguration = new HashMap<>();
        KafkaConsumerConfig.applySslProperties(kafkaConfiguration, properties);

        assertThat(kafkaConfiguration)
                .containsEntry("security.protocol", "SSL")
                .containsEntry("ssl.protocol", "TLSv1.3")
                .containsEntry("ssl.engine.factory.class",
                        "ru.yandex.practicum.telemetry.analyzer.config.pqc.HybridPqcSslEngineFactory")
                .containsEntry("ssl.truststore.location", "/tls/analyzer.truststore.p12")
                .containsEntry("ssl.keystore.location", "/tls/analyzer.keystore.p12")
                .containsEntry("ssl.key.password", "private-secret");
    }
}
