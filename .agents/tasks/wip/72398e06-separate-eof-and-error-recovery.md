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

- Use the stream-end classification recorded by the evidence task; do not infer it. If normal finite per-track completion is confirmed, desired-playback EOF immediately continues through the canonical opener, while EOF when paused/stopped does not reopen playback. If evidence records another usable classification, implement it as documented. If classification remains unknown or blocked, leave EOF handling blocked and do not assume per-track completion.
- Keep actual player/acquisition errors on one bounded delayed-retry path. Preserve or adjust the existing backoff only with a stated reason; do not route EOF through error backoff or use a broad health check to erase the cause.
- Cancel or invalidate retries on pause/stop and newer explicit channel/quality selections. Re-check current selection and source generation immediately before reopening.
- Add behavior-focused tests before/with the corresponding production change.

## Acceptance criteria

- [ ] EOF handling follows the evidence task's recorded classification and is separate from actual error retries; if classification remains unknown or blocked, EOF handling is explicitly blocked rather than based on an assumption.
- [ ] Error recovery remains bounded and uses the canonical opener; stale retries cannot override a newer source.
- [ ] Tests cover desired vs paused/stopped EOF behavior as defined by the recorded classification, the bounded retry-delay schedule, cancellation on pause/stop, and invalidation after a newer channel/quality selection.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass.
