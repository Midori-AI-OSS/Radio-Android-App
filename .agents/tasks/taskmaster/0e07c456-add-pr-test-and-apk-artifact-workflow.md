# [T11] Add PR test and debug-APK artifact workflow

- Status: done
- Source: Approved workstream: PR test/build APK-artifact workflow (no PR-triggered CI exists today; PRs merge with zero checks — e.g., run 31886563466 failed only after PR #7's merge push)
- Owner: coder
- Depends on: 0d1416de (SDK package pin correction — the new workflow must use the corrected `platforms;android-37.0` pin)

## Goal

Give pull requests against `main` an automated gate: run the unit tests and build the debug APK, uploading the APK as a workflow artifact so reviewers/testers can sideload it without a local build.

## Scope

- Add a new workflow file (e.g., `.github/workflows/pr-test-and-apk-artifact.yml`) triggered on `pull_request` to `main`
- Test job: `./gradlew --no-daemon test` (Java 17 temurin, `android-actions/setup-android@v3`, SDK packages installed with the corrected pins `platforms;android-37.0` + `build-tools;36.0.0` — mirror the setup steps already used in `daily-beta-release.yml`)
- Build job: `./gradlew --no-daemon -PciVersionCode=... -PciVersionName=... :app:assembleDebug` (reuse the existing `-PciVersionCode`/`-PciVersionName` convention from `app/build.gradle.kts`), then upload `app/build/outputs/apk/debug/*.apk` via `actions/upload-artifact@v4` with `if-no-files-found: error` and a PR-derived artifact name
- Do not modify `daily-beta-release.yml` or `app/build.gradle.kts`

## Acceptance criteria

- [x] New workflow exists and triggers on `pull_request` to `main`
- [x] Test job runs `./gradlew --no-daemon test`
- [x] Build job runs `:app:assembleDebug` and uploads the APK artifact (`if-no-files-found: error`)
- [x] SDK pins in the new workflow are `platforms;android-37.0` and `build-tools;36.0.0`
- [x] Verified: a run on a real PR passes, with run URL and results recorded in this file (or the local CI-equivalent commands/results recorded if a PR run is not feasible)

## Verification record (2026-08-15)

- No real PR run was possible (no push/PR opened from this environment); local CI-equivalent recorded instead, matching the workflow's exact commands and SDK pins.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ./gradlew --no-daemon test` -> BUILD SUCCESSFUL (24 tasks)
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ./gradlew --no-daemon -PciVersionCode=7 -PciVersionName=0.1.0-pr.7 :app:assembleDebug` -> BUILD SUCCESSFUL (36 tasks)
- APK produced at `app/build/outputs/apk/debug/app-debug.apk` (matches the workflow's upload glob `app/build/outputs/apk/debug/*.apk`)
- Workflow YAML parsed cleanly; structure spot-checked (trigger `pull_request` -> `main`, pins `platforms;android-37.0` + `build-tools;36.0.0` in both jobs, upload with `if-no-files-found: error` and PR-derived artifact name `midoriai-radio-debug-pr-<number>-<short_sha>`)
- Docker build validation skipped per instruction.

## Notes

- This is a new file, not a refactor of the beta workflow; keep both workflows' setup steps consistent with each other.
- `./gradlew test` and `./gradlew :app:assembleDebug` must pass locally before opening the PR.
- Minimal scope: tests + APK artifact only; no release/publish behavior in this workflow.

## Audit (2026-08-15)

- Reviewed implementing commit `00d02d8a492e59ca40217606362022872ba98201` and the current repository state.
- Confirmed the workflow is scoped to pull requests targeting `main`; its test and dependent build jobs use the required Java/SDK setup, corrected SDK pins, test/build commands, and APK artifact upload configuration.
- Re-ran `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ./gradlew --no-daemon test` (BUILD SUCCESSFUL, 24 actionable tasks) and `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ./gradlew --no-daemon -PciVersionCode=7 -PciVersionName=0.1.0-pr.7 :app:assembleDebug` (BUILD SUCCESSFUL, 36 actionable tasks).
- Remote PR execution was not available; the task's recorded local CI-equivalent verification satisfies its stated alternative.

Passed; routed to taskmaster queue.
