#!/usr/bin/env bash
# ---------------------------------------------------------------
# Generate a self-signed CA, broker keystore, and client
# truststore / keystore for Kafka TLS + PQC in Docker Compose.
#
# Output directory: $CERT_DIR (default: ./certs)
# All passwords are read from environment variables or use
# the single $STORE_PASSWORD (default: changeit).
# ---------------------------------------------------------------
set -euo pipefail

CERT_DIR="${CERT_DIR:-$(dirname "$0")/certs}"
STORE_PASSWORD="${STORE_PASSWORD:-changeit}"
CA_VALIDITY="${CA_VALIDITY:-3650}"
CERT_VALIDITY="${CERT_VALIDITY:-825}"
BROKER_CN="${BROKER_CN:-kafka}"

mkdir -p "$CERT_DIR"

echo ">>> Generating PulseHome TLS certificates in $CERT_DIR"

# ---- 1. Certificate Authority (CA) ----
if [ ! -f "$CERT_DIR/ca.p12" ]; then
  keytool -genkeypair \
    -alias ca \
    -keyalg EC -groupname secp256r1 \
    -dname "CN=PulseHome CA,O=PulseHome" \
    -validity "$CA_VALIDITY" \
    -storetype PKCS12 \
    -keystore "$CERT_DIR/ca.p12" \
    -storepass "$STORE_PASSWORD" \
    -ext bc:c
  keytool -exportcert \
    -alias ca \
    -keystore "$CERT_DIR/ca.p12" \
    -storepass "$STORE_PASSWORD" \
    -rfc -file "$CERT_DIR/ca.pem"
  echo "    CA created"
else
  echo "    CA already exists, skipping"
fi

# ---- 2. Kafka broker keystore ----
if [ ! -f "$CERT_DIR/kafka.keystore.p12" ]; then
  keytool -genkeypair \
    -alias kafka \
    -keyalg EC -groupname secp256r1 \
    -dname "CN=$BROKER_CN,O=PulseHome" \
    -validity "$CERT_VALIDITY" \
    -storetype PKCS12 \
    -keystore "$CERT_DIR/kafka.keystore.p12" \
    -storepass "$STORE_PASSWORD" \
    -ext "SAN=dns:$BROKER_CN,dns:localhost"

  # Sign broker cert with CA
  keytool -certreq \
    -alias kafka \
    -keystore "$CERT_DIR/kafka.keystore.p12" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/kafka.csr"

  keytool -gencert \
    -alias ca \
    -keystore "$CERT_DIR/ca.p12" \
    -storepass "$STORE_PASSWORD" \
    -infile "$CERT_DIR/kafka.csr" \
    -outfile "$CERT_DIR/kafka-signed.pem" \
    -validity "$CERT_VALIDITY" \
    -ext "SAN=dns:$BROKER_CN,dns:localhost" \
    -rfc

  keytool -importcert -alias ca -noprompt \
    -keystore "$CERT_DIR/kafka.keystore.p12" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/ca.pem"

  keytool -importcert -alias kafka -noprompt \
    -keystore "$CERT_DIR/kafka.keystore.p12" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/kafka-signed.pem"

  echo "    Broker keystore created"
else
  echo "    Broker keystore already exists, skipping"
fi

# ---- 3. Client keystore (shared by collector/aggregator/analyzer) ----
if [ ! -f "$CERT_DIR/client.keystore.p12" ]; then
  keytool -genkeypair \
    -alias client \
    -keyalg EC -groupname secp256r1 \
    -dname "CN=pulsehome-client,O=PulseHome" \
    -validity "$CERT_VALIDITY" \
    -storetype PKCS12 \
    -keystore "$CERT_DIR/client.keystore.p12" \
    -storepass "$STORE_PASSWORD"

  keytool -certreq \
    -alias client \
    -keystore "$CERT_DIR/client.keystore.p12" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/client.csr"

  keytool -gencert \
    -alias ca \
    -keystore "$CERT_DIR/ca.p12" \
    -storepass "$STORE_PASSWORD" \
    -infile "$CERT_DIR/client.csr" \
    -outfile "$CERT_DIR/client-signed.pem" \
    -validity "$CERT_VALIDITY" \
    -rfc

  keytool -importcert -alias ca -noprompt \
    -keystore "$CERT_DIR/client.keystore.p12" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/ca.pem"

  keytool -importcert -alias client -noprompt \
    -keystore "$CERT_DIR/client.keystore.p12" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/client-signed.pem"

  echo "    Client keystore created"
else
  echo "    Client keystore already exists, skipping"
fi

# ---- 4. Shared truststore (CA only) ----
if [ ! -f "$CERT_DIR/truststore.p12" ]; then
  keytool -importcert -alias ca -noprompt \
    -storetype PKCS12 \
    -keystore "$CERT_DIR/truststore.p12" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/ca.pem"
  echo "    Truststore created"
else
  echo "    Truststore already exists, skipping"
fi

# Clean up CSR / signed cert intermediaries
rm -f "$CERT_DIR"/*.csr "$CERT_DIR"/*-signed.pem

echo ">>> TLS certificates ready in $CERT_DIR"
echo "    Keystore/truststore password: $STORE_PASSWORD"
