package ru.yandex.practicum.telemetry.aggregator.config.pqc;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.lang.reflect.Field;
import java.security.Security;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HybridPqcSslEngineFactoryTest {

    @BeforeAll
    static void registerProviders() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider("BCJSSE") == null) {
            Security.addProvider(new BouncyCastleJsseProvider());
        }
    }

    @Test
    void shouldUseBcJsseProviderAndSetNamedGroups() throws Exception {
        HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
        factory.configure(Map.of("ssl.pqc.require", "false"));

        SSLEngine engine = factory.createClientSslEngine("localhost", 9093, "https");

        Field sslContextField = HybridPqcSslEngineFactory.class.getDeclaredField("sslContext");
        sslContextField.setAccessible(true);
        SSLContext sslContext = (SSLContext) sslContextField.get(factory);

        assertThat(sslContext.getProvider().getName()).isEqualTo("BCJSSE");
        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isEqualTo("https");

        String[] groups = engine.getSSLParameters().getNamedGroups();
        assertThat(groups).isNotNull();
        assertThat(groups[0]).isEqualTo("X25519MLKEM768");
    }

    @Test
    void shouldCreateServerEngine() {
        HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
        factory.configure(Map.of("ssl.pqc.require", "false"));

        SSLEngine engine = factory.createServerSslEngine("localhost", 9093);

        assertThat(engine.getUseClientMode()).isFalse();
        String[] groups = engine.getSSLParameters().getNamedGroups();
        assertThat(groups).isNotNull();
        assertThat(groups[0]).isEqualTo("X25519MLKEM768");
    }

    @Test
    void shouldFailFastWhenPqcRequiredButGroupUnavailable() throws Exception {
        SSLContext bcjsseCtx = SSLContext.getInstance("TLSv1.3", "BCJSSE");
        bcjsseCtx.init(null, null, null);
        String[] supportedGroups = bcjsseCtx.getSupportedSSLParameters().getNamedGroups();
        boolean pqcAvailable = supportedGroups != null
                && Arrays.asList(supportedGroups).contains("X25519MLKEM768");

        if (pqcAvailable) {
            HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
            factory.configure(Map.of("ssl.pqc.require", "true"));

            SSLEngine engine = factory.createClientSslEngine("kafka", 9093, "https");
            assertThat(engine).isNotNull();
        } else {
            assertThatThrownBy(() -> {
                HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
                factory.configure(Map.of("ssl.pqc.require", "true"));
            })
                    .isInstanceOf(RuntimeException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause()
                    .hasMessageContaining("X25519MLKEM768");
        }
    }
}
