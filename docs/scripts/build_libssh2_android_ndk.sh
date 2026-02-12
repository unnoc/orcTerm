#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SRC_DIR="$ROOT_DIR/sshlib/src/main/cpp/libssh2_official"
BUILD_ROOT="$ROOT_DIR/sshlib/src/main/cpp/build_libssh2_android"
OUT_ROOT="$SCRIPT_DIR/libs/android"
OPENSSL_ROOT="${OPENSSL_ROOT:-$ROOT_DIR/sshlib/src/main/cpp}"
ANDROID_ABIS="${ANDROID_ABIS:-arm64-v8a;x86_64}"

log() {
  printf "[android-ndk] %s\n" "$1"
}

command -v cmake >/dev/null 2>&1 || { log "缺少 cmake"; exit 2; }
command -v git >/dev/null 2>&1 || { log "缺少 git"; exit 3; }

NDK_ROOT="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
if [ -z "${NDK_ROOT}" ]; then
  log "未检测到 ANDROID_NDK_ROOT 或 ANDROID_NDK_HOME"
  exit 4
fi
if [ ! -f "$NDK_ROOT/build/cmake/android.toolchain.cmake" ]; then
  log "NDK 路径无效：未找到 android.toolchain.cmake"
  exit 5
fi

if [ -d "$SRC_DIR/.git" ] || [ -f "$SRC_DIR/.git" ]; then
  log "更新 libssh2 源码"
  git -C "$SRC_DIR" fetch --all
  git -C "$SRC_DIR" pull --rebase
else
  log "拉取 libssh2 源码"
  mkdir -p "$(dirname "$SRC_DIR")"
  git clone https://github.com/libssh2/libssh2.git "$SRC_DIR"
fi

IFS=';' read -r -a ABI_LIST <<< "$ANDROID_ABIS"
for ABI in "${ABI_LIST[@]}"; do
  OPENSSL_LIB_DIR="$OPENSSL_ROOT/libs/$ABI"
  OPENSSL_INCLUDE_DIR="$OPENSSL_ROOT/include"
  if [ ! -f "$OPENSSL_LIB_DIR/libssl.a" ] || [ ! -f "$OPENSSL_LIB_DIR/libcrypto.a" ]; then
    log "缺少 OpenSSL 静态库：$OPENSSL_LIB_DIR"
    exit 6
  fi
  if [ ! -f "$OPENSSL_INCLUDE_DIR/openssl/ssl.h" ]; then
    log "缺少 OpenSSL 头文件：$OPENSSL_INCLUDE_DIR"
    exit 7
  fi

  BUILD_DIR="$BUILD_ROOT/$ABI"
  OUT_DIR="$OUT_ROOT/$ABI"
  log "开始构建 ABI=$ABI"
  cmake -S "$SRC_DIR" -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-26 \
    -DBUILD_SHARED_LIBS=ON \
    -DBUILD_STATIC_LIBS=ON \
    -DBUILD_EXAMPLES=OFF \
    -DBUILD_TESTING=OFF \
    -DLIBSSH2_BUILD_DOCS=OFF \
    -DLIBSSH2_DISABLE_INSTALL=ON \
    -DLIBSSH2_USE_PKGCONFIG=OFF \
    -DCRYPTO_BACKEND=OpenSSL \
    -DOPENSSL_ROOT_DIR="$OPENSSL_ROOT" \
    -DOPENSSL_INCLUDE_DIR="$OPENSSL_INCLUDE_DIR" \
    -DOPENSSL_SSL_LIBRARY="$OPENSSL_LIB_DIR/libssl.a" \
    -DOPENSSL_CRYPTO_LIBRARY="$OPENSSL_LIB_DIR/libcrypto.a"
  cmake --build "$BUILD_DIR" --config Release

  mkdir -p "$OUT_DIR"
  LIB_A="$(find "$BUILD_DIR" -name "libssh2.a" -type f | head -n 1 || true)"
  LIB_SO="$(find "$BUILD_DIR" -name "libssh2*.so" -type f | head -n 1 || true)"
  if [ -z "$LIB_A" ]; then
    log "未找到 libssh2.a (ABI=$ABI)"
    exit 8
  fi
  if [ -z "$LIB_SO" ]; then
    log "未找到 libssh2.so (ABI=$ABI)"
    exit 9
  fi
  cp -f "$LIB_A" "$OUT_DIR/"
  cp -f "$LIB_SO" "$OUT_DIR/"
done

log "构建完成，产物已输出到 $OUT_ROOT"
