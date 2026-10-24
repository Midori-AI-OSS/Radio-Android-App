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
- [x] Behavior-focused tests demonstrate single-flight admission, latest effective channel/quality winning after rapid changes, stale callback/retry rejection, retry/fade overlap, pause/stop invalidation of delayed opening work, actual MediaSession/controller handling of exposed item mutations, and consistent state after post-admission exceptions.
- [x] `./gradlew test` and `./gradlew :app:assembleDebug` pass.

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

## Re-audit (2026-10-24)

- Remains WIP: a rejected switch open returns before resetting `_playbackState`, so it can remain `SwitchingChannel`; tests cover coordinator/forwarding behavior but not service-level MediaSession installation or actual player-volume restoration. Add service-level coverage and ensure rejected switches leave a consistent playback state. Both Gradle checks remain blocked by the missing Android SDK.

## Re-audit closure (2026-10-24)

- Current rejected switch opens now restore normal player volume and replace `SwitchingChannel` with `Connecting` while playback is still desired; stale switch completions cannot overwrite newer state.
- A service-owned MediaSession opening collaborator now joins callback selection/result construction to the wrapped player's canonical installation callback. Behavioral coverage verifies one canonical open and no delegated source replacement or prepare.
- Player-effect tests use a `Player` fake to verify real volume writes for failed opens, switch cancellation, and selection supersession, along with the rejected-switch playback-state transition.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk GRADLE_USER_HOME=/tmp/gradle ./gradlew test --console=plain`: passed. The SDK remains unconfigured in the ambient checkout environment, so the explicit SDK variables are required.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk GRADLE_USER_HOME=/tmp/gradle ./gradlew :app:assembleDebug --console=plain`: passed.
- Docker verification remains blocked because this user cannot access `/var/run/docker.sock`.

## Re-audit (2026-10-24)

- Remains WIP: Media3 1.11.0's session fallback applies the callback result with `setMediaItem(...)` when `COMMAND_CHANGE_MEDIA_ITEMS` is unavailable. `RadioSessionPlayer` intercepts only `setMediaItems(List, int, long)`; inherited item-setting overloads delegate directly, while `prepare()` is always a no-op. Route fallback installation and preparation through the canonical opener, and add service/session-level coverage for fallback plus actual player source/volume effects.
- `openFreshStream()` cancels the pending reconnect before attempting coordinator admission, so a rejected open can discard a retry. Preserve the retry when admission is rejected and cover this case behaviorally.
- The Gradle test and assemble commands above passed on this checkout; Gradle reported tasks up-to-date.

## Completion (2026-10-24)

- All six Media3 `setMediaItem`/`setMediaItems` overloads now invoke the canonical service opening callback. The wrapper suppresses only the prepare paired with an intercepted installation and delegates an ordinary later `prepare()` to the player.
- Pending reconnect cancellation now occurs only after fresh-open admission, so a rejected open leaves a valid queued retry intact.
- Added executable wrapper/service-facing tests for every installation overload, fallback `setMediaItem(...)`, canonical source preparation and volume restoration, ordinary prepare delegation, and rejected-open retry preservation.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk GRADLE_USER_HOME=/tmp/gradle ./gradlew test --console=plain`: passed.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk GRADLE_USER_HOME=/tmp/gradle ./gradlew :app:assembleDebug --console=plain`: passed.

## Re-audit (2026-10-24)

- `RadioSessionPlayer` leaves `canonicalPreparePending` set after an intercepted installation until a `prepare()` arrives, so an unmatched installation can suppress a later unrelated prepare. Replace the sticky pairing state with a safe pairing mechanism and test both the unmatched-prepare and callback-exception paths.
- `resolvePlaybackItems()` calls `selectChannelImmediately()` before MediaSession applies the returned items, changing selection, persistence, and retry/fade state before installation. Move those effects to an accepted installation/open boundary and cover rejected or unapplied results.
- Current tests use fake players and collaborators, not the actual service/MediaSession boundary. Add focused integration coverage for prepare pairing and selection/open state effects.

## Closure (2026-10-24)

- Removed prepare-suppression state. Both ordinary and MediaSession-paired prepares now delegate normally; canonical source replacement remains intercepted, and callback exceptions cannot affect a later prepare.
- Session callback resolution is side-effect free. Selection is derived from the items Media3 actually installs, committed only after canonical open admission and successful source preparation, and rejected installation is surfaced as a failed player operation.
- Added focused executable coverage across the session-opening collaborator and forwarding player for ordinary and paired prepare, unapplied results, admission rejection, resolution/open exceptions, fallback item installation, source preparation, and volume effects without adding a new test framework.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk GRADLE_USER_HOME=/tmp/gradle ./gradlew test --console=plain`: passed.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk GRADLE_USER_HOME=/tmp/gradle ./gradlew :app:assembleDebug --console=plain`: passed.

## Re-audit (2026-10-24)

- Remains WIP: `SessionCallback.onConnect()` exposes `DEFAULT_PLAYER_COMMANDS`, but `RadioSessionPlayer` intercepts only `setMediaItem(s)`. Playlist mutations (`add`, `replace`, `remove`, `clear`) can reach ExoPlayer directly and bypass canonical open admission. Restrict those commands or route exposed mutations through the canonical opener, and cover the behavior with an actual MediaSession/controller integration test.
- Add post-admission exception coverage verifying selection/playback state and player effects remain consistent when admitted installation or preparation throws.

## Final closure (2026-10-24)

- MediaSession connections now expose a radio-specific player command set. Single-item selection remains available for Android Auto and routes through canonical resolution/opening; list/add/replace/remove/clear mutations are unavailable and cannot reach ExoPlayer.
- Added a Robolectric MediaSession/MediaController integration test covering every permitted set overload, canonicalization of arbitrary URI-bearing input, unavailable playlist mutation entry points, and preserved adjacent-channel commands.
- Admitted source installation and preparation failures restore the prior player item, play intent, quality/cache state, volume ownership, and a non-switching playback state. Failed opens now have an explicit result and cannot continue a channel switch.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk GRADLE_USER_HOME=/tmp/gradle ./gradlew test --console=plain`: passed.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk GRADLE_USER_HOME=/tmp/gradle ./gradlew :app:assembleDebug --console=plain`: passed.
