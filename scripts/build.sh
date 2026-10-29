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
task_key=$(printf '%s' "$project_root" | shasum -a 256 | cut -c1-12)
task_build_root="${TMPDIR:-/tmp}/kakao-openchat-gamebot-$task_key"
if [ -x ./gradlew ]; then
    ./gradlew "-PbotBuildRoot=$task_build_root" "$@"
else
    "$toolchain/gradle-8.14.4/bin/gradle" "-PbotBuildRoot=$task_build_root" "$@"
fi
# Mirror only final artifacts/reports to the documented paths. Gradle never reads
# these copies, so external synchronization cannot duplicate its intermediates.
mkdir -p "$project_root/app/build"
for part in outputs reports test-results; do
    rm -rf -- "$project_root/app/build/$part"
    if [ -d "$task_build_root/app/$part" ]; then
        cp -R "$task_build_root/app/$part" "$project_root/app/build/$part"
    fi
done
