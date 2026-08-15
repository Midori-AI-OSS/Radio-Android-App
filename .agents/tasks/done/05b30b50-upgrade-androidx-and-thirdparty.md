# [T4] Upgrade AndroidX and third-party library versions

- Status: wip
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: 78601278 (Gradle + AGP + Kotlin + Compose compiler upgrade)

## Goal

Bring all direct runtime library dependencies in `app/build.gradle.kts` to the current stable versions selected in T1.

## Scope

Bump to the T1-selected stable versions (recorded 2026-08-14; the T1 matrix file is archived off-disk, so the targets are embedded here):

- Media3 1.11.0 (exoplayer, session)
- Compose BOM 2026.08.00 (ui, material3, material-icons-extended, tooling follow the BOM)
- activity-compose 1.13.0, lifecycle 2.11.0 (runtime-ktx, viewmodel-compose, viewmodel-ktx), core-ktx 1.19.0
- datastore-preferences 1.2.1, material 1.14.0 (com.google.android.material), palette-ktx 1.0.0 (pinned)
- coil 3.5.0 (new group io.coil-kt.coil3: package/import changes required), okhttp 5.4.0, kotlinx-serialization-json 1.11.0, junit 4.13.2 (pinned)

Do not change build toolchain (Gradle/AGP/Kotlin) versions in this task. Keep any T1-documented deliberate pins with a code comment.

## Acceptance criteria

- [ ] `./gradlew test` passes
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] All direct dependencies are at the T1-selected stable versions (diff review)
- [ ] No unexplained legacy version pins remain (any remaining pin is documented with a reason)

## Notes

- Material and Compose BOM versions should be checked for cross-compatibility with the selected compileSdk.
- kotlinx.serialization-json must remain compatible with the Kotlin version (2.4.10) from 78601278.
