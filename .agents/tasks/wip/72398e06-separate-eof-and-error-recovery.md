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

- The recorded deployed HTTP sample confirms that a normal ready-track response body can be finite per track, so a desired-playback `STATE_ENDED` event immediately calls the canonical opener; EOF when paused/stopped does not reopen playback. This does not claim that Media3's device boundary behavior was observed: that remains `BLOCKED — unverified` pending the device task.
- The EOF event itself is immediate and does not consume an error attempt. If that immediate open returns `Failed`, treat the failed acquisition as an error and enter the delayed error-retry path. `Rejected` is not a failed acquisition and must not schedule a retry.
- Actual player/acquisition failures retry indefinitely. Use `50, 100, 200, 400, 800, 1500 ms`, then continue at `1500 ms`; there is no finite attempt limit. Keep one retry path and do not use a broad health check to erase the recovery cause.
- Cancel or invalidate retries on pause/stop and newer explicit channel/quality selections. Re-check current selection, source identity, and player state immediately before reopening.
- Preserve coroutine cancellation rather than converting cancellation into a failed open/retry.
- Add behavior-focused tests for production orchestration, not only isolated policy/coordinator helpers.

## Acceptance criteria

- [x] EOF handling follows a reviewable evidence-task classification. The EOF recovery admission is bound to the currently installed source/event identity rather than comparing a generation sampled twice in one callback, and execution rechecks current ended/source/selection state.
- [x] The normal EOF event calls the canonical opener immediately without incrementing error attempts. Only a `Failed` open enters error backoff; a `Rejected` open does not.
- [x] Player errors and every retryable failed open use the canonical opener and retry indefinitely with delay capped at `1500 ms`; stale retries cannot override a newer source. `Rejected` opens do not retry.
- [ ] Behavior tests exercise production listener/service wiring for EOF duplicate admission, player-error queuing, and actual retry survival/cancellation on restored-source READY, pause/stop, and newer selections; helper/coordinator-only coverage is insufficient.
- [x] Cancellation exceptions propagate through the installer/open path and do not become retryable `Failed` results; rollback/failure side effects on cancellation are explicitly tested.
- [x] `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test --rerun-tasks` and `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug --rerun-tasks` pass (fresh rerun 2026-10-25; 24 and 36 actionable tasks executed, respectively).
- [x] Device Media3 `Player` behavior at finite-response EOF is explicitly recorded as `BLOCKED — unverified`, with no runtime-success claim and follow-up in the separate device-verification task.

## Current implementation snapshot

- `PlayerEventListener.onPlaybackStateChanged(STATE_ENDED)` captures an event for the currently installed source. The recovery orchestrator admits that exact source/event identity once and rechecks current player state, channel/quality, playback intent, and opener availability before opening.
- A `Failed` immediate EOF open enters the error retry path. Error retry attempts are unbounded and delays cap at `1500 ms`.
- `RadioPlaybackRecoveryController` owns production EOF routing and the single delayed retry job. Player errors and failed opens from play, channel switches, and MediaSession selections route through it. However, `onPlaybackStateChanged()` marks any healthy READY/playing state recovered without checking that it belongs to the requested selection; a healthy prior source restored after a failed explicit channel/session open can cancel that target's retry.
- `RadioPlaybackPlayerEventListener` is the production Media3 listener seam. Tests execute its EOF route and unlimited capped backoff, but retry cancellation tests call controller/coordinator helpers directly; they do not exercise production listener/service wiring for `onPlayerError`, pause/stop, or newer-selection cancellation/invalidation.
- `FreshStreamPlayerInstaller` rollback is exercised through the production `openFreshStreamPlayerSource` path with `CancellationException`: player/service cleanup runs, the exact cancellation propagates, no retryable result is produced, and open admission is released.
- Terminal stale-event admission uses installed-source object identity. The unused source generation field was removed and stale-source events are tested. The repeated-event assertion does not directly test the same-source duplicate guard: the first open installs a new source, so the later callback is rejected as stale instead.
- Finite-body evidence is available through the pinned historical report at commit `cee9787f9fb163cdab11ead8041de1486f2308fa`, path `.agents/tasks/taskmaster/2641ed31-verify-stream-and-stall-recovery-evidence.md` (`git show cee9787f9fb163cdab11ead8041de1486f2308fa:.agents/tasks/taskmaster/2641ed31-verify-stream-and-stall-recovery-evidence.md`); that report is not present in the current task tree. The 2026-10-24 deployed sample completed normally after 1,032,600 bytes and `ffprobe` measured 85.920 seconds of `Chill Mix`; server revision remained unknown. Device Media3 `Player` EOF boundary: `BLOCKED — unverified`; follow-up is `.agents/tasks/wip/5a84a5c1-verify-foreground-audio-recovery-on-device.md`.
- Stall timeout: not selected; deferred to `dd9c189c-add-evidence-based-stall-recovery.md`.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test --rerun-tasks`: PASS on host SDK (fresh completion rerun 2026-10-25; 24 actionable tasks executed).
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug --rerun-tasks`: PASS on host SDK (fresh completion rerun 2026-10-25; 36 actionable tasks executed).
- Dockerized verification remains blocked: fresh `docker info` reports permission denied connecting to `/var/run/docker.sock`.

## Audit return — 2026-10-25

- Cover `PlayerEventListener` EOF routing and delayed-retry job cancellation/invalidation behavior on pause/stop and newer selections; current tests call the orchestrator or coordinator without exercising that production wiring.
- Route retryable `Failed` opens outside EOF through the same delayed retry path. `play()` currently ignores its open result, and channel-switch failure returns without queuing retry. Keep `Rejected` non-retryable, preserve latest-selection authority, and retain unlimited attempts with the `1500 ms` delay cap.
- Test `CancellationException` through the installer and `openFreshStream()` path. `FreshStreamPlayerInstaller.install()` catches `Throwable`, runs restore/`onFailed`, then rethrows; assert propagation, no retry conversion, and intended rollback effects.
- `InstalledSource.generation` is stored but unused; remove it or give it explicit tested semantics. Object identity currently binds terminal events to the installed source.
- Docker is unavailable because access to `/var/run/docker.sock` is denied; the two Gradle commands above passed using the available host SDK. The evidence report is retrievable from the pinned historical commit/path above, although it is absent from the current task tree.
- Keep the EOF event immediate; only a failed immediate EOF open enters error backoff. Do not add a finite attempt limit.

## Audit return — 2026-10-25 (follow-up)

- Prevent a healthy READY event from a restored prior source from cancelling the retry for a failed explicit channel/MediaSession selection. Cover the failed-open rollback plus READY event through production listener/service wiring, preserving the latest target as authoritative.
- Exercise production callback wiring for `onPlayerError` retry queuing and retry cancellation/invalidation on pause/stop and newer selections; current coverage directly calls controller/coordinator helpers or checks source text.
- Directly test duplicate admission by delivering the same captured terminal event twice while the installed source remains unchanged; the existing repeated-event test changes source during its first open and therefore tests stale-source rejection instead.
- Preserve unlimited retries with delays `50, 100, 200, 400, 800, 1500 ms`, continuing at `1500 ms`; do not add a finite attempt limit. The latest recorded host Gradle results remain applicable, and app code was not changed in this audit.
