#!/bin/sh
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tools_root="$project_root/work/android-remote/tools"
scrcpy_bin="$tools_root/scrcpy-macos-aarch64-v5.0.1/scrcpy"
if [ ! -x "$scrcpy_bin" ]; then
  echo 'scrcpy is not installed in this workspace. See the Android remote setup document.' >&2
  exit 1
fi
export ADB="$tools_root/platform-tools/adb"
export ADB_MDNS_AUTO_CONNECT=0
exec "$scrcpy_bin" --select-usb --force-adb-forward --max-size=1280 --no-audio --no-clipboard-autosync --no-power-on --window-title='Galaxy S20+ - Kakao bot development' "$@"
