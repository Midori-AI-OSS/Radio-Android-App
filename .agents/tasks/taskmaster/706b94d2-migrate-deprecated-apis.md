# [T5] Migrate deprecated APIs and build configuration required by the new stack

- Status: done
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: 05b30b50 (library version upgrades)

## Goal

Resolve deprecations and configuration changes surfaced by the upgraded AGP/Kotlin/Compose/Media3 versions so the codebase compiles cleanly and keeps current behaviors.

## Scope

- Address compile errors and warnings caused by the upgrade, including any deprecated Media3 session/browse APIs, lifecycle, activity, and Compose APIs used in the codebase
- Migrate build configuration required by newer AGP/Kotlin (e.g., `kotlinOptions` -> `kotlin { compilerOptions }` if the selected AGP/Kotlin requires or warns about it)
- Preserve existing behavior exactly; no feature changes
- No broad fallbacks or backward-compatibility shims unless required by a specific compile issue

## Acceptance criteria

- [x] `./gradlew test` passes
- [x] `./gradlew :app:assembleDebug` succeeds
- [x] `./gradlew :app:assembleRelease` succeeds
- [x] No new deprecation warnings from upgraded APIs in the changed files (or each remaining one is justified)
- [x] Behavior unchanged: code diff limited to API migration, not logic changes

## Verification

- `./gradlew :app:compileDebugKotlin --rerun-tasks --warning-mode all` -> BUILD SUCCESSFUL, zero warnings
- `./gradlew test :app:assembleDebug :app:assembleRelease` -> BUILD SUCCESSFUL (incl. lintVitalRelease)

Changes: removed `android.builtInKotlin=false`/`android.newDsl=false` (AGP 9 defaults) and the `org.jetbrains.kotlin.android` plugin (built-in Kotlin); replaced deprecated `bundleOf` with platform `Bundle`; replaced deprecated `AcceptedResultBuilder(MediaSession)` with `(MediaSession, ControllerInfo)`.

## Notes

- Confirm which deprecated APIs actually surface from the upgrade before touching anything; do not preemptively rewrite working code.
- Unit tests under `app/src/test/` (e.g., RadioAutoCatalogTest) must keep passing.
