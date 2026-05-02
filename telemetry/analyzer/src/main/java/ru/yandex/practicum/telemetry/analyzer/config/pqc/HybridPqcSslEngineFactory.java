package ru.yandex.practicum.telemetry.analyzer.config.pqc;

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

import org.apache.kafka.common.config.types.Password;
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
 * while maintaining full backward-compatibility.</p>
 *
 * <p>Compatible with Kafka 3.7+ ({@code createClientSslEngine} /
 * {@code createServerSslEngine} contract).</p>
 */
public class HybridPqcSslEngineFactory implements SslEngineFactory {

    private static final Logger log = LoggerFactory.getLogger(HybridPqcSslEngineFactory.class);
    private static final String JSSE_PROVIDER = "BCJSSE";
    private static final String PQC_HYBRID_GROUP = "X25519MLKEM768";

    private static final String[] CLASSICAL_GROUPS = {
            "x25519",
            "secp256r1",
            "secp384r1",
            "secp521r1"
    };

    private static final String[] PRIORITISED_GROUPS = {
            PQC_HYBRID_GROUP,
            "x25519",
            "secp256r1",
            "secp384r1",
            "secp521r1"
    };

    private static final String REQUIRE_PQC_CONFIG = "ssl.pqc.require";

    private SSLContext sslContext;
    private KeyStore keyStore;
    private KeyStore trustStore;
    private boolean requirePqc = true;
    private String[] enabledGroups = new String[0];

    @Override
    public void configure(Map<String, ?> configs) {
        this.requirePqc = !"false".equalsIgnoreCase(stringValue(configs, REQUIRE_PQC_CONFIG));
        try {
            this.trustStore = loadStore(configs,
                    "ssl.truststore.location", "ssl.truststore.password", "ssl.truststore.type");
            this.keyStore = loadStore(configs,
                    "ssl.keystore.location", "ssl.keystore.password", "ssl.keystore.type");

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

        SSLContext probeContext = SSLContext.getInstance("TLSv1.3", JSSE_PROVIDER);
        probeContext.init(null, null, null);
        String[] bcGroups = probeContext.getSupportedSSLParameters().getNamedGroups();
        if (supportsPqc(bcGroups)) {
            return SSLContext.getInstance("TLSv1.3", JSSE_PROVIDER);
        }

        if (requirePqc) {
            throw new IllegalStateException(
                    PQC_HYBRID_GROUP + " is not supported by provider " + JSSE_PROVIDER
                            + ". Supported groups: " + (bcGroups != null ? Arrays.toString(bcGroups) : "none")
                            + ". Upgrade Bouncy Castle or set ssl.pqc.require=false.");
        }

        log.warn("{} is not supported by {}; using default JSSE for classical TLS.",
                PQC_HYBRID_GROUP, JSSE_PROVIDER);
        return SSLContext.getInstance("TLSv1.3");
    }

    private void verifyPqcSupport() {
        String[] groups = sslContext.getSupportedSSLParameters().getNamedGroups();
        boolean supported = supportsPqc(groups);
        if (!supported && requirePqc) {
            throw new IllegalStateException(
                    PQC_HYBRID_GROUP + " is not supported by provider " + sslContext.getProvider().getName()
                            + ". Supported groups: " + (groups != null ? Arrays.toString(groups) : "none")
                            + ". Upgrade Bouncy Castle to 1.81+ or set ssl.pqc.require=false.");
        }
        enabledGroups = selectEnabledGroups(groups, supported);
        if (!supported) {
            log.warn("{} is NOT supported by {} — connections will use classical ECDH only.",
                    PQC_HYBRID_GROUP, sslContext.getProvider().getName());
        } else {
            log.info("Post-quantum group {} confirmed available via {}",
                    PQC_HYBRID_GROUP, sslContext.getProvider().getName());
        }
    }

    private void applyPqcGroups(SSLEngine engine) {
        if (enabledGroups.length == 0) {
            return;
        }
        SSLParameters params = engine.getSSLParameters();
        params.setNamedGroups(enabledGroups);
        engine.setSSLParameters(params);
    }

    private static boolean supportsPqc(String[] groups) {
        return groups != null && Arrays.asList(groups).contains(PQC_HYBRID_GROUP);
    }

    private static String[] selectEnabledGroups(String[] supportedGroups, boolean includePqc) {
        if (supportedGroups == null || supportedGroups.length == 0) {
            return new String[0];
        }
        var supported = Arrays.asList(supportedGroups);
        return Arrays.stream(includePqc ? PRIORITISED_GROUPS : CLASSICAL_GROUPS)
                .filter(supported::contains)
                .toArray(String[]::new);
    }

    private KeyStore loadStore(Map<String, ?> configs, String locationKey,
                               String passwordKey, String typeKey) throws Exception {
        String location = stringValue(configs, locationKey);
        String password = stringValue(configs, passwordKey);
        String type = stringValue(configs, typeKey);
        if (location == null || location.isBlank()) {
            return null;
        }
        String effectiveType = type == null || type.isBlank() ? KeyStore.getDefaultType() : type;
        KeyStore ks = KeyStore.getInstance(effectiveType);
        try (InputStream in = Files.newInputStream(Path.of(location))) {
            ks.load(in, password != null ? password.toCharArray() : null);
        }
        return ks;
    }

    private static String stringValue(Map<String, ?> configs, String key) {
        Object v = configs.get(key);
        if (v instanceof Password password) {
            return password.value();
        }
        return v != null ? v.toString() : null;
    }
}
