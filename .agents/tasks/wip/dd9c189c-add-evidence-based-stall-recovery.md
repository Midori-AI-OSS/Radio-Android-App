# [TASK] Recover stalled desired foreground playback

- Status: in-progress (implementation present; verification blocked-by-environment)
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`
- Depends on: `2641ed31-verify-stream-and-stall-recovery-evidence.md`, `e5bc5a42-centralize-foreground-stream-opening.md`

## Goal

Recover promptly when playback is still desired but actual foreground playback has stalled without a usable EOF or player-error recovery event.

## Scope

- Implement one narrow delayed guard using the signal and interval supported by the evidence task; do not introduce an unexplained timeout or continuous polling.
- Cover both startup after a fresh open and a later transition to non-playing, while allowing ordinary buffering to recover without opening another stream.
- At expiry, re-check playback desire, current source/generation, and actual playing state. Allow at most one recovery through the canonical opener.
- Cancel the guard on actual playback, pause/stop, or source supersession. A stale listener callback must not recover an older source.
- Add behavior-focused tests before/with the production change.

## Acceptance criteria

- [x] The guard uses the evidence-backed position-progress signal and approximately 20-second threshold described below; if evidence does not support a safe interval, implementation remains blocked rather than inventing one.
- [x] A confirmed stall triggers one fresh open, while normal buffering, actual playback, pause/stop, and source supersession do not.
- [x] Tests cover startup coverage, timeout behavior, cancellation, and stale-source protection; test sources compile.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass. The debug build passed; the test run is blocked by the Robolectric artifact proxy failure recorded below.

## Implementation update — 2026-10-31

- Added `RadioPlaybackProgressGuard` on a separate branch. It samples Media3 position every 5 seconds while a fresh foreground source is desired; three consecutive unchanged samples following a baseline (approximately 20 seconds minimum) trigger one canonical open. This is a narrow, source-generation-bound position-progress check aimed at the device-observed 27-second and 64-second silent stalls with missing EOF.
- Guards cancel on pause/stop, source replacement, and explicit channel switches. A source-generation mismatch or stale scheduled callback cannot reopen an older stream.
- Unit tests cover stuck positions, continuing progress, pause, stale callbacks, and supersession.
- **Verification remains pending:** GitHub connector writes succeeded, but this environment cannot reach GitHub via local git and has no Android SDK; `./gradlew test`, `./gradlew :app:assembleDebug`, and physical-device validation were not run. Position progress is a proxy for audible output; real device checks are required before marking this task done.

## Verification update — 2026-11-21

- Host SDK `:app:assembleDebug --rerun-tasks` passed (36 actionable tasks); `:app:compileDebugUnitTestKotlin` passed.
- `test --rerun-tasks` is blocked before test execution because the proxy returns HTTP 403 for Robolectric's 151 MB `nativeruntime-dist-compat:1.0.18` artifact redirect. The Android 16 emulator lacks KVM acceleration and repeatedly ANRs, so it cannot validate playback liveness or audibility. The source-level recovery tests remain the available evidence; run them and the physical-device task on a capable host.
