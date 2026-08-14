# [T6] Run unit tests, clean build, and verify CI/beta APK generation

- Status: wip
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

- [ ] `./gradlew test` passes
- [ ] `./gradlew clean :app:assembleDebug` succeeds from a clean state
- [ ] `./gradlew :app:assembleRelease` succeeds
- [ ] Beta workflow is aligned with the new toolchain and produces a debug APK artifact (verified by run or by a recorded local equivalent)
- [ ] Exact commands and results recorded in this task file

## Notes

- Do not run `./buildapk.sh`/device install here; device validation is T7/T8.
- Record any version or config drift found between local build and CI config.
