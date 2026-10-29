#!/bin/sh
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
toolchain="$project_root/work/toolchain"
export JAVA_HOME="$toolchain/jdk-17.0.21+9/Contents/Home"
export ANDROID_HOME="$toolchain/android-sdk"
export ANDROID_USER_HOME="$toolchain/android-user"
export GRADLE_USER_HOME="$toolchain/gradle-user"
if [ ! -x "$JAVA_HOME/bin/java" ]; then echo 'Prepare the pinned local JDK first.' >&2; exit 1; fi
cd "$project_root"
if [ -x ./gradlew ]; then exec ./gradlew "$@"; fi
exec "$toolchain/gradle-8.9/bin/gradle" "$@"
