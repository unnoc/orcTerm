$ErrorActionPreference = "Stop"
$WORK_DIR = "temp_build"

if (Test-Path $WORK_DIR) { Remove-Item -Recurse -Force $WORK_DIR }
New-Item -ItemType Directory -Force -Path $WORK_DIR | Out-Null

$LIBSSH2_URL = "https://github.com/libssh2/libssh2/releases/download/libssh2-1.11.0/libssh2-1.11.0.zip"
Write-Host "Downloading Libssh2..."
Invoke-WebRequest -Uri $LIBSSH2_URL -OutFile "$WORK_DIR/libssh2.zip"
Expand-Archive -Path "$WORK_DIR/libssh2.zip" -DestinationPath "$WORK_DIR" -Force

$OPENSSL_URL = "https://github.com/PurpleI2P/OpenSSL-for-Android-Prebuilt/archive/refs/heads/master.zip"
Write-Host "Downloading OpenSSL..."
Invoke-WebRequest -Uri $OPENSSL_URL -OutFile "$WORK_DIR/openssl.zip"
Expand-Archive -Path "$WORK_DIR/openssl.zip" -DestinationPath "$WORK_DIR" -Force

Write-Host "Listing Libssh2 structure:"
Get-ChildItem -Recurse -Depth 2 "$WORK_DIR/libssh2*" | Select-Object FullName

Write-Host "`nListing OpenSSL structure:"
Get-ChildItem -Recurse -Depth 4 "$WORK_DIR/OpenSSL-for-Android-Prebuilt-master" | Where-Object { $_.PSIsContainer } | Select-Object FullName
