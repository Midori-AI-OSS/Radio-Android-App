# [T1] Inventory dependencies and determine current stable compatible versions

- Status: done
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

## Compatibility matrix (verified 2026-08-14, all targets stable)

### Toolchain

| Component | Current | Target | Constraints / rationale |
|---|---|---|---|
| Gradle wrapper | 8.2.1 | 9.5.0 | AGP 9.1.1 min/default Gradle 9.3.1; Kotlin 2.4.10 fully supports Gradle up to 9.5.0 (9.5.1, 9.6.x, 9.7.0 exist but exceed the KGP tested max). 9.5.0 confirmed on services.gradle.org. |
| AGP | 8.2.2 | 9.1.1 | Compose ui/foundation 1.12.0 and core-ktx 1.19.0 AAR metadata require `minAndroidGradlePluginVersion 9.1.0` -> AGP >= 9.1.0 mandatory. AGP 9.1.1 (Apr 2026) supports API level 37 and below, min/default Gradle 9.3.1, Build Tools 36.0.0, JDK 17. Sits inside Kotlin 2.4.10 fully-supported AGP range (8.5.2-9.1.0). Newer stable AGP 9.3.1 rejected: exceeds KGP 2.4.10 tested max 9.1.0. |
| Kotlin | 1.9.22 | 2.4.10 | KGP 2.4.10 (Jul 2026): fully supports Gradle 7.6.3-9.5.0, AGP 8.5.2-9.1.0. 2.4.20 stable expected ~Sep 2026 (RC only now). |
| Compose compiler | 1.5.8 via `composeOptions.kotlinCompilerExtensionVersion` | `org.jetbrains.kotlin.plugin.compose` 2.4.10 | **Required migration** for Kotlin 2.x: apply the plugin (version tracks Kotlin) and drop `composeOptions`. With the plugin, no Compose<->Kotlin version checking is needed (per Compose<->Kotlin compatibility docs). |
| kotlinx.serialization plugin | 1.9.22 | 2.4.10 | `org.jetbrains.kotlin.plugin.serialization` must track the Kotlin version. |
| JDK | 17 | 17 | AGP 9.1 default/min 17; unchanged. |
| Build Tools (CI) | 34.0.0 | 36.0.0 | AGP 9.1 default/min. |
| compileSdk | 34 | 37 | **Required**: compose ui/foundation 1.12.0 and core-ktx 1.19.0 AAR metadata `minCompileSdk=37`. AGP 9.1.1 supports API 37. |
| targetSdk | 34 | 37 | Aligned with compileSdk (API 37 = Android 17, supported by AGP 9.1.1). |
| minSdk | 26 | 26 | Unchanged. |
| CI SDK platform | android-34 | android-37 | Must match compileSdk (T7 scope). |

### Libraries

| Dependency | Current | Target | Constraints / rationale |
|---|---|---|---|
| Compose BOM | 2024.02.00 | 2026.08.00 | BOM pins Compose 1.12.0 (animation/foundation/material/runtime/ui, Aug 2026) and material3 1.4.0 (verified in BOM pom). ui/foundation need compileSdk 37 + AGP 9.1.0; material3 needs 35. |
| Media3 | 1.3.1 | 1.11.0 | Aug 2026 stable; AAR `minCompileSdk=36`. Built with Kotlin 2.2.0 (fine for Kotlin 2.4.10 consumer). |
| activity-compose | 1.8.2 | 1.13.0 | AAR `minCompileSdk=36`. |
| lifecycle (runtime-ktx etc.) | 2.7.0 | 2.11.0 | AAR `minCompileSdk=34`. 2.12.0-alpha01 exists -> not chosen (alpha). |
| core-ktx | 1.12.0 | 1.19.0 | AAR `minCompileSdk=37`, `minAndroidGradlePluginVersion=9.1.0` (drives compileSdk/AGP choices above). |
| datastore-preferences | 1.1.1 | 1.2.1 | AAR `minCompileSdk=34`. 1.3.0-alpha10 exists -> not chosen (alpha). |
| material (com.google.android.material) | 1.11.0 | 1.14.0 | AAR `minCompileSdk=1`; stable. |
| palette-ktx | 1.0.0 | 1.0.0 | **Pinned**: stable line stuck at 1.0.0 (1.1.0-alpha01 is alpha). |
| coil-compose | 2.6.0 | 3.5.0 (io.coil-kt.coil3) | Coil3 3.5.0 stable; AAR `minCompileSdk=36`. **Group/package change** (io.coil-kt -> io.coil-kt.coil3, KMP) -> import changes needed in T4. Fallback: 2.7.0, the final 2.x release. |
| okhttp | 4.12.0 | 5.4.0 | JVM artifact, no compileSdk constraint. |
| kotlinx-serialization-json | 1.6.3 | 1.11.0 | Requires Kotlin 2.x (compatible with 2.4.10). |
| junit | 4.13.2 | 4.13.2 | **Pinned**: final 4.x release; JUnit 5 migration out of scope. |

### Compatibility notes

- AGP <-> Gradle: AGP 9.1.1 requires Gradle >= 9.3.1 (default 9.3.1); 9.5.0 chosen to stay at KGP 2.4.10 fully-supported max.
- Kotlin <-> Compose compiler: with `org.jetbrains.kotlin.plugin.compose` (version = Kotlin version), no compiler compatibility checking is required.
- AGP <-> compileSdk: AGP 9.1.1 supports API level 37 and below -> compileSdk/targetSdk 37 OK.
- AGP 9.1 R8 behavior change: repackaging to unnamed (default) package when compiling to DEX is now the default (opt-out `-dontrepackage`) -> review in T5 if shrinking is enabled.
- All chosen targets are stable releases; alpha/beta/canary candidates (lifecycle 2.12.0-alpha01, datastore 1.3.0-alpha10, palette 1.1.0-alpha01, Kotlin 2.4.20-RC, AGP 9.4.0-alpha08) were rejected per scope rules.

### Acceptance criteria

- [x] Matrix records, for each dependency/plugin: current repo version, chosen stable target version, and compatibility constraints (AGP <-> Gradle, Kotlin <-> Compose compiler plugin, AGP <-> compileSdk)
- [x] Explicitly records whether the Compose compiler must migrate from `composeOptions.kotlinCompilerExtensionVersion` to the `org.jetbrains.kotlin.plugin.compose` Gradle plugin (required for Kotlin 2.x)
- [x] Notes any dependency that should stay pinned and why
- [x] No build files or source code modified (research only); findings recorded in this task file

## Verification

All version/constraint claims above were machine-verified at execution time (2026-08-14), not from memory:

- Stable version lists: `curl -s https://services.gradle.org/versions/all | grep -oE '"version" : "9\.[0-9]+\.[0-9]+"'` and `curl -s https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml | grep -E '<version>|<latest>'`
- Compose BOM mapping: `curl -s https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.08.00/compose-bom-2026.08.00.pom` (ui/foundation/runtime/animation 1.12.0, material3 1.4.0)
- AAR metadata (minCompileSdk / minAndroidGradlePluginVersion): downloaded each artifact AAR from Google Maven (dl.google.com/android/maven2) or Maven Central (repo1.maven.org for Coil3) and read `META-INF/com/android/build/gradle/aar-metadata.properties`
- Kotlin <-> Gradle/AGP compatibility table and Compose <-> Kotlin plugin rule: kotlinlang.org and developer.android.com compatibility pages (fetched 2026-08-13/14)
- AGP 9.1.1 release notes (developer.android.com, Apr 2026): API 37 support, Gradle 9.3.1, Build Tools 36.0.0, JDK 17, R8 repackaging default
