package ru.yandex.practicum.telemetry.collector.config.pqc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.apache.kafka.common.security.auth.SslEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom {@link SslEngineFactory} for Kafka clients that enables hybrid
 * post-quantum key exchange via TLS 1.3.
 *
 * <p>Prioritises the {@code X25519MLKEM768} named group, which combines
 * classical ECDH (X25519) with the NIST-standardised ML-KEM-768
 * (FIPS 203).  This defends against "Store Now, Decrypt Later" attacks
 * while maintaining full backward-compatibility: if the JVM or the
 * remote peer does not support the hybrid group, the handshake falls
 * back to classical ECDH automatically.</p>
 *
 * <h3>Kafka configuration</h3>
 * <pre>
 * ssl.engine.factory.class=ru.yandex.practicum.telemetry.collector.config.pqc.HybridPqcSslEngineFactory
 * </pre>
 *
 * <p>Compatible with Kafka 3.7+ (uses {@code createClientSslEngine} /
 * {@code createServerSslEngine} contract).</p>
 */
public class HybridPqcSslEngineFactory implements SslEngineFactory {

    private static final Logger log = LoggerFactory.getLogger(HybridPqcSslEngineFactory.class);
    private static final String JSSE_PROVIDER = "BCJSSE";
    private static final String PQC_HYBRID_GROUP = "X25519MLKEM768";

    private static final String[] PRIORITISED_GROUPS = {
            PQC_HYBRID_GROUP,
            "secp256r1",
            "secp384r1",
            "secp521r1"
    };

    private static final String REQUIRE_PQC_CONFIG = "ssl.pqc.require";

    private SSLContext sslContext;
    private KeyStore keyStore;
    private KeyStore trustStore;
    private boolean requirePqc = true;

    /* ------------------------------------------------------------------ */
    /*  Configurable                                                       */
    /* ------------------------------------------------------------------ */

    @Override
    public void configure(Map<String, ?> configs) {
        this.requirePqc = !"false".equalsIgnoreCase(stringValue(configs, REQUIRE_PQC_CONFIG));
        try {
            this.trustStore = loadStore(configs, "ssl.truststore.location", "ssl.truststore.password");
            this.keyStore = loadStore(configs, "ssl.keystore.location", "ssl.keystore.password");

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            String keyPassword = stringValue(configs, "ssl.key.password");
            kmf.init(keyStore, keyPassword != null ? keyPassword.toCharArray() : null);

            sslContext = createSslContext();
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

            verifyPqcSupport();

            log.info("PQC SslEngineFactory initialised (TLSv1.3, provider: {}, target group: {}, pqc-required: {})",
                    sslContext.getProvider().getName(), PQC_HYBRID_GROUP, requirePqc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise PQC SSLContext from Kafka config", e);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  SslEngineFactory — Kafka 3.7+ contract                            */
    /* ------------------------------------------------------------------ */

    @Override
    public SSLEngine createClientSslEngine(String peerHost, int peerPort,
                                           String endpointIdentification) {
        SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);
        engine.setUseClientMode(true);
        if (endpointIdentification != null && !endpointIdentification.isEmpty()) {
            SSLParameters params = engine.getSSLParameters();
            params.setEndpointIdentificationAlgorithm(endpointIdentification);
            engine.setSSLParameters(params);
        }
        applyPqcGroups(engine);
        return engine;
    }

    @Override
    public SSLEngine createServerSslEngine(String peerHost, int peerPort) {
        SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);
        engine.setUseClientMode(false);
        applyPqcGroups(engine);
        return engine;
    }

    @Override
    public boolean shouldBeRebuilt(Map<String, Object> nextConfigs) {
        return false;
    }

    @Override
    public Set<String> reconfigurableConfigs() {
        return Set.of();
    }

    @Override
    public KeyStore keystore() {
        return keyStore;
    }

    @Override
    public KeyStore truststore() {
        return trustStore;
    }

    @Override
    public void close() throws IOException {
        // no resources to release
    }

    /* ------------------------------------------------------------------ */
    /*  Internal helpers                                                    */
    /* ------------------------------------------------------------------ */

    private SSLContext createSslContext() throws Exception {
        if (Security.getProvider(JSSE_PROVIDER) == null) {
            if (requirePqc) {
                throw new IllegalStateException(
                        JSSE_PROVIDER + " provider is not registered. "
                                + "Post-quantum TLS requires Bouncy Castle JSSE. "
                                + "Add bcprov-jdk18on + bctls-jdk18on to the classpath "
                                + "or set ssl.pqc.require=false to allow classical-only TLS.");
            }
            log.warn("{} provider is not registered. PQC TLS will NOT be negotiated.", JSSE_PROVIDER);
            return SSLContext.getInstance("TLSv1.3");
        }
        return SSLContext.getInstance("TLSv1.3", JSSE_PROVIDER);
    }

    private void verifyPqcSupport() {
        String[] groups = sslContext.getSupportedSSLParameters().getNamedGroups();
        boolean supported = groups != null && Arrays.asList(groups).contains(PQC_HYBRID_GROUP);
        if (!supported && requirePqc) {
            throw new IllegalStateException(
                    PQC_HYBRID_GROUP + " is not supported by provider " + sslContext.getProvider().getName()
                            + ". Supported groups: " + (groups != null ? Arrays.toString(groups) : "none")
                            + ". Upgrade Bouncy Castle to 1.81+ or set ssl.pqc.require=false.");
        }
        if (!supported) {
            log.warn("{} is NOT supported by {} — connections will use classical ECDH only.",
                    PQC_HYBRID_GROUP, sslContext.getProvider().getName());
        } else {
            log.info("Post-quantum group {} confirmed available via {}",
                    PQC_HYBRID_GROUP, sslContext.getProvider().getName());
        }
    }

    private void applyPqcGroups(SSLEngine engine) {
        SSLParameters params = engine.getSSLParameters();
        params.setNamedGroups(PRIORITISED_GROUPS);
        engine.setSSLParameters(params);
    }

    private KeyStore loadStore(Map<String, ?> configs, String locationKey,
                               String passwordKey) throws Exception {
        String location = stringValue(configs, locationKey);
        String password = stringValue(configs, passwordKey);
        if (location == null || location.isBlank()) {
            return null;
        }
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = Files.newInputStream(Path.of(location))) {
            ks.load(in, password != null ? password.toCharArray() : null);
        }
        return ks;
    }

    private static String stringValue(Map<String, ?> configs, String key) {
        Object v = configs.get(key);
        return v != null ? v.toString() : null;
    }
}
