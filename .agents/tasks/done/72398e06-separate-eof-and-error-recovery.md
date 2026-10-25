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

- The recorded deployed HTTP sample confirms that a normal ready-track response body can be finite per track, so a desired-playback `STATE_ENDED` event immediately calls the canonical opener; EOF when paused/stopped does not reopen playback. This does not claim that Media3's device boundary behavior was observed: that remains `BLOCKED — unverified` pending the device task.
- The EOF event itself is immediate and does not consume an error attempt. If that immediate open returns `Failed`, treat the failed acquisition as an error and enter the delayed error-retry path. `Rejected` is not a failed acquisition and must not schedule a retry.
- Actual player/acquisition failures retry indefinitely. Use `50, 100, 200, 400, 800, 1500 ms`, then continue at `1500 ms`; there is no finite attempt limit. Keep one retry path and do not use a broad health check to erase the recovery cause.
- Cancel or invalidate retries on pause/stop and newer explicit channel/quality selections. Re-check current selection, source identity, and player state immediately before reopening.
- Preserve coroutine cancellation rather than converting cancellation into a failed open/retry.
- Add behavior-focused tests for production orchestration, not only isolated policy/coordinator helpers.

## Acceptance criteria

- [x] EOF handling follows a reviewable evidence-task classification. The EOF recovery admission is bound to the currently installed source/event identity rather than comparing a generation sampled twice in one callback, and execution rechecks current ended/source/selection state.
- [x] The normal EOF event calls the canonical opener immediately without incrementing error attempts. Only a `Failed` open enters error backoff; a `Rejected` open does not.
- [x] Player errors and failed opens use the canonical opener and retry indefinitely with delay capped at `1500 ms`; stale retries cannot override a newer source.
- [x] The production-integrated orchestration seam tests desired vs paused/stopped EOF, source-bound stale/duplicate terminal events, failed/rejected opens, retry timing and repeat-at-cap behavior, healthy-state cleanup, and pause/stop or newer-selection invalidation without constructing a broad service framework.
- [x] Cancellation exceptions propagate through the coordinator/open path and do not become retryable `Failed` results.
- [x] `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test --rerun-tasks` and `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug --rerun-tasks` pass (fresh rerun 2026-10-25; 24 and 36 actionable tasks executed, respectively).
- [x] Device Media3 `Player` behavior at finite-response EOF is explicitly recorded as `BLOCKED — unverified`, with no runtime-success claim and follow-up in the separate device-verification task.

## Current implementation snapshot

- `PlayerEventListener.onPlaybackStateChanged(STATE_ENDED)` captures an event for the currently installed source. The recovery orchestrator admits that exact source/event identity once and rechecks current player state, channel/quality, playback intent, and opener availability before opening.
- A `Failed` immediate EOF open enters the error retry path. Error retry attempts are unbounded and delays cap at `1500 ms`.
- A healthy error signal and a healthy delayed-retry check call `markPlaybackRecovered()`; retries are invalidated through the coordinator on pause/selection changes.
- `RadioPlaybackRecoveryOrchestratorTest` covers the production-integrated seam from installed-source terminal admission through immediate open/error fallback, duplicate/stale suppression, capped unbounded retry state, cleanup/cancellation, and cancellation propagation.
- Finite-body evidence is preserved at commit `cee9787f9fb163cdab11ead8041de1486f2308fa`, path `.agents/tasks/taskmaster/2641ed31-verify-stream-and-stall-recovery-evidence.md` (`git show cee9787f9fb163cdab11ead8041de1486f2308fa:.agents/tasks/taskmaster/2641ed31-verify-stream-and-stall-recovery-evidence.md`). The 2026-10-24 deployed sample completed normally after 1,032,600 bytes and `ffprobe` measured 85.920 seconds of `Chill Mix`; server revision remained unknown. Device Media3 `Player` EOF boundary: `BLOCKED — unverified`; follow-up is `.agents/tasks/wip/5a84a5c1-verify-foreground-audio-recovery-on-device.md`.
- Stall timeout: not selected; deferred to `dd9c189c-add-evidence-based-stall-recovery.md`.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test --rerun-tasks`: PASS (fresh rerun 2026-10-25; 24 actionable tasks executed).
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug --rerun-tasks`: PASS (fresh rerun 2026-10-25; 36 actionable tasks executed).

## Audit follow-up — 2026-10-25

- Bind terminal recovery to the source/event identity rather than using the callback-time `activeSourceGeneration`; retain the current-player ended/source/selection checks.
- Add orchestration coverage for terminal-event admission, failed-open retry scheduling, duplicate/stale events, and retry cleanup/cancellation.
- In `openFreshStream()`, rethrow `CancellationException` before catching other `RuntimeException`s; add a test proving cancellation is not converted to `FreshStreamOpenResult.Failed`.
- Keep the retry requirement explicitly unbounded with a `1500 ms` delay cap. The EOF event remains immediate; only a failed immediate open is an error retry. Do not add a max-attempt limit.
- Link the evidence used to classify finite responses and retain the device-boundary limitation as unverified until the device task records it.
