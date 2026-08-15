# [T6] Run unit tests, clean build, and verify CI/beta APK generation

- Status: done
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: 706b94d2 (deprecated API migration)

## Goal

Prove the modernized project builds cleanly from scratch and that CI/beta APK generation still works.

## Scope

- Run the project checks from the root `AGENTS.md` and record exact commands/results in this task file:
  - `./gradlew test`
  - `./gradlew clean :app:assembleDebug`
  - `./gradlew :app:assembleRelease`
- Prefer Dockerized execution via the PixelArch container when Docker is available on the host
- Verify the daily beta workflow (`.github/workflows/daily-beta-release.yml`) is consistent with the new toolchain (SDK pins, Gradle version, Java 17) and, if feasible, trigger a workflow_dispatch run or a local equivalent of its build steps to confirm the beta APK publishes
- If CI steps fail due to the upgrade, fix the workflow in this task and re-verify

## Acceptance criteria

- [x] `./gradlew test` passes
- [x] `./gradlew clean :app:assembleDebug` succeeds from a clean state
- [x] `./gradlew :app:assembleRelease` succeeds
- [x] Beta workflow is aligned with the new toolchain and produces a debug APK artifact (verified by run or by a recorded local equivalent)
- [x] Exact commands and results recorded in this task file

## Notes

- Do not run `./buildapk.sh`/device install here; device validation is T7/T8.
- Record any version or config drift found between local build and CI config.

## Verification results (2026-08-15)

Environment: Docker unavailable on host (permission denied), so Gradle ran directly on the host.
Toolchain: OpenJDK 17.0.20, Gradle wrapper 9.5.0 (AGP 9.1.1, Kotlin 2.4.10), `sdk.dir=/tmp/agents-artifacts/android-sdk`
(platforms: android-34, android-37.0; build-tools: 34.0.0, 36.0.0).

1. `./gradlew test` -> BUILD SUCCESSFUL. First run was up-to-date; re-run after the clean build below
   executed for real: 11 suites / 52 tests / 0 failures / 0 errors / 0 skipped.
2. `./gradlew clean :app:assembleDebug` -> BUILD SUCCESSFUL (37 tasks executed from a clean state).
3. `./gradlew :app:assembleRelease` -> BUILD SUCCESSFUL (47 tasks incl. lintVitalRelease; output
   `app-release-unsigned.apk` as expected, no signing config in project).
4. Beta workflow alignment (`.github/workflows/daily-beta-release.yml`) - no drift, no changes needed:
   - Java: CI temurin 17 matches local OpenJDK 17.0.20 and `jvmTarget`/`compileOptions` VERSION_17.
   - Gradle: CI uses `./gradlew`; wrapper pins 9.5.0 locally and in CI. No version pinned separately in CI.
   - SDK pins: CI installs `platforms;android-37` + `build-tools;36.0.0`. Resolved locally via init script:
     `RESOLVED-BUILD-TOOLS=36.0.0`, `RESOLVED-COMPILE-SDK=android-37.0` (new sdkmanager installs platform
     dirs with the `.0` suffix; same package as `platforms;android-37`).
   - Version props: `-PciVersionCode` / `-PciVersionName` are consumed by `app/build.gradle.kts`
     (confirmed via `output-metadata.json`).
5. Local equivalent of the CI `build_and_release` build step (workflow_dispatch not feasible: no `gh`
   token/remote publish access on this host):
   `./gradlew --no-daemon -PciVersionCode=1042 -PciVersionName="0.1.0-beta.1042-1a2b3c4d" :app:assembleDebug`
   -> BUILD SUCCESSFUL; `app/build/outputs/apk/debug/app-debug.apk` produced with
   versionCode=1042, versionName=0.1.0-beta.1042-1a2b3c4d. The workflow artifact glob
   `app/build/outputs/apk/debug/*.apk` matches this path (if-no-files-found: error would not trip).

Conclusion: all acceptance criteria met; no version/config drift found between local build and CI config.
