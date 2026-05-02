package ru.yandex.practicum.telemetry.collector.config.pqc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.Arrays;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.kafka.common.config.types.Password;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    void shouldSelectProviderAndSetNamedGroups() throws Exception {
        HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
        factory.configure(Map.of("ssl.pqc.require", "false"));

        SSLEngine engine = factory.createClientSslEngine("localhost", 9093, "https");

        Field sslContextField = HybridPqcSslEngineFactory.class.getDeclaredField("sslContext");
        sslContextField.setAccessible(true);
        SSLContext sslContext = (SSLContext) sslContextField.get(factory);

        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isEqualTo("https");

        String[] groups = engine.getSSLParameters().getNamedGroups();
        assertThat(groups).isNotNull();
        assertThat(groups).isNotEmpty();
        if (bcSupportsPqc()) {
            assertThat(sslContext.getProvider().getName()).isEqualTo("BCJSSE");
            assertThat(groups[0]).isEqualTo("X25519MLKEM768");
        } else {
            assertThat(sslContext.getProvider().getName()).isNotEqualTo("BCJSSE");
            assertThat(groups).doesNotContain("X25519MLKEM768");
        }
    }

    @Test
    void shouldCreateServerEngine() throws Exception {
        HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
        factory.configure(Map.of("ssl.pqc.require", "false"));

        SSLEngine engine = factory.createServerSslEngine("localhost", 9093);

        assertThat(engine.getUseClientMode()).isFalse();
        String[] groups = engine.getSSLParameters().getNamedGroups();
        assertThat(groups).isNotNull();
        assertThat(groups).isNotEmpty();
        if (bcSupportsPqc()) {
            assertThat(groups[0]).isEqualTo("X25519MLKEM768");
        } else {
            assertThat(groups).doesNotContain("X25519MLKEM768");
        }
    }

    @Test
    void shouldReadKafkaPasswordValues() throws Exception {
        Method stringValue = HybridPqcSslEngineFactory.class.getDeclaredMethod("stringValue", Map.class, String.class);
        stringValue.setAccessible(true);

        Object value = stringValue.invoke(null, Map.of("ssl.truststore.password", new Password("secret")),
                "ssl.truststore.password");

        assertThat(value).isEqualTo("secret");
    }

    @Test
    void shouldFailFastWhenPqcRequiredButGroupUnavailable() throws Exception {
        if (bcSupportsPqc()) {
            // On JVM/BC that supports PQC — factory should start fine with require=true
            HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
            factory.configure(Map.of("ssl.pqc.require", "true"));

            SSLEngine engine = factory.createClientSslEngine("kafka", 9093, "https");
            assertThat(engine).isNotNull();
        } else {
            // On JVM/BC without PQC — factory must refuse to start
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

    @Test
    void shouldDefaultPqcRequireToTrue() throws Exception {
        // Map.of() — no ssl.pqc.require key — default should be true.
        // The factory will throw because PQC may not be available on test JVM,
        // but the important thing is it does NOT silently fall back.
        if (!bcSupportsPqc()) {
            assertThatThrownBy(() -> {
                HybridPqcSslEngineFactory factory = new HybridPqcSslEngineFactory();
                factory.configure(Map.of());
            })
                    .isInstanceOf(RuntimeException.class)
                    .rootCause()
                    .hasMessageContaining("X25519MLKEM768");
        }
    }

    private static boolean bcSupportsPqc() throws Exception {
        SSLContext bcjsseCtx = SSLContext.getInstance("TLSv1.3", "BCJSSE");
        bcjsseCtx.init(null, null, null);
        String[] supportedGroups = bcjsseCtx.getSupportedSSLParameters().getNamedGroups();
        return supportedGroups != null && Arrays.asList(supportedGroups).contains("X25519MLKEM768");
    }
}
