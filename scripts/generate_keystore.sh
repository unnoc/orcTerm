#!/bin/bash

KEYSTORE_DIR="../keystores"
KEYSTORE_FILE="$KEYSTORE_DIR/release.jks"
ALIAS="orcterm"
PASS="android" # Default password for demo purposes

mkdir -p "$KEYSTORE_DIR"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "Keystore already exists at $KEYSTORE_FILE"
else
    echo "Generating new release keystore..."
    keytool -genkeypair \
        -alias "$ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -keystore "$KEYSTORE_FILE" \
        -storepass "$PASS" \
        -keypass "$PASS" \
        -dname "CN=OrcTerm, OU=Mobile, O=OrcTerm, L=City, ST=State, C=US"
    
    echo "Keystore generated."
fi

echo "Keystore Path: $KEYSTORE_FILE"
echo "Alias: $ALIAS"
echo "Password: $PASS"
