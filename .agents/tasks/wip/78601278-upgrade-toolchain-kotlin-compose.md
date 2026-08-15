# [T2+T3] Upgrade Gradle, AGP, Kotlin, and Compose compiler (single execution)

- Status: wip
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: none (T1 dependency inventory, archived off-disk; target versions embedded below)
- Merged: 40ab1c1d (Kotlin + Compose compiler upgrade) — former T3 is now part of this task; T2 and T3 cannot execute independently (see Notes)

## Goal

Move Gradle, AGP, Kotlin, and the Compose compiler setup to the current stable versions selected in T1 in one execution, plus the compileSdk/targetSdk level required by the target AGP/AARs.

## Target versions (from T1 matrix, verified 2026-08-14)

- Gradle wrapper: 9.5.0 (AGP 9.1.1 requires >= 9.3.1; 9.5.0 is the KGP 2.4.10 tested max)
- AGP: 9.1.1 (only stable line supporting API 37; newer stable 9.3.1 exceeds KGP 2.4.10 tested max; AGP 8.x cannot compile compileSdk 37)
- Kotlin: 2.4.10 (`org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.serialization`, and new `org.jetbrains.kotlin.plugin.compose` all at 2.4.10)
- compileSdk/targetSdk: 37 (minSdk 26 unchanged)
- CI SDK pins: `platforms;android-37`, `build-tools;36.0.0`
- JDK: 17 (unchanged)

## Scope

- Bump `gradle/wrapper/gradle-wrapper.properties` distributionUrl to Gradle 9.5.0
- Bump AGP in root `build.gradle.kts` to 9.1.1
- Bump the Kotlin android + serialization plugins to 2.4.10 and add `org.jetbrains.kotlin.plugin.compose` (2.4.10) in root `build.gradle.kts`
- In `app/build.gradle.kts`: apply `org.jetbrains.kotlin.plugin.compose`, remove `composeOptions { kotlinCompilerExtensionVersion }`, keep `buildFeatures.compose = true`
- Raise compileSdk/targetSdk in `app/build.gradle.kts` to 37 (minSdk 26 stays)
- Update `.github/workflows/daily-beta-release.yml` SDK pins to `platforms;android-37` / `build-tools;36.0.0`, plus the matching mirrors in `.agents/setup-agents.sh` and `dockerfile`
- Fix any Kotlin/Compose compilation issues introduced by this change
- Do not bump AndroidX/third-party library versions in this task (T4 follows)

## Acceptance criteria

- [ ] `./gradlew --version` reports Gradle 9.5.0
- [ ] `./gradlew help` resolves the project with the new AGP
- [ ] `./gradlew test` passes
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] Compose compiler is configured via `org.jetbrains.kotlin.plugin.compose`; no `composeOptions`/`kotlinCompilerExtensionVersion` remains
- [ ] CI workflow SDK pins are `platforms;android-37` and `build-tools;36.0.0`
- [ ] No AndroidX/third-party library version changes included

## Notes

- Why a single execution: AGP 9.x auto-upgrades any declared KGP below 2.2.10 on the classpath, and Compose enabled without `org.jetbrains.kotlin.plugin.compose` fails with MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN — so the AGP bump cannot land without the Kotlin 2.4.10 + Compose plugin bump. AGP 8.x cannot stay because compileSdk 37 (required by T4's target AARs) needs AGP 9.1.x, and KGP 2.4.10 does not support AGP below 8.5.2. Intermediate states are not buildable by design; commit the whole change as one unit.
- Execution state: a prior attempt already left uncommitted working-tree changes for the toolchain half (wrapper 9.5.0, AGP 9.1.1, compileSdk/targetSdk 37, CI/SDK pin mirrors, and `android.builtInKotlin=false` + `android.newDsl=false` in `gradle.properties`); the build is currently red. Continue from that state: complete the Kotlin 2.4.10 + Compose plugin half, review the two `gradle.properties` flags (keep only if required to build; remove otherwise), then verify the acceptance criteria and commit the combined change.
- Verify current stable versions at execution time against the compatibility constraints above (KGP 2.4.10 <-> Gradle <= 9.5.0, AGP 8.5.2-9.1.0; AGP 9.1.1 <-> Gradle >= 9.3.1).
