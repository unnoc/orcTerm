#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SRC_DIR="$ROOT_DIR/sshlib/src/main/cpp/libssh2_official"
BUILD_DIR="$ROOT_DIR/sshlib/src/main/cpp/build_libssh2_macos"
OUT_DIR="$SCRIPT_DIR/libs/macos"

log() {
  printf "[macos] %s\n" "$1"
}

command -v xcode-select >/dev/null 2>&1 || { log "缺少 xcode-select"; exit 2; }
xcode-select -p >/dev/null 2>&1 || { log "未安装 Xcode 命令行工具"; exit 3; }
command -v git >/dev/null 2>&1 || { log "缺少 git"; exit 4; }
command -v cmake >/dev/null 2>&1 || { log "缺少 cmake"; exit 5; }
command -v make >/dev/null 2>&1 || { log "缺少 make"; exit 6; }

OPENSSL_ROOT_DIR="${OPENSSL_ROOT_DIR:-}"
if [ -z "${OPENSSL_ROOT_DIR}" ]; then
  if command -v brew >/dev/null 2>&1; then
    OPENSSL_ROOT_DIR="$(brew --prefix openssl@3 2>/dev/null || true)"
    if [ -z "${OPENSSL_ROOT_DIR}" ]; then
      OPENSSL_ROOT_DIR="$(brew --prefix openssl@1.1 2>/dev/null || true)"
    fi
  fi
fi
if [ -z "${OPENSSL_ROOT_DIR}" ]; then
  log "未检测到 OpenSSL，请设置 OPENSSL_ROOT_DIR 或通过 brew 安装 openssl"
  exit 7
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

log "开始构建"
cmake -S "$SRC_DIR" -B "$BUILD_DIR" \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_STATIC_LIBS=ON \
  -DBUILD_EXAMPLES=OFF \
  -DBUILD_TESTING=OFF \
  -DLIBSSH2_BUILD_DOCS=OFF \
  -DLIBSSH2_DISABLE_INSTALL=ON \
  -DLIBSSH2_USE_PKGCONFIG=OFF \
  -DCRYPTO_BACKEND=OpenSSL \
  -DOPENSSL_ROOT_DIR="$OPENSSL_ROOT_DIR"
cmake --build "$BUILD_DIR" --config Release

mkdir -p "$OUT_DIR"
LIB_A="$(find "$BUILD_DIR" -name "libssh2.a" -type f | head -n 1 || true)"
LIB_DYLIB="$(find "$BUILD_DIR" -name "libssh2*.dylib" -type f | head -n 1 || true)"
if [ -z "$LIB_A" ]; then
  log "未找到 libssh2.a"
  exit 8
fi
if [ -z "$LIB_DYLIB" ]; then
  log "未找到 libssh2.dylib"
  exit 9
fi
cp -f "$LIB_A" "$OUT_DIR/"
cp -f "$LIB_DYLIB" "$OUT_DIR/"

log "构建完成，产物已输出到 $OUT_DIR"
