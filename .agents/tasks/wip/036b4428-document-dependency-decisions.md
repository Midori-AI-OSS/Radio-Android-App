# [T9] Document dependency decisions and close out issue #3

- Status: wip
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder
- Depends on: d6b4fdad (Android Auto verification)

## Goal

Leave a durable record of the modernization and close out issue #3 with verified outcomes.

## Outcome summary

Modernization (T1-T5) is complete and audit-verified; issue #3 remains **OPEN** because the
runtime/CI verification tasks (T6-T8) are not yet complete. See "Gap" below.

## Dependency decisions (before -> after)

| Dependency | Before | After | Notes |
|---|---|---|---|
| Gradle wrapper | 8.2.1 | 9.5.0 | AGP 9.1.1 requires Gradle >= 9.3.1 (T2+T3) |
| Android Gradle Plugin | 8.2.2 | 9.1.1 | only stable line supporting compileSdk 37 (T2+T3) |
| Kotlin | 1.9.22 | 2.4.10 | serialization + compose plugins at 2.4.10; `org.jetbrains.kotlin.android` replaced by AGP built-in Kotlin in T5 |
| compileSdk / targetSdk | 34 | 37 | minSdk 26 unchanged |
| Media3 | 1.3.1 | 1.11.0 | exoplayer, session |
| Compose BOM | 2024.02.00 | 2026.08.00 | ui, material3, material-icons-extended, tooling follow the BOM |
| activity-compose | 1.8.2 | 1.13.0 | |
| lifecycle | 2.7.0 | 2.11.0 | runtime-ktx, viewmodel-compose, viewmodel-ktx |
| core-ktx | 1.12.0 | 1.19.0 | |
| datastore-preferences | 1.1.1 | 1.2.1 | |
| material (com.google.android.material) | 1.11.0 | 1.14.0 | |
| Coil | 2.6.0 (io.coil-kt) | 3.5.0 (io.coil-kt.coil3) | + coil-network-okhttp for remote art; NowPlayingScreen migrated to coil3 APIs |
| OkHttp | 4.12.0 | 5.4.0 | |
| kotlinx-serialization-json | 1.6.3 | 1.11.0 | |
| JDK | 17 | 17 | unchanged |

## Deliberate pins (issue #3 suggested work item 8)

- `androidx.palette:palette-ktx:1.0.0` — pinned because no newer stable palette-ktx release exists (T1 selection). Reason documented as a code comment in `app/build.gradle.kts`.
- `junit:junit:4.13.2` — pinned because it is the latest stable JUnit 4 release (T1 selection). Reason documented as a code comment in `app/build.gradle.kts`.

No unexplained legacy version pins remain.

## Verification results

### Executed and audit-verified (T2-T5, commits 460a522 / cc1adc9 / a83d7c7)

- `./gradlew test` -> BUILD SUCCESSFUL (audits re-ran with `--rerun-tasks`)
- `./gradlew :app:assembleDebug` -> BUILD SUCCESSFUL
- `./gradlew :app:assembleRelease` -> BUILD SUCCESSFUL (incl. lintVitalRelease)
- `./gradlew :app:compileDebugKotlin --rerun-tasks --warning-mode all` -> BUILD SUCCESSFUL, zero warnings (T5)
- Toolchain: Gradle 9.5.0, AGP 9.1.1, Kotlin 2.4.10, Java 17, Android SDK `platforms;android-37` + `build-tools;36.0.0` (Docker unavailable on host; runs used the local wrapper)

### Re-verified at HEAD for this task (2026-08-15, branch midoriaiagents/ginger-1831896e61)

- `./gradlew test` -> BUILD SUCCESSFUL (24 tasks)
- `./gradlew :app:assembleDebug :app:assembleRelease` -> BUILD SUCCESSFUL (83 tasks, incl. lintVitalRelease)

### Pending (T6-T8 remain in `.agents/tasks/wip/`)

- T6 (e2752bd2, clean build + CI/beta APK generation): local equivalent verified (assembleRelease incl. lintVitalRelease; CI workflow SDK pins aligned: `platforms;android-37`, `build-tools;36.0.0`, Java 17), but no `workflow_dispatch` run recorded.
- T7 (dcaae75d, phone playback verification on device): not executed — no Android device available in the execution environment (adb not installed on host); task file remains wip.
- T8 (d6b4fdad, Android Auto discovery verification on Android 16): not executed — Android Auto / head-unit emulator unavailable; task file remains wip.

## Issue #3 acceptance criteria status

- [x] Project builds cleanly with a current stable Android build toolchain.
- [x] Direct dependencies are on current stable releases where practical and compatible.
- [x] No unexplained legacy version pins remain.
- [ ] Existing phone playback behavior remains intact (pending T7).
- [ ] Android Auto discovers the sideloaded app with developer **Unknown sources** enabled (pending T8).
- [ ] Android Auto browse/playback/channel controls continue working (pending T8).
- [ ] CI/beta APK generation still works (partially verified locally; workflow_dispatch run pending T6).

## Retained note (Android Auto disappearance)

The random Android Auto disappearance on Android 16 was the trigger event for this issue but
remains observational and unresolved as a specific bug: the app disappeared from Android Auto
-> Customize launcher, then spontaneously reappeared without any app change. It was not tied
to a specific app bug; recorded as context in issue #3.

## Gap

The criterion "All remaining issue #3 task files (T2-T8) in `.agents/tasks/wip/` have
completed status and are archived to `.agents/tasks/done/`" is **not met**: T6/T7/T8
(e2752bd2, dcaae75d, d6b4fdad) are not completed and remain in wip with their own acceptance
criteria unmet. Issue #3 is left OPEN with this gap described in the issue comment; closure
depends on the verified outcomes of T6-T8.

## Acceptance criteria

- [x] Issue #3 comment(s) contain final versions and per-criterion verification results
- [x] Any deliberate version pin is documented with a reason (code comment + this task file)
- [ ] All remaining issue #3 task files (T2-T8) in `.agents/tasks/wip/` have completed status and are archived to `.agents/tasks/done/` — not met, see Gap
- [x] Issue #3 left open with the gap described (closure requires verified T6-T8 outcomes)

## Audit (2026-08-15)

Returned to wip. Documentation content verified (issue #3 comment, code-comment pins, build results), but the
third acceptance criterion is unmet: T6/T7/T8 (`e2752bd2`, `dcaae75d`, `d6b4fdad`) still sit in
`.agents/tasks/wip/` with `Status: wip` and their acceptance criteria unchecked. To pass: complete T6-T8,
archive their files to `.agents/tasks/done/`, then re-check this criterion.
