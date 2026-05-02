#!/usr/bin/env bash
# ---------------------------------------------------------------
# Generate a self-signed CA, broker keystore, and client
# truststore / keystore for Kafka TLS in Docker Compose.
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

CA_STORE="$CERT_DIR/ca.p12"
BROKER_STORE="$CERT_DIR/kafka.keystore.p12"
CLIENT_STORE="$CERT_DIR/client.keystore.p12"
TRUSTSTORE="$CERT_DIR/truststore.p12"
CA_CERT="$CERT_DIR/ca.pem"

mkdir -p "$CERT_DIR"

verify_existing_store() {
  local store_path="$1"
  local description="$2"

  if [ ! -f "$store_path" ]; then
    return
  fi

  if ! keytool -list \
    -storetype PKCS12 \
    -keystore "$store_path" \
    -storepass "$STORE_PASSWORD" >/dev/null 2>&1; then
    echo "ERROR: Existing $description at $store_path cannot be opened with STORE_PASSWORD." >&2
    echo "       Reuse the original TLS_STORE_PASSWORD or remove the PulseHome TLS certs volume." >&2
    exit 1
  fi
}

write_secret_file() {
  local path="$1"
  printf '%s\n' "$STORE_PASSWORD" > "$path"
  chmod 644 "$path"
}

write_kafka_files() {
  write_secret_file "$CERT_DIR/keystore-password.txt"
  write_secret_file "$CERT_DIR/key-password.txt"
  write_secret_file "$CERT_DIR/truststore-password.txt"

  cat > "$CERT_DIR/kafka-health.properties" <<EOF
security.protocol=SSL
ssl.truststore.location=/etc/kafka/secrets/truststore.p12
ssl.truststore.type=PKCS12
ssl.truststore.password=$STORE_PASSWORD
ssl.keystore.location=/etc/kafka/secrets/client.keystore.p12
ssl.keystore.type=PKCS12
ssl.keystore.password=$STORE_PASSWORD
ssl.key.password=$STORE_PASSWORD
EOF
  chmod 644 "$CERT_DIR/kafka-health.properties"
}

echo ">>> Generating PulseHome TLS certificates in $CERT_DIR"

# ---- 1. Certificate Authority (CA) ----
if [ ! -f "$CA_STORE" ]; then
  keytool -genkeypair \
    -alias ca \
    -keyalg EC -groupname secp256r1 \
    -dname "CN=PulseHome CA,O=PulseHome" \
    -validity "$CA_VALIDITY" \
    -storetype PKCS12 \
    -keystore "$CA_STORE" \
    -storepass "$STORE_PASSWORD" \
    -ext bc:c
  echo "    CA created"
else
  echo "    CA already exists, verifying"
fi

verify_existing_store "$CA_STORE" "CA keystore"
rm -f "$CA_CERT"
keytool -exportcert \
  -alias ca \
  -keystore "$CA_STORE" \
  -storepass "$STORE_PASSWORD" \
  -rfc -file "$CA_CERT" >/dev/null

# ---- 2. Kafka broker keystore ----
if [ ! -f "$BROKER_STORE" ]; then
  keytool -genkeypair \
    -alias kafka \
    -keyalg EC -groupname secp256r1 \
    -dname "CN=$BROKER_CN,O=PulseHome" \
    -validity "$CERT_VALIDITY" \
    -storetype PKCS12 \
    -keystore "$BROKER_STORE" \
    -storepass "$STORE_PASSWORD" \
    -ext "SAN=dns:$BROKER_CN,dns:localhost"

  keytool -certreq \
    -alias kafka \
    -keystore "$BROKER_STORE" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/kafka.csr"

  keytool -gencert \
    -alias ca \
    -keystore "$CA_STORE" \
    -storepass "$STORE_PASSWORD" \
    -infile "$CERT_DIR/kafka.csr" \
    -outfile "$CERT_DIR/kafka-signed.pem" \
    -validity "$CERT_VALIDITY" \
    -ext "SAN=dns:$BROKER_CN,dns:localhost" \
    -rfc

  keytool -importcert -alias ca -noprompt \
    -keystore "$BROKER_STORE" \
    -storepass "$STORE_PASSWORD" \
    -file "$CA_CERT"

  keytool -importcert -alias kafka -noprompt \
    -keystore "$BROKER_STORE" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/kafka-signed.pem"

  echo "    Broker keystore created"
else
  echo "    Broker keystore already exists, verifying"
fi
verify_existing_store "$BROKER_STORE" "Kafka broker keystore"

# ---- 3. Client keystore (shared by collector/aggregator/analyzer) ----
if [ ! -f "$CLIENT_STORE" ]; then
  keytool -genkeypair \
    -alias client \
    -keyalg EC -groupname secp256r1 \
    -dname "CN=pulsehome-client,O=PulseHome" \
    -validity "$CERT_VALIDITY" \
    -storetype PKCS12 \
    -keystore "$CLIENT_STORE" \
    -storepass "$STORE_PASSWORD"

  keytool -certreq \
    -alias client \
    -keystore "$CLIENT_STORE" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/client.csr"

  keytool -gencert \
    -alias ca \
    -keystore "$CA_STORE" \
    -storepass "$STORE_PASSWORD" \
    -infile "$CERT_DIR/client.csr" \
    -outfile "$CERT_DIR/client-signed.pem" \
    -validity "$CERT_VALIDITY" \
    -rfc

  keytool -importcert -alias ca -noprompt \
    -keystore "$CLIENT_STORE" \
    -storepass "$STORE_PASSWORD" \
    -file "$CA_CERT"

  keytool -importcert -alias client -noprompt \
    -keystore "$CLIENT_STORE" \
    -storepass "$STORE_PASSWORD" \
    -file "$CERT_DIR/client-signed.pem"

  echo "    Client keystore created"
else
  echo "    Client keystore already exists, verifying"
fi
verify_existing_store "$CLIENT_STORE" "client keystore"

# ---- 4. Shared truststore (CA only) ----
if [ ! -f "$TRUSTSTORE" ]; then
  keytool -importcert -alias ca -noprompt \
    -storetype PKCS12 \
    -keystore "$TRUSTSTORE" \
    -storepass "$STORE_PASSWORD" \
    -file "$CA_CERT"
  echo "    Truststore created"
else
  echo "    Truststore already exists, verifying"
fi
verify_existing_store "$TRUSTSTORE" "shared truststore"

write_kafka_files

# Clean up CSR / signed cert intermediaries
rm -f "$CERT_DIR"/*.csr "$CERT_DIR"/*-signed.pem

echo ">>> TLS certificates ready in $CERT_DIR"
echo "    Kafka credential files refreshed"
