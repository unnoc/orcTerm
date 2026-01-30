$KEYSTORE_DIR = "..\keystores"
$KEYSTORE_FILE = "$KEYSTORE_DIR\release.jks"
$ALIAS = "orcterm"
$PASS = "android"

if (!(Test-Path $KEYSTORE_DIR)) {
    New-Item -ItemType Directory -Force -Path $KEYSTORE_DIR
}

if (Test-Path $KEYSTORE_FILE) {
    Write-Host "Keystore already exists at $KEYSTORE_FILE"
} else {
    Write-Host "Generating new release keystore..."
    & keytool -genkeypair `
        -alias $ALIAS `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -keystore $KEYSTORE_FILE `
        -storepass $PASS `
        -keypass $PASS `
        -dname "CN=OrcTerm, OU=Mobile, O=OrcTerm, L=City, ST=State, C=US"
    
    Write-Host "Keystore generated."
}

Write-Host "Keystore Path: $KEYSTORE_FILE"
Write-Host "Alias: $ALIAS"
Write-Host "Password: $PASS"
