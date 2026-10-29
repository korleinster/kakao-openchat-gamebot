#!/usr/bin/env python3
"""Prepare pinned Android build tools inside this macOS ARM64 workspace."""
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / 'work/toolchain'
DOWNLOADS = BASE / 'downloads'
LOCK = json.loads((ROOT / 'tools/build-toolchain.lock.json').read_text())

def download(name, url, algorithm, expected):
    target = DOWNLOADS / name
    if not target.exists() or hashlib.new(algorithm, target.read_bytes()).hexdigest() != expected:
        partial = target.with_suffix(target.suffix + '.partial')
        print('Downloading:', name, flush=True)
        with urllib.request.urlopen(url, timeout=60) as response, partial.open('wb') as output:
            while True:
                block = response.read(1024 * 1024)
                if not block:
                    break
                output.write(block)
        if hashlib.new(algorithm, partial.read_bytes()).hexdigest() != expected:
            raise RuntimeError('Official checksum mismatch: ' + name)
        partial.replace(target)
    return target

def extract_zip(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as source:
        if source.testzip() is not None:
            raise RuntimeError('Corrupt ZIP archive')
        for item in source.infolist():
            target = (destination / item.filename).resolve()
            if not target.is_relative_to(destination.resolve()):
                raise RuntimeError('Unsafe archive path')
            source.extract(item, destination)
            mode = (item.external_attr >> 16) & 0o777
            if mode:
                target.chmod(mode)

def main():
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise SystemExit('This helper is pinned for macOS ARM64. Other hosts can use JDK 17 + Android SDK + ./gradlew.')
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    java_home = BASE / 'jdk-17.0.21+9/Contents/Home'
    if not (java_home / 'bin/java').exists():
        archive = download('jdk17.tar.gz', 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.21_9.tar.gz', 'sha256', LOCK['jdk_sha256'])
        with tarfile.open(archive) as source:
            for item in source.getmembers():
                target = (BASE / item.name).resolve()
                if not target.is_relative_to(BASE.resolve()):
                    raise RuntimeError('Unsafe archive path')
                if item.issym() and not (target.parent / item.linkname).resolve().is_relative_to(BASE.resolve()):
                    raise RuntimeError('Unsafe archive link')
                if item.islnk() and not (BASE / item.linkname).resolve().is_relative_to(BASE.resolve()):
                    raise RuntimeError('Unsafe archive link')
            source.extractall(BASE)
    gradle = BASE / 'gradle-8.14.4/bin/gradle'
    if not gradle.exists():
        archive = download('gradle-8.14.4-bin.zip', 'https://services.gradle.org/distributions/gradle-8.14.4-bin.zip', 'sha256', LOCK['gradle_sha256'])
        extract_zip(archive, BASE)
    sdk = BASE / 'android-sdk'
    tools = sdk / 'cmdline-tools/19.0'
    if not tools.exists():
        archive = download('commandlinetools.zip', 'https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip', 'sha1', LOCK['command_line_tools_sha1'])
        staging = BASE / 'sdk-bootstrap'
        extract_zip(archive, staging)
        tools.parent.mkdir(parents=True, exist_ok=True)
        (staging / 'cmdline-tools').rename(tools)
    environment = os.environ.copy()
    environment.update(JAVA_HOME=str(java_home), ANDROID_USER_HOME=str(BASE / 'android-user'))
    packages = [(sdk/'platforms/android-35/android.jar'), (sdk/'build-tools/34.0.0/aapt2')]
    if not all(p.exists() for p in packages):
        # Direct Java invocation preserves paths containing spaces. The SDK CLI
        # presents its own package license prompt when approval is still needed.
        subprocess.run([str(java_home/'bin/java'), '-Dcom.android.sdklib.toolsdir='+str(tools), '-classpath', str(tools/'lib/sdkmanager-classpath.jar'), 'com.android.sdklib.tool.sdkmanager.SdkManagerCli', '--sdk_root='+str(sdk), 'platforms;android-35', 'build-tools;34.0.0'], env=environment, check=True)
    (ROOT/'local.properties').write_text('sdk.dir='+str(sdk)+'\n')
    keystore = BASE/'debug.keystore'
    if not keystore.exists():
        # Public standard Android DEBUG password; never use this key for release.
        subprocess.run([str(java_home/'bin/keytool'), '-genkeypair', '-keystore', str(keystore), '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey', '-dname', 'CN=Android Debug,O=Android,C=US', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000'], check=True)
        keystore.chmod(0o600)
    print('Pinned toolchain ready. Run scripts/build.sh testDebugUnitTest lintDebug assembleDebug')

if __name__ == '__main__':
    main()
