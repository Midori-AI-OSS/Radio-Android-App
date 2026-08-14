# [T1] Inventory dependencies and determine current stable compatible versions

- Status: wip
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: none (first task of the issue #3 workstream)

## Goal

Produce the compatibility matrix that all subsequent upgrade tasks in this workstream use.

## Scope

Inventory every direct Android/Kotlin/Gradle dependency and plugin in `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`, and `.github/workflows/daily-beta-release.yml`, then determine the current **stable** (never RC/beta/canary unless a pre-release is specifically required) mutually compatible versions for: AGP, Gradle, Kotlin, Compose compiler setup, compileSdk/targetSdk, Media3, Compose BOM, Activity, Lifecycle, Core, DataStore, Material, Coil, OkHttp, kotlinx.serialization, plus the remaining direct deps (palette-ktx, material (com.google.android.material), junit).

Current repo state (verified):

- AGP 8.2.2, Kotlin 1.9.22, Gradle wrapper 8.2.1
- compileSdk 34, targetSdk 34, minSdk 26
- Compose BOM 2024.02.00, compose compiler via `composeOptions.kotlinCompilerExtensionVersion = "1.5.8"` (no Kotlin Compose plugin)
- Media3 1.3.1, activity-compose 1.8.2, lifecycle 2.7.0, core-ktx 1.12.0, datastore-preferences 1.1.1, material 1.11.0, palette-ktx 1.0.0, coil-compose 2.6.0, okhttp 4.12.0, kotlinx-serialization-json 1.6.3, junit 4.13.2
- CI workflow pins `platforms;android-34` and `build-tools;34.0.0`; no version catalog in use

## Acceptance criteria

- [ ] Matrix records, for each dependency/plugin: current repo version, chosen stable target version, and compatibility constraints (AGP <-> Gradle, Kotlin <-> Compose compiler plugin, AGP <-> compileSdk)
- [ ] Explicitly records whether the Compose compiler must migrate from `composeOptions.kotlinCompilerExtensionVersion` to the `org.jetbrains.kotlin.plugin.compose` Gradle plugin (required for Kotlin 2.x)
- [ ] Notes any dependency that should stay pinned and why
- [ ] No build files or source code modified (research only); findings recorded in this task file

## Notes

- Verify versions at execution time against current stable release channels; do not trust this file's era of versions.
- Prefer stable over RC/beta/canary unless a pre-release is required to fix a demonstrated platform compatibility issue.
