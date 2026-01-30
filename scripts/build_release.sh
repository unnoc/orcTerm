#!/bin/bash

# Build Release APK and AAB for Google Play and FOSS flavors

GRADLE_CMD="../gradlew"

if [ ! -f "$GRADLE_CMD" ]; then
    echo "Warning: Gradle Wrapper (gradlew) not found."
    if command -v gradle &> /dev/null; then
        echo "Using global gradle..."
        GRADLE_CMD="gradle"
    else
        echo "Error: Neither gradlew nor global gradle found."
        exit 1
    fi
fi

echo "Cleaning project..."
$GRADLE_CMD clean

echo "Building Release (Google Play)..."
$GRADLE_CMD assembleGooglePlayRelease bundleGooglePlayRelease

echo "Building Release (FOSS)..."
$GRADLE_CMD assembleFossRelease bundleFossRelease

echo "Build complete. Outputs are in app/build/outputs/apk/ and app/build/outputs/bundle/"
