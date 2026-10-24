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

- [x] EOF handling follows the evidence task's recorded classification and is separate from actual error retries; the event is tied to the installed source generation and rechecks the player's current ended/source state rather than sampling a generation inside the callback.
- [x] Error recovery uses the canonical opener and the capped `50, 100, 200, 400, 800, 1500 ms` delay schedule; failed opens are handled and stale retries cannot override a newer source.
- [x] Tests cover desired vs paused/stopped EOF behavior, capped retry timing, failed opens, duplicate/stale terminal-event coalescing, healthy error signals, and delayed retry invalidation.
- [x] `./gradlew test` and `./gradlew :app:assembleDebug` pass.

## Current implementation snapshot

- Desired-playback `STATE_ENDED` rechecks the player's actual ended state and current channel/quality source, coalesces duplicate terminal callbacks by the installed source generation, and immediately calls the canonical opener. Paused/stopped EOF does nothing.
- A failed immediate EOF open enters the same capped-delay error retry path as player/acquisition failures. Retry attempts remain unbounded while delays cap at `1500 ms`; no max-attempt limit was added.
- A player error delivered after playback is already healthy cancels recovery and restores `Playing`; delayed retries perform the same health check before opening.
- New channel/quality selections cancel terminal/retry work and unhealthy quality changes queue fresh generation-bound recovery.
- Deterministic policy/coordinator tests cover EOF versus error timing, failed EOF opens, healthy error signals, and duplicate/stale events.
- Deployed finite-response-body evidence exists and supports finite per-track content. Device Media3 `Player` boundary behavior remains unverified.
- Stall timeout: not selected; deferred to `dd9c189c-add-evidence-based-stall-recovery.md`.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test`: PASS (fresh, 2026-10-24).
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug`: PASS (fresh, 2026-10-24).

## Audit resolution

- Removed the ineffective callback-time generation comparison. The service records the generation admitted for the installed source and uses it only to coalesce terminal opens while validating current player/source state at execution.
- `FreshStreamOpenResult.Failed` from immediate EOF or delayed error opening queues the next capped-delay retry.
- Delay values are bounded at `1500 ms`; retry attempts intentionally have no maximum, per the approved requirement.
- Healthy immediate error signals and delayed health checks clear retry state and publish `Playing`.
- Added deterministic behavior coverage for stale/current EOF state, terminal coalescing/cancellation, failed-open follow-up, healthy error handling, and terminal versus delayed admission.
