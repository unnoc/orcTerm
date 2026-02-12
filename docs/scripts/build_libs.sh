#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORK_DIR="$SCRIPT_DIR/temp_build"
CPP_DIR="$ROOT_DIR/sshlib/src/main/cpp"
OUT_DIR="$SCRIPT_DIR/libs/android"
ANDROID_ABIS="${ANDROID_ABIS:-arm64-v8a x86_64}"

log() {
  printf "[android-build] %s\n" "$1"
}

if [ -z "${ANDROID_NDK_ROOT:-}" ]; then
  log "请设置 ANDROID_NDK_ROOT"
  exit 1
fi
command -v git >/dev/null 2>&1 || { log "缺少 git"; exit 2; }

rm -rf "$WORK_DIR"
git clone https://github.com/egorovandreyrm/libssh_android_build_scripts.git "$WORK_DIR"
cd "$WORK_DIR"
./build_all_abi.sh

OPENSSL_INCLUDE="$(find "$WORK_DIR" -path "*/include/openssl/ssl.h" -print -quit | xargs -I{} dirname {})"
LIBSSH2_INCLUDE="$(find "$WORK_DIR" -name libssh2.h -print -quit | xargs -I{} dirname {})"
if [ -z "$OPENSSL_INCLUDE" ] || [ -z "$LIBSSH2_INCLUDE" ]; then
  log "未找到 include 目录"
  exit 3
fi

mkdir -p "$CPP_DIR/libs" "$CPP_DIR/include" "$OUT_DIR"
cp -r "$(dirname "$OPENSSL_INCLUDE")/"* "$CPP_DIR/include/"
cp -r "$LIBSSH2_INCLUDE/"* "$CPP_DIR/include/"

FOUND_ANY=0
for ABI in $ANDROID_ABIS; do
  LIBSSH2_A="$(find "$WORK_DIR" -path "*/${ABI}*/libssh2.a" -print -quit || true)"
  if [ -z "$LIBSSH2_A" ]; then
    log "未找到 libssh2.a (ABI=$ABI)"
    exit 4
  fi
  BASE_DIR="$(dirname "$LIBSSH2_A")"
  SSL_A="${BASE_DIR}/libssl.a"
  CRYPTO_A="${BASE_DIR}/libcrypto.a"
  if [ ! -f "$SSL_A" ] || [ ! -f "$CRYPTO_A" ]; then
    log "未找到 OpenSSL 静态库 (ABI=$ABI)"
    exit 5
  fi
  mkdir -p "$CPP_DIR/libs/$ABI" "$OUT_DIR/$ABI"
  cp -f "$LIBSSH2_A" "$CPP_DIR/libs/$ABI/"
  cp -f "$SSL_A" "$CPP_DIR/libs/$ABI/"
  cp -f "$CRYPTO_A" "$CPP_DIR/libs/$ABI/"
  cp -f "$LIBSSH2_A" "$OUT_DIR/$ABI/"
  cp -f "$SSL_A" "$OUT_DIR/$ABI/"
  cp -f "$CRYPTO_A" "$OUT_DIR/$ABI/"
  LIBSSH2_SO="$(find "$BASE_DIR" -maxdepth 1 -name "libssh2*.so" -print -quit || true)"
  SSL_SO="$(find "$BASE_DIR" -maxdepth 1 -name "libssl*.so" -print -quit || true)"
  CRYPTO_SO="$(find "$BASE_DIR" -maxdepth 1 -name "libcrypto*.so" -print -quit || true)"
  if [ -n "$LIBSSH2_SO" ]; then
    cp -f "$LIBSSH2_SO" "$OUT_DIR/$ABI/"
  fi
  if [ -n "$SSL_SO" ]; then
    cp -f "$SSL_SO" "$OUT_DIR/$ABI/"
  fi
  if [ -n "$CRYPTO_SO" ]; then
    cp -f "$CRYPTO_SO" "$OUT_DIR/$ABI/"
  fi
  FOUND_ANY=1
done

if [ "$FOUND_ANY" -ne 1 ]; then
  log "未检测到 .a 产物"
  exit 6
fi

log "完成：OpenSSL 与 libssh2 已写入 sshlib/src/main/cpp，并归档到 $OUT_DIR"
