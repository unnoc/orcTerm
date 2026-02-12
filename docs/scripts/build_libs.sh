#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORK_DIR="$SCRIPT_DIR/temp_build"
CPP_DIR="$ROOT_DIR/sshlib/src/main/cpp"
OUT_DIR="$SCRIPT_DIR/libs/android"

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

LIBS_DIR="$(find "$WORK_DIR" -maxdepth 4 -type d -name libs | head -n 1 || true)"
INCLUDE_DIR="$(find "$WORK_DIR" -maxdepth 4 -type d -name include | head -n 1 || true)"
if [ -z "$LIBS_DIR" ]; then
  log "未找到 libs 输出目录"
  exit 3
fi
if [ -z "$INCLUDE_DIR" ]; then
  log "未找到 include 输出目录"
  exit 4
fi

mkdir -p "$CPP_DIR/libs" "$CPP_DIR/include" "$OUT_DIR"
cp -r "$LIBS_DIR/"* "$CPP_DIR/libs/"
cp -r "$INCLUDE_DIR/"* "$CPP_DIR/include/"
cp -r "$LIBS_DIR/"* "$OUT_DIR/"

if ! find "$CPP_DIR/libs" -name "*.a" -type f | grep -q .; then
  log "未检测到 .a 产物"
  exit 5
fi

log "完成：libs 与 include 已写入 sshlib/src/main/cpp，并归档到 $OUT_DIR"
