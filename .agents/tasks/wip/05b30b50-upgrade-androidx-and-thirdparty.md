# [T4] Upgrade AndroidX and third-party library versions

- Status: wip
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: 40ab1c1d (Kotlin + Compose compiler upgrade)

## Goal

Bring all direct runtime library dependencies in `app/build.gradle.kts` to the current stable versions selected in T1.

## Scope

Bump to the T1-selected stable versions:

- Media3 (exoplayer, session)
- Compose BOM (ui, material3, material-icons-extended, tooling follow the BOM)
- activity-compose, lifecycle (runtime-ktx, viewmodel-compose, viewmodel-ktx), core-ktx
- datastore-preferences, material (com.google.android.material), palette-ktx
- coil-compose, okhttp, kotlinx-serialization-json, junit

Do not change build toolchain (Gradle/AGP/Kotlin) versions in this task. Keep any T1-documented deliberate pins with a code comment.

## Acceptance criteria

- [ ] `./gradlew test` passes
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] All direct dependencies are at the T1-selected stable versions (diff review)
- [ ] No unexplained legacy version pins remain (any remaining pin is documented with a reason)

## Notes

- Material and Compose BOM versions should be checked for cross-compatibility with the selected compileSdk.
- kotlinx.serialization-json must remain compatible with the Kotlin version from T3.
