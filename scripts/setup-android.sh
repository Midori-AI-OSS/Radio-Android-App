#!/usr/bin/env bash

set -euo pipefail

# Keep build dependencies in a task-local /tmp directory; do not change the
# container image or the shared project checkout.
setup_root="$(printenv ANDROID_SETUP_ROOT || true)"
if [[ -z "$setup_root" ]]; then
  setup_root="/tmp/radio-android-app-tools"
fi
jdk_dir="$setup_root/jdk17"
sdk_root="$setup_root/android-sdk"
download_dir="$setup_root/downloads"
sdk_tools_version="15859902"
printf -v sdk_tools_zip 'commandlinetools-linux-%s_latest.zip' "$sdk_tools_version"
sdk_tools_sha256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"

die() {
  printf 'setup-android: %s\n' "$*" >&2
  exit 1
}

for tool in curl python3 tar sha256sum; do
  command -v "$tool" >/dev/null 2>&1 || die "required tool not found: $tool"
done

[[ "$(uname -s)" == Linux ]] || die "this setup script supports Linux only"
[[ "$(uname -m)" == x86_64 ]] || die "Android command-line tools require Linux x86_64 here"

mkdir -p "$download_dir"

java_home=""
system_java_home="$(printenv JAVA_HOME || true)"
if [[ -n "$system_java_home" && -x "$system_java_home/bin/java" ]]; then
  java_major="$("$system_java_home/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)"
  [[ "$java_major" == 17 ]] && java_home="$system_java_home"
fi

if [[ -z "$java_home" ]] && command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)"
  if [[ "$java_major" == 17 ]]; then
    java_home="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  fi
fi

if [[ -z "$java_home" ]]; then
  if [[ ! -x "$jdk_dir/bin/java" ]]; then
    printf 'setup-android: resolving the latest Eclipse Temurin JDK 17 package\n'
    jdk_metadata="$download_dir/temurin-jdk17.json"
    jdk_fields="$download_dir/temurin-jdk17-package.txt"
    curl --fail --location --retry 3 --silent --show-error \
      'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse' \
      --output "$jdk_metadata"
    python3 - "$jdk_metadata" >"$jdk_fields" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    release = json.load(source)[0]
package = release["binary"]["package"]
print(package["link"])
print(package["checksum"])
PY
    jdk_url="$(sed -n '1p' "$jdk_fields")"
    jdk_sha="$(sed -n '2p' "$jdk_fields")"
    [[ -n "$jdk_url" && -n "$jdk_sha" ]] || die "could not read the JDK download and checksum"
    jdk_archive="$download_dir/temurin-jdk17.tar.gz"
    curl --fail --location --retry 3 --silent --show-error \
      "$jdk_url" --output "$jdk_archive"
    printf '%s  %s\n' "$jdk_sha" "$jdk_archive" | sha256sum --check --status \
      || die "Temurin JDK checksum verification failed"
    jdk_extract="$(mktemp -d "$setup_root/jdk17-extract.XXXXXX")"
    tar -xzf "$jdk_archive" -C "$jdk_extract" --strip-components=1
    rm -rf "$jdk_dir"
    mv "$jdk_extract" "$jdk_dir"
  fi
  java_home="$jdk_dir"
fi

sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then
  sdk_archive="$download_dir/$sdk_tools_zip"
  if [[ ! -f "$sdk_archive" ]] || \
    [[ "$(sha256sum "$sdk_archive" | awk '{print $1}')" != "$sdk_tools_sha256" ]]; then
    printf 'setup-android: downloading Android command-line tools %s\n' "$sdk_tools_version"
    curl --fail --location --retry 3 --silent --show-error \
      "https://dl.google.com/android/repository/$sdk_tools_zip" \
      --output "$sdk_archive"
  fi
  printf '%s  %s\n' "$sdk_tools_sha256" "$sdk_archive" | sha256sum --check --status \
    || die "Android command-line tools checksum verification failed"
  sdk_extract="$(mktemp -d "$setup_root/sdk-tools-extract.XXXXXX")"
  python3 - "$sdk_archive" "$sdk_extract" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import sys

archive = Path(sys.argv[1])
destination = Path(sys.argv[2]).resolve()
with ZipFile(archive) as bundle:
    bad_member = bundle.testzip()
    if bad_member:
        raise SystemExit(f"corrupt ZIP member: {bad_member}")
    for member in bundle.infolist():
        target = (destination / member.filename).resolve()
        if target != destination and destination not in target.parents:
            raise SystemExit(f"unsafe ZIP member path: {member.filename}")
    bundle.extractall(destination)
    for member in bundle.infolist():
        mode = (member.external_attr >> 16) & 0o777
        target = (destination / member.filename).resolve()
        if mode and target.exists():
            target.chmod(mode)
PY
  [[ -x "$sdk_extract/cmdline-tools/bin/sdkmanager" ]] \
    || die "Android command-line tools archive had an unexpected layout"
  mkdir -p "$sdk_root/cmdline-tools"
  rm -rf "$sdk_root/cmdline-tools/latest"
  mv "$sdk_extract/cmdline-tools" "$sdk_root/cmdline-tools/latest"
  rm -rf "$sdk_extract"
fi

export JAVA_HOME="$java_home"
export ANDROID_HOME="$sdk_root"
export ANDROID_SDK_ROOT="$sdk_root"
export GRADLE_USER_HOME="$setup_root/gradle"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

printf 'setup-android: accepting Android SDK package licenses\n'
set +o pipefail
yes | "$sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null
set -o pipefail

printf 'setup-android: installing platform-tools, API 37.0, and build-tools 36.0.0\n'
"$sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" \
  'platform-tools' \
  'platforms;android-37.0' \
  'build-tools;36.0.0'

env_file="$setup_root/android-env.sh"
{
  printf 'export JAVA_HOME=%q\n' "$JAVA_HOME"
  printf 'export ANDROID_HOME=%q\n' "$ANDROID_HOME"
  printf 'export ANDROID_SDK_ROOT=%q\n' "$ANDROID_SDK_ROOT"
  printf 'export GRADLE_USER_HOME=%q\n' "$GRADLE_USER_HOME"
  printf 'export PATH=%q:"$PATH"\n' \
    "$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"
} >"$env_file"

printf 'setup-android: ready\n'
printf 'Activate with: source %q\n' "$env_file"
printf 'Build with: ./gradlew :app:assembleDebug\n'
