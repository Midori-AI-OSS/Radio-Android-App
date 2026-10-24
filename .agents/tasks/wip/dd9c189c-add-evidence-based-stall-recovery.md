# [TASK] Recover stalled desired foreground playback

- Status: ready
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

- [ ] The guard uses the evidence-backed signal/interval; if evidence does not support a safe interval, implementation remains blocked rather than inventing one.
- [ ] A confirmed stall triggers one fresh open, while normal buffering, actual playback, pause/stop, and source supersession do not.
- [ ] Tests cover startup coverage, timeout behavior, cancellation, and stale-source protection.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass.
