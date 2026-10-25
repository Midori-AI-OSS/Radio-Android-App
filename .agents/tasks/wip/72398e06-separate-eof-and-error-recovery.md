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

- Use the stream-end classification recorded by the evidence task; do not infer it. If normal finite per-track completion is confirmed, desired-playback EOF immediately calls the canonical opener, while EOF when paused/stopped does not reopen playback. If classification remains unknown or blocked, leave EOF handling blocked.
- The EOF event itself is immediate and does not consume an error attempt. If that immediate open returns `Failed`, treat the failed acquisition as an error and enter the delayed error-retry path. `Rejected` is not a failed acquisition and must not schedule a retry.
- Actual player/acquisition failures retry indefinitely. Use `50, 100, 200, 400, 800, 1500 ms`, then continue at `1500 ms`; there is no finite attempt limit. Keep one retry path and do not use a broad health check to erase the recovery cause.
- Cancel or invalidate retries on pause/stop and newer explicit channel/quality selections. Re-check current selection, source identity, and player state immediately before reopening.
- Preserve coroutine cancellation rather than converting cancellation into a failed open/retry.
- Add behavior-focused tests for production orchestration, not only isolated policy/coordinator helpers.

## Acceptance criteria

- [ ] EOF handling follows a reviewable evidence-task classification. The EOF recovery admission is bound to the source generation that produced the event, not a generation sampled when the callback runs, and execution rechecks current ended/source/selection state.
- [ ] The normal EOF event calls the canonical opener immediately without incrementing error attempts. Only a `Failed` open enters error backoff; a `Rejected` open does not.
- [ ] Player errors and failed opens use the canonical opener and retry indefinitely with delay capped at `1500 ms`; stale retries cannot override a newer source.
- [ ] Behavior tests exercise service/orchestration wiring for desired vs paused/stopped EOF, source-bound stale/duplicate terminal events, failed/rejected opens, retry timing and repeat-at-cap behavior, healthy-state cleanup, and pause/stop or newer-selection invalidation.
- [ ] Cancellation exceptions propagate through the open path and do not become retryable `Failed` results.
- [x] `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test` and `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug` pass (rerun 2026-10-25; Gradle reported tasks up-to-date).
- [x] Device Media3 `Player` behavior at finite-response EOF is explicitly recorded as `BLOCKED — unverified`, with no runtime-success claim and follow-up in the separate device-verification task.

## Current implementation snapshot

- `PlayerEventListener.onPlaybackStateChanged(STATE_ENDED)` calls `continueAfterEndOfStream()`, which checks current player state and channel/quality. However, it reads `activeSourceGeneration` inside that callback and uses the sampled value for coalescing; the terminal event is not bound to the source generation that produced it.
- A `Failed` immediate EOF open enters the error retry path. Error retry attempts are unbounded and delays cap at `1500 ms`.
- A healthy error signal and a healthy delayed-retry check call `markPlaybackRecovered()`; retries are invalidated through the coordinator on pause/selection changes.
- Current recovery tests exercise policy/coordinator helpers. No test drives the service's player-listener-to-opener/retry wiring or verifies service retry-state cleanup. `MediaSessionMutationIntegrationTest` covers session mutation, not recovery orchestration.
- The task snapshot records deployed finite-response-body evidence, but the referenced evidence task is not present in the current `.agents/tasks` tree. Device Media3 `Player` EOF boundary: `BLOCKED — unverified`; follow-up is in `.agents/tasks/wip/5a84a5c1-verify-foreground-audio-recovery-on-device.md`.
- Stall timeout: not selected; deferred to `dd9c189c-add-evidence-based-stall-recovery.md`.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test`: PASS (rerun 2026-10-25; tasks up-to-date).
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug`: PASS (rerun 2026-10-25; tasks up-to-date).

## Audit follow-up — 2026-10-25

- Bind terminal recovery to the source/event identity rather than using the callback-time `activeSourceGeneration`; retain the current-player ended/source/selection checks.
- Add orchestration coverage for terminal-event admission, failed-open retry scheduling, duplicate/stale events, and retry cleanup/cancellation.
- In `openFreshStream()`, rethrow `CancellationException` before catching other `RuntimeException`s; add a test proving cancellation is not converted to `FreshStreamOpenResult.Failed`.
- Keep the retry requirement explicitly unbounded with a `1500 ms` delay cap. The EOF event remains immediate; only a failed immediate open is an error retry. Do not add a max-attempt limit.
- Link the evidence used to classify finite responses and retain the device-boundary limitation as unverified until the device task records it.
