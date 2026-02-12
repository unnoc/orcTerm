$ErrorActionPreference = "Stop"

$LIBSSH2_URL = "https://github.com/libssh2/libssh2/releases/download/libssh2-1.11.0/libssh2-1.11.0.zip"
$OPENSSL_REPO_ZIP = "https://github.com/PurpleI2P/OpenSSL-for-Android-Prebuilt/archive/refs/heads/master.zip"

$WORK_DIR = "temp_build"
$DEST_CPP = "sshlib/src/main/cpp"
$OUT_DIR = "docs/scripts/libs/android"

if (-not (Test-Path $WORK_DIR)) { New-Item -ItemType Directory -Force -Path $WORK_DIR | Out-Null }
if (-not (Test-Path "$DEST_CPP/libs/arm64-v8a")) { New-Item -ItemType Directory -Force -Path "$DEST_CPP/libs/arm64-v8a" | Out-Null }
if (-not (Test-Path "$DEST_CPP/libs/x86_64")) { New-Item -ItemType Directory -Force -Path "$DEST_CPP/libs/x86_64" | Out-Null }
if (-not (Test-Path "$DEST_CPP/include")) { New-Item -ItemType Directory -Force -Path "$DEST_CPP/include" | Out-Null }
if (-not (Test-Path "$DEST_CPP/libssh2_src")) { New-Item -ItemType Directory -Force -Path "$DEST_CPP/libssh2_src" | Out-Null }

Write-Host "Downloading Libssh2..."
Invoke-WebRequest -Uri $LIBSSH2_URL -OutFile "$WORK_DIR/libssh2.zip"
Expand-Archive -Path "$WORK_DIR/libssh2.zip" -DestinationPath "$WORK_DIR" -Force

Write-Host "Setting up Libssh2 source..."
if (Test-Path "$WORK_DIR/libssh2-1.11.0/src") {
    Copy-Item -Recurse -Force "$WORK_DIR/libssh2-1.11.0/src/*" "$DEST_CPP/libssh2_src/"
    Copy-Item -Recurse -Force "$WORK_DIR/libssh2-1.11.0/include/*" "$DEST_CPP/libssh2_src/"
} else {
    Write-Error "Libssh2 source not found in extracted zip!"
}

Write-Host "Downloading OpenSSL Prebuilts..."
Invoke-WebRequest -Uri $OPENSSL_REPO_ZIP -OutFile "$WORK_DIR/openssl.zip"
Expand-Archive -Path "$WORK_DIR/openssl.zip" -DestinationPath "$WORK_DIR" -Force

Write-Host "Inspecting OpenSSL directory structure..."
$openssl_root = Get-ChildItem "$WORK_DIR" | Where-Object { $_.Name -like "OpenSSL-for-Android-Prebuilt-*" } | Select-Object -First 1
$openssl_root_path = $openssl_root.FullName
Write-Host "OpenSSL Root: $openssl_root_path"

function Copy-OpenSSL($archSource, $archDest) {
    $version_dir = Get-ChildItem "$openssl_root_path" | Where-Object { $_.Name -like "openssl-*" } | Sort-Object Name -Descending | Select-Object -First 1
    if ($version_dir) {
        $candidates = @(
            (Join-Path $version_dir.FullName $archSource),
            (Join-Path $version_dir.FullName "clang/$archSource")
        )
        foreach ($base in $candidates) {
            if (Test-Path $base) {
                Write-Host "Found OpenSSL at: $base"
                Copy-Item "$base/lib/libcrypto.a" "$DEST_CPP/libs/$archDest/" -ErrorAction SilentlyContinue
                Copy-Item "$base/lib/libssl.a" "$DEST_CPP/libs/$archDest/" -ErrorAction SilentlyContinue
                if (-not (Test-Path "$OUT_DIR/$archDest")) { New-Item -ItemType Directory -Force -Path "$OUT_DIR/$archDest" | Out-Null }
                Copy-Item "$base/lib/libcrypto.a" "$OUT_DIR/$archDest/" -ErrorAction SilentlyContinue
                Copy-Item "$base/lib/libssl.a" "$OUT_DIR/$archDest/" -ErrorAction SilentlyContinue
                return
            }
        }
    }
    Write-Warning "OpenSSL source path not found for $archSource"
}

Write-Host "Copying OpenSSL libraries..."
Copy-OpenSSL "arm64-v8a" "arm64-v8a"
Copy-OpenSSL "x86_64" "x86_64"

Write-Host "Copying OpenSSL headers..."
$version_dir = Get-ChildItem "$openssl_root_path" | Where-Object { $_.Name -like "openssl-*" } | Sort-Object Name -Descending | Select-Object -First 1
if ($version_dir) {
    $include_path = Join-Path $version_dir.FullName "include/openssl"
    if (Test-Path $include_path) {
        Copy-Item -Recurse -Force "$include_path" "$DEST_CPP/include/"
    }
}

Remove-Item -Recurse -Force $WORK_DIR
Write-Host "Done! Dependencies ready."
