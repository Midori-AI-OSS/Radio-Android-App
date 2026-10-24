# [TASK] Separate normal stream-end and player-error recovery

- Status: ready
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`
- Depends on: `2641ed31-verify-stream-and-stall-recovery-evidence.md`, `e5bc5a42-centralize-foreground-stream-opening.md`

## Goal

Handle normal stream completion and actual player failures as distinct causes, while keeping pause/stop and newer selections authoritative.

## Scope

- Use the stream-end decision documented by the evidence task. Only if normal finite per-track completion is confirmed, treat desired-playback EOF as immediate continuation through the canonical opener; EOF while paused/stopped must not resume playback. If evidence differs, implement the documented classification instead.
- Keep actual player/acquisition errors on one bounded delayed-retry path. Preserve or adjust the existing backoff only with a stated reason; do not route EOF through error backoff or use a broad health check to erase the cause.
- Cancel or invalidate retries on pause/stop and newer explicit channel/quality selections. Re-check current selection and source generation immediately before reopening.
- Add behavior-focused tests before/with the corresponding production change.

## Acceptance criteria

- [ ] EOF behavior matches the verified protocol decision and is separate from actual error retries.
- [ ] Error recovery remains bounded and uses the canonical opener; stale retries cannot override a newer source.
- [ ] Tests cover desired vs paused/stopped EOF, bounded error retry, and cancellation/supersession.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass.
