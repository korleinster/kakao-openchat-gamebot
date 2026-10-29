#!/bin/sh
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
adb_bin="$project_root/work/android-remote/tools/platform-tools/adb"
if [ ! -x "$adb_bin" ]; then
  echo 'ADB is not installed in this workspace. See the Android remote setup document.' >&2
  exit 1
fi
export ADB_MDNS_AUTO_CONNECT=0
exec "$adb_bin" -d "$@"
