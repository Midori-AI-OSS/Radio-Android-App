#!/usr/bin/env bash
set -euo pipefail

# setup-agents.sh - provision the PixelArch agents container for Midori AI Radio.
#
# Conventions (see dockerfile and buildapk.sh):
# - Intended for the PixelArch container; `yay` is assumed present (no gates).
# - Package installation exclusively via `yay -Syu` (no pacman/apt/yay -S).
# - Launch from the repository root: the script asserts ./gradlew exists and
#   invokes Gradle relative to the CWD (no -p, no worktree resolution).

usage() {
  cat <<'EOF'
Midori AI: provision the PixelArch agents container for Midori AI Radio

Usage:
  setup-agents.sh [--help]

Idempotent steps:
  - Install Java 17 (jdk17-openjdk)
  - Install Android SDK cmdline-tools, platform-tools, platforms;android-37
    and build-tools;36.0.0 under /tmp/agents-artifacts/android-sdk
  - Set the Gradle cache to /tmp/gradle (GRADLE_USER_HOME, persisted to
    /etc/profile.d/agents-env.sh)
  - Warm Gradle dependencies (gradlew :app:assembleDebug)
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1 (try --help)"
      ;;
  esac
  shift
done

# --- Repository root ---
[ -f ./gradlew ] || die "gradlew not found; run setup-agents.sh from the repository root"

# --- Target environment ---
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk
export ANDROID_HOME="${ANDROID_SDK_ROOT}"
export GRADLE_USER_HOME=/tmp/gradle
export PATH="${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"

# Persist for later agent shells (runtime equivalent of the dockerfile ENV).
sudo tee /etc/profile.d/agents-env.sh >/dev/null <<EOF
# Managed by setup-agents.sh
export JAVA_HOME=${JAVA_HOME}
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}
export ANDROID_HOME=${ANDROID_SDK_ROOT}
export GRADLE_USER_HOME=${GRADLE_USER_HOME}
export PATH="${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:\${PATH}"
EOF

mkdir -p "${GRADLE_USER_HOME}"

# --- Packages: yay -Syu only ---
echo "setup-agents: installing Java 17 and build essentials"
yay -Syu --noconfirm jdk17-openjdk unzip curl git

# --- Android SDK under /tmp/agents-artifacts/android-sdk ---
if [ -x "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "setup-agents: sdkmanager already present at ${ANDROID_SDK_ROOT}/cmdline-tools/latest"
else
  echo "setup-agents: bootstrapping Android cmdline-tools"
  cmdline_tmp="/tmp/android-sdk-tmp"
  rm -rf "${cmdline_tmp}"
  mkdir -p "${cmdline_tmp}"
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
    -o "${cmdline_tmp}/cmdline-tools.zip"
  rm -rf "${ANDROID_SDK_ROOT}"
  mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
  unzip -q "${cmdline_tmp}/cmdline-tools.zip" -d "${cmdline_tmp}"
  mv "${cmdline_tmp}/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  rm -rf "${cmdline_tmp}"
fi

if [ -f "${ANDROID_SDK_ROOT}/licenses/android-sdk-license" ]; then
  echo "setup-agents: SDK licenses already accepted"
else
  echo "setup-agents: accepting SDK licenses"
  # `yes` is expected to die of SIGPIPE once sdkmanager closes stdin; under
  # `set -o pipefail` that would surface as a nonzero pipeline status even on
  # success, so disable pipefail for this pipeline (sdkmanager's own exit
  # status is still honored).
  set +o pipefail
  yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses
  set -o pipefail
fi

echo "setup-agents: installing platform-tools, platforms;android-37, build-tools;36.0.0"
sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" \
  "platform-tools" \
  "platforms;android-37" \
  "build-tools;36.0.0"

# --- Warm Gradle dependencies (downloads distribution + deps; validates JDK/SDK) ---
echo "setup-agents: warming Gradle dependencies (gradlew :app:assembleDebug)"
chmod +x ./gradlew
./gradlew :app:assembleDebug

echo "setup-agents: done"
echo "setup-agents: java=$("${JAVA_HOME}/bin/java" -version 2>&1 | head -n1)"
echo "setup-agents: sdk=${ANDROID_SDK_ROOT}"
echo "setup-agents: gradle_cache=${GRADLE_USER_HOME}"
