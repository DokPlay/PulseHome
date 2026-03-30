package ru.yandex.practicum.telemetry.collector.config.pqc;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.lang.reflect.Field;
import java.security.Security;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridPqcSslEngineFactoryTest {

    @Test
    void shouldPreferBcJsseProviderWhenItIsRegistered() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastleJsseProvider());

        HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
        factory.configure(Map.of());
        SSLEngine engine = factory.createClientSslEngine("localhost", 9093, "https");

        Field sslContextField = HybridPqcSslEngineFactory.class.getDeclaredField("sslContext");
        sslContextField.setAccessible(true);
        SSLContext sslContext = (SSLContext) sslContextField.get(factory);

        assertThat(sslContext.getProvider().getName()).isEqualTo("BCJSSE");
        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isEqualTo("https");
    }
}
