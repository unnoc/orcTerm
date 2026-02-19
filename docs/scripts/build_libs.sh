#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORK_DIR="$SCRIPT_DIR/temp_build"
CPP_DIR="$ROOT_DIR/sshlib/src/main/cpp"
OUT_DIR="$SCRIPT_DIR/libs/android"
ANDROID_ABIS="${ANDROID_ABIS:-arm64-v8a x86_64}"
ANDROID_NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}}"

log() {
  printf "[android-build] %s\n" "$1"
}

if [ -z "${ANDROID_NDK_ROOT:-}" ]; then
  if [ -z "$ANDROID_NDK" ]; then
    log "请设置 ANDROID_NDK_ROOT（或 ANDROID_NDK_HOME / ANDROID_NDK）"
    exit 1
  fi
fi
export ANDROID_NDK_ROOT="$ANDROID_NDK"

find_first_file() {
  local base="$1"
  shift
  find "$base" "$@" -print -quit 2>/dev/null || true
}

find_abi_lib() {
  local abi="$1"
  shift
  local name=""
  local candidate=""
  for name in "$@"; do
    candidate="$(find_first_file "$WORK_DIR" -path "*/${abi}/*" -name "$name")"
    if [ -n "$candidate" ]; then
      printf "%s" "$candidate"
      return 0
    fi
  done
  exit 1
}
command -v git >/dev/null 2>&1 || { log "缺少 git"; exit 2; }

rm -rf "$WORK_DIR"
git clone https://github.com/egorovandreyrm/libssh_android_build_scripts.git "$WORK_DIR"
cd "$WORK_DIR"
if [ ! -x "./build_all_abi.sh" ]; then
  log "缺少 build_all_abi.sh"
  exit 2
fi
./build_all_abi.sh

OPENSSL_SSL_HEADER="$(find_first_file "$WORK_DIR" -path "*/include/openssl/ssl.h")"
LIBSSH2_HEADER="$(find_first_file "$WORK_DIR" -path "*/include/libssh2.h")"
if [ -z "$OPENSSL_SSL_HEADER" ] || [ -z "$LIBSSH2_HEADER" ]; then
  log "未找到 include 目录"
  exit 3
fi
OPENSSL_INCLUDE="$(dirname "$OPENSSL_SSL_HEADER")"
LIBSSH2_INCLUDE="$(dirname "$LIBSSH2_HEADER")"

mkdir -p "$CPP_DIR/libs" "$CPP_DIR/include" "$OUT_DIR"
cp -r "$(dirname "$OPENSSL_INCLUDE")/"* "$CPP_DIR/include/"
find "$LIBSSH2_INCLUDE" -maxdepth 1 -type f -name "libssh2*.h" -exec cp -f {} "$CPP_DIR/include/" \;

FOUND_ANY=0
for ABI in $ANDROID_ABIS; do
  LIBSSH2_A="$(find_abi_lib "$ABI" "libssh2.a" "libssh2_static.a" || true)"
  SSL_A="$(find_abi_lib "$ABI" "libssl.a" || true)"
  CRYPTO_A="$(find_abi_lib "$ABI" "libcrypto.a" || true)"
  if [ -z "$LIBSSH2_A" ]; then
    log "未找到 libssh2 静态库 (ABI=$ABI)"
    exit 4
  fi
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
  LIBSSH2_SO="$(find_abi_lib "$ABI" "libssh2*.so" || true)"
  SSL_SO="$(find_abi_lib "$ABI" "libssl*.so" || true)"
  CRYPTO_SO="$(find_abi_lib "$ABI" "libcrypto*.so" || true)"
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
