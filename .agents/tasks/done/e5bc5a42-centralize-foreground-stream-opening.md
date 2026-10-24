# [TASK] Centralize foreground stream opening and selection

- Status: done
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`
- Depends on: `2641ed31-verify-stream-and-stall-recovery-evidence.md`

## Goal

Make one service path responsible for admitting each foreground stream open/reopen. It must open the currently selected channel at the effective quality, and stale or overlapping work must not replace a newer source.

## Scope

- Route initial play, explicit channel changes, and existing recovery/open entry points through one fresh-stream opener. A quality change uses this opener when existing active/pending-quality semantics require a reconnect; do not make a playing quality change reconnect immediately solely as part of this task.
- Resolve the selected channel and effective active/pending quality when an open is admitted, rather than capturing them when delayed work is scheduled.
- Invalidate or cancel stale delayed work using a generation/token or equivalent current-source check. Since player callbacks do not identify their originating source, callback/retry work must be checked against playback desire, the current connection, and the current selection before it can reopen.
- Make source-replacement/prepare admission single-flight (not serialization behind the lifetime of the active stream). A newer explicit selection may supersede pending work, but older work must not open afterward. Coordinate admission with the 220 ms channel-switch fade so a retry cannot overlap the switch, undo its volume handling, or restore an older channel/quality.
- Keep MediaSession/Android Auto-initiated foreground playback on the same opener path while preserving the MediaSession callback's API-required return shape and browse behavior. Recovery trigger/timing policy remains in the linked recovery tasks; this task centralizes and guards the open operation.
- Simplify one-item playlist/index/transition recovery machinery only where it exists to support the old stream-opening flow. Preserve MediaSession API requirements, Android Auto browse behavior, and adjacent-channel selection.

## Acceptance criteria

- [x] All foreground stream source replacements, including session-originated playback and recovery opens, converge on one canonical service-side opener.
- [x] A stale callback/retry, or rapid channel/quality changes, cannot open an older selection over the current one; selection and effective quality are resolved at open admission using existing active/pending-quality semantics.
- [x] At most one source-replacement/prepare transaction is admitted at a time. A newer explicit selection supersedes older pending work, and opening/retry work cannot race the channel fade into duplicate connections, incorrect selection, or incorrect volume restoration.
- [x] Behavior-focused tests demonstrate single-flight admission, latest effective channel/quality winning after rapid changes, stale callback/retry rejection, retry/fade overlap, and pause/stop invalidation of delayed opening work.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass (blocked: Android SDK location is not configured in this checkout).

## Audit findings (2026-10-24)

- `SessionCallback.onSetMediaItems()` returns a selected item list for MediaSession to install, while `selectChannelImmediately()` starts `switchChannelWithFade()`, which later calls `openFreshStream()` and installs/prepares again. Coordinate these paths so session-originated selection admits one source replacement through the canonical opener while preserving the callback return contract.
- `openFreshStream()` does not own volume restoration; it was removed from the shared open path and is now split across `play()`, `markPlaybackRecovered()`, `pausePlayback()`, and the switch-fade continuation. Give each open/fade transaction one guaranteed volume-restoration owner, including interrupted/superseded recovery paths, and test it.
- `FreshStreamOpenCoordinatorTest` covers coordinator predicates and selection resolution, but not the MediaSession/switch integration, rapid channel/quality changes, or volume restoration. Add behavior-focused coverage for those paths and stale callback/retry rejection.
- `./gradlew test`: BLOCKED — SDK location not found (`ANDROID_HOME` or `local.properties` `sdk.dir` is not configured).
- `./gradlew :app:assembleDebug`: BLOCKED — same SDK-location error. Docker verification was unavailable because access to `/var/run/docker.sock` was denied. A copied-tree “42 tasks up-to-date” result is not fresh verification of this checkout.

## Completion notes (2026-10-24)

- Session selection now preserves the MediaSession callback return contract without starting a parallel fade/open transaction.
- Fresh opens restore normal volume in their `finally` block; fades own restoration only while their generation remains current, including cancellation and supersession.
- Added coordinator predicate tests and source-text checks for selected service-path structure; these do not verify the listed runtime behavior.
- `./gradlew test` and `./gradlew :app:assembleDebug` remain blocked by the missing Android SDK location.

## Re-audit findings (2026-10-24)

- `SessionCallback.onSetMediaItems()` returns `playbackItems` and selects with `openStream = false`; MediaSession installs the returned items outside `openFreshStream()`. Coordinate the required callback result with canonical open admission so session playback has exactly one source replacement.
- `selectChannelImmediately()` does not invalidate pending reconnect work. Invalidate or reconcile retries on selection changes, including session-originated selections.
- `switchChannelWithFade()` proceeds to fade up and poll even when `openFreshStream()` silently rejects admission because another open is active. Return an admission/result outcome or coordinate the transaction so a rejected open cannot be treated as a successful switch.
- Replace source-text assertions with behavioral coverage for session/open integration, selection invalidating retries, rejected switch opens, and actual volume restoration across cancellation/supersession. Current tests exercise coordinator helpers but do not prove these service interactions.
- The single-item playlist still has target-index seek and automatic-transition restore machinery. Review which parts remain necessary, simplifying only obsolete open-flow recovery while preserving browse behavior and adjacent-channel selection.
- Gradle verification remains blocked by the missing Android SDK location; Docker verification is unavailable because access to `/var/run/docker.sock` is denied.

## Final implementation notes (2026-10-24)

- `RadioSessionPlayer` now intercepts the MediaSession item installation and follow-up prepare operation. The returned callback list is preserved, while the service installs and prepares the selected source through `openFreshStream()` exactly once.
- Explicit channel and quality selections synchronously invalidate delayed reconnect admission. Session selection also cancels an active fade and restores normal volume before the canonical open.
- Fresh-open admission now returns `Opened` or `Rejected`; a rejected open cannot continue channel-switch fade-up or polling. Coordinator behavior tests cover reentrant rejection, stale retry invalidation, latest selection/quality resolution, fade overlap, and volume ownership on cancellation and supersession.
- Removed the obsolete one-item active-playlist policy, target-index reconnect seeking, and automatic playlist-transition restoration. Browse items and adjacent-channel commands remain unchanged.
- Replaced the foreground-open source-text integration test with executable coordinator and forwarding-player behavior tests.
- `git diff --check`: passed.
- `./gradlew test --offline --console=plain`: blocked before task execution because no Android SDK location is configured.
- `./gradlew :app:assembleDebug --offline --console=plain`: blocked for the same missing Android SDK location. Docker remains unavailable to this user.
