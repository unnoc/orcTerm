$ErrorActionPreference = "Stop"
$WORK_DIR = "temp_build"
$DEST_CPP = "sshlib/src/main/cpp"
$OUT_DIR = "docs/scripts/libs/android"

Write-Host "Installing Libssh2..."
$libssh2_src = "$WORK_DIR/libssh2-1.11.0/src"
$libssh2_inc = "$WORK_DIR/libssh2-1.11.0/include"

if (Test-Path $libssh2_src) {
    New-Item -ItemType Directory -Force -Path "$DEST_CPP/libssh2_src" | Out-Null
    Copy-Item -Recurse -Force "$libssh2_src" "$DEST_CPP/libssh2_src/"
    Copy-Item -Recurse -Force "$libssh2_inc" "$DEST_CPP/libssh2_src/"
} else {
    Write-Error "Libssh2 source not found at $libssh2_src"
}

Write-Host "Installing OpenSSL..."
$openssl_repo = "$WORK_DIR/OpenSSL-for-Android-Prebuilt-master"
$openssl_root = Get-ChildItem "$openssl_repo" | Where-Object { $_.Name -like "openssl-*" } | Sort-Object Name -Descending | Select-Object -First 1
if (-not $openssl_root) {
    Write-Error "OpenSSL 目录未找到"
}
$openssl_root = $openssl_root.FullName

$openssl_inc = "$openssl_root/include/openssl"
if (Test-Path $openssl_inc) {
    New-Item -ItemType Directory -Force -Path "$DEST_CPP/include" | Out-Null
    Copy-Item -Recurse -Force "$openssl_inc" "$DEST_CPP/include/"
} else {
    Write-Warning "OpenSSL headers not found at $openssl_inc. Trying parent..."
    $openssl_inc_parent = "$openssl_root/include"
    if (Test-Path $openssl_inc_parent) {
         Copy-Item -Recurse -Force "$openssl_inc_parent/*" "$DEST_CPP/include/"
    }
}

function Install-Lib($archSource, $archDest) {
    $srcPath = "$openssl_root/$archSource/lib"
    $destPath = "$DEST_CPP/libs/$archDest"
    $outPath = "$OUT_DIR/$archDest"
    
    if (Test-Path $srcPath) {
        New-Item -ItemType Directory -Force -Path $destPath | Out-Null
        New-Item -ItemType Directory -Force -Path $outPath | Out-Null
        Copy-Item "$srcPath/libcrypto.a" "$destPath/"
        Copy-Item "$srcPath/libssl.a" "$destPath/"
        Copy-Item "$srcPath/libcrypto.a" "$outPath/"
        Copy-Item "$srcPath/libssl.a" "$outPath/"
        Write-Host "Installed $archDest libs."
    } else {
        Write-Warning "Libs not found for $archSource at $srcPath"
    }
}

Install-Lib "arm64-v8a" "arm64-v8a"
Install-Lib "x86_64" "x86_64"

Write-Host "Cleaning up..."
Remove-Item -Recurse -Force $WORK_DIR

Write-Host "Success! Dependencies installed."
