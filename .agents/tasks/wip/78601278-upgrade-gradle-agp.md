# [T2] Upgrade Gradle wrapper and Android Gradle Plugin

- Status: wip
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: d4992f93 (dependency inventory)

## Goal

Move the build toolchain base to current stable releases: Gradle wrapper and AGP, plus the compileSdk/targetSdk level required by the chosen AGP.

## Scope

- Bump `gradle/wrapper/gradle-wrapper.properties` distributionUrl to the stable Gradle version selected in T1
- Bump AGP in root `build.gradle.kts` to the stable version selected in T1
- Raise compileSdk/targetSdk in `app/build.gradle.kts` to the level required by the chosen AGP (minSdk 26 stays)
- Update `.github/workflows/daily-beta-release.yml` SDK pins (`platforms;android-XX`, `build-tools;XX`) to match the new compileSdk
- Do not bump Kotlin, Compose, or library versions in this task (T3/T4 follow)

## Acceptance criteria

- [ ] `./gradlew --version` reports the selected Gradle
- [ ] `./gradlew help` resolves the project with the new AGP
- [ ] `./gradlew test` passes
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] CI workflow SDK pins match the new compileSdk
- [ ] No Kotlin/Compose/library version changes included in this task

## Notes

- Respect the AGP <-> Gradle and AGP <-> compileSdk compatibility requirements recorded in T1.
- Verify current stable AGP/Gradle versions at execution time (per T1 matrix).
