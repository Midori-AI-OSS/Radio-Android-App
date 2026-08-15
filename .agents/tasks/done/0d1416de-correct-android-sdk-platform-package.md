# [T10] Correct Android SDK platform package pin to platforms;android-37.0

- Status: done
- Source: CI run 31886563466 (Radio App Daily Beta, push to main, 2026-08-15) failure: `Warning: Failed to find package 'platforms;android-37'` then exit code 1
- Owner: coder
- Depends on: none

## Goal

Fix the CI SDK package install so the beta workflow stops failing. `platforms;android-37` does not exist in the remote sdkmanager repository; the available package is `platforms;android-37.0` (verified 2026-08-15 with `sdkmanager --list`: `platforms;android-37.0 | 2 | Android SDK Platform 37.0`; there is no `platforms;android-37` entry).

## Scope

Replace the `platforms;android-37` package pin with `platforms;android-37.0` in every place it appears (keep `build-tools;36.0.0` unchanged):

- `.github/workflows/daily-beta-release.yml` — test job and build_and_release job SDK install steps
- `.agents/setup-agents.sh` — usage doc lines and the sdkmanager install list
- `dockerfile` — sdkmanager RUN

Do not change `app/build.gradle.kts` (compileSdk/targetSdk 37 stays; it already resolves locally to the `android-37.0` platform dir per T6/e2752bd2).

## Acceptance criteria

- [x] No `platforms;android-37` (without `.0`) remains in the workflow, setup-agents.sh, or dockerfile
- [x] All three files install `platforms;android-37.0` and `build-tools;36.0.0`
- [x] `./gradlew test` and `./gradlew :app:assembleDebug` still pass locally
- [x] A CI run (push or workflow_dispatch) passes the "Install Android SDK packages" step; record the run URL and result in this file (or record the local CI-equivalent command and result if no CI run is possible)

## Verification record (2026-08-15)

- No CI run was possible (no push/trigger performed); local CI-equivalent recorded instead.
- Local CI-equivalent of the failing step, using the corrected pin (ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk):
  - `yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses` -> ok
  - `sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" "platforms;android-37.0" "build-tools;36.0.0"` -> exit 0, installed `Android SDK Platform 37.0` and build-tools 36.0.0; no `Failed to find package` warning.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ./gradlew --no-daemon test` -> BUILD SUCCESSFUL (24 tasks)
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ./gradlew --no-daemon :app:assembleDebug` -> BUILD SUCCESSFUL (36 tasks)
- Docker build validation skipped per instruction; Docker daemon not accessible in this environment.

## Notes

- Evidence of the failure (run 31886563466): `Install Android SDK packages` step — `yes | sdkmanager --licenses >/dev/null`; `sdkmanager "platforms;android-37" "build-tools;36.0.0"` -> `Warning: Failed to find package 'platforms;android-37'` -> `##[error]Process completed with exit code 1.` No later step ran; build_and_release never started.
- The remote repo's 37-series packages are `platforms;android-37.0`, `platforms;android-37.1`, `platforms;android-37.2-beta1/2/3`; only the stable `platforms;android-37.0` should be pinned.
- Keep this change minimal: package pin only, no workflow restructuring (PR CI is a separate task, 0e07c456).
