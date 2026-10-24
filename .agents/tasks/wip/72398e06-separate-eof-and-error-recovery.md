# [TASK] Separate normal stream-end and player-error recovery

- Status: wip
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

- [ ] EOF handling follows the evidence task's recorded classification and is separate from actual error retries; the event must be tied to the source generation rather than sampling the current generation in the callback.
- [ ] Error recovery remains bounded and uses the canonical opener; failed opens are handled and stale retries cannot override a newer source.
- [ ] Tests cover desired vs paused/stopped EOF behavior, a finite retry budget, failed opens, duplicate retry coalescing/cancellation, and pause/stop or newer-selection invalidation through production orchestration.
- [x] `./gradlew test` and `./gradlew :app:assembleDebug` pass.

## Current implementation snapshot

- Desired-playback `STATE_ENDED` immediately calls the canonical opener after a callback-time generation comparison; paused/stopped EOF does nothing.
- Player errors retain the capped `50, 100, 200, 400, 800, 1500 ms` delayed retry schedule, coalesce duplicate pending retries, and retain generation invalidation checks before reopening.
- EOF policy and retry-schedule tests pass, as do the coordinator's generic pause/stop and newer-selection invalidation tests; service callback wiring and duplicate retry cancellation are not covered.
- Protocol evidence confirms finite HTTP bodies correspond to the remaining track duration; no device Media3 EOF boundary has been observed, so runtime behavior remains unverified.
- Stall timeout: not selected; deferred to `dd9c189c-add-evidence-based-stall-recovery.md`.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test`: PASS.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug`: PASS.

## Required follow-up

- Bind EOF recovery to the source generation that produced the event; the current callback samples the current generation for both sides of its equality check.
- Handle `FreshStreamOpenResult.Failed` in both EOF and error recovery rather than silently ending recovery.
- Bound the number of error retry attempts; capping delay does not bound retries.
- Clear retry/reconnecting state when the delayed health check finds playback healthy.
- Add orchestration-level tests for stale EOF, failed opens, duplicate errors, and retry cancellation/state cleanup.
