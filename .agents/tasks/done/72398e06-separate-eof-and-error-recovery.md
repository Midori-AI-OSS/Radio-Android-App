# [TASK] Separate normal stream-end and player-error recovery

- Status: done
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

- [x] EOF handling follows the evidence task's recorded classification and is separate from actual error retries; if classification remains unknown or blocked, EOF handling is explicitly blocked rather than based on an assumption.
- [x] Error recovery remains bounded and uses the canonical opener; stale retries cannot override a newer source.
- [x] Tests cover desired vs paused/stopped EOF behavior as defined by the recorded classification, the bounded retry-delay schedule, cancellation on pause/stop, and invalidation after a newer channel/quality selection.
- [x] `./gradlew test` and `./gradlew :app:assembleDebug` pass.

## Completion

- Desired-playback `STATE_ENDED` now immediately uses the canonical fresh-stream opener after current-source and generation checks; paused/stopped EOF does nothing.
- Player errors retain the capped `50, 100, 200, 400, 800, 1500 ms` delayed retry schedule, coalesce duplicate pending retries, and retain generation invalidation checks before reopening.
- Added focused EOF and retry-schedule policy tests; existing coordinator coverage verifies pause/stop and newer selection invalidation.
- Stall timeout: not selected; deferred to `dd9c189c-add-evidence-based-stall-recovery.md`.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test`: PASS.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug`: PASS.
