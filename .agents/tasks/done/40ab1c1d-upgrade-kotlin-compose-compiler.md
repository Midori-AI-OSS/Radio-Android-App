# [T3] Upgrade Kotlin and migrate Compose compiler setup

- Status: merged into 78601278 (2026-08-15)
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: 78601278 (Gradle + AGP upgrade)

## Goal

Move Kotlin (android + serialization plugins) to the current stable release selected in T1 and migrate the Compose compiler to the supported setup for that Kotlin version.

## Scope

- Bump `org.jetbrains.kotlin.android` and `org.jetbrains.kotlin.plugin.serialization` in root `build.gradle.kts`
- If the selected Kotlin is 2.x (per T1): add the `org.jetbrains.kotlin.plugin.compose` Gradle plugin and remove `composeOptions { kotlinCompilerExtensionVersion }` from `app/build.gradle.kts` (keep `buildFeatures.compose = true`)
- Fix any Kotlin/Compose compilation issues introduced by the upgrade
- Do not bump library dependency versions in this task (T4 follows)

## Acceptance criteria

- [ ] `./gradlew test` passes
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] Compose compiler is configured via the supported mechanism for the selected Kotlin version (no stale `kotlinCompilerExtensionVersion` pin unless T1 documented a reason)
- [ ] No library version changes included in this task

## Notes

- The Kotlin -> Compose compiler plugin mapping is a hard compatibility constraint; use the versions recorded in T1.
- Verify current stable Kotlin at execution time.

## Merge note

T3 cannot execute independently of T2: AGP 9.x auto-upgrades declared KGP below 2.2.10 on the classpath and fails with MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN when Compose is enabled without `org.jetbrains.kotlin.plugin.compose`, so the Kotlin bump must land in the same execution as the AGP bump. Scope and acceptance criteria are merged into `78601278-upgrade-toolchain-kotlin-compose.md`; execute that task instead.
