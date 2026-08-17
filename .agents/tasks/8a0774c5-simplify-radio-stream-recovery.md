# [TASK] Fix silent radio playback by simplifying finite-stream recovery

- Status: ready
- Priority: high
- Source: user-reported Android playback regression investigated across `Radio-Android-App`, `website-blog`, and `Cookie-Club-Bots`
- Owner: coder
- Related task: `.agents/tasks/wip/dcaae75d-verify-phone-playback.md`
- Created: 2026-08-17

## Goal

Fix the Android radio failure where playback can go silent while the app continues to look healthy: the play state remains active and `/current` + artwork polling continue to advance to later songs, but no audio is heard.

Do this by simplifying the Android audio-stream lifecycle so it matches the actual radio protocol:

1. one selected channel = one active Media3 `MediaItem`;
2. one function/path opens a fresh `/radio/v1/stream` request;
3. normal end-of-stream immediately opens a fresh request;
4. actual playback errors use one bounded delayed retry path;
5. if playback is still desired but Media3 stops actually playing without EOF/error, one narrow liveness guard opens a fresh request;
6. metadata/art/session updates remain independent of the audio connection and must not rebuild or mutate the playback source.

The desired result is **less playback state machinery than exists today**, not another recovery subsystem layered on top of the existing playlist/reconnect code.

## User-visible bug to reproduce / reason about first

Reported behavior:

- Start the radio normally.
- Audio plays.
- At some later point, often around a track transition or stream interruption, audio stops.
- The app still behaves as though playback is active.
- Song title, progress-derived presentation, and artwork continue changing to later songs because metadata polling is still alive.
- No audio returns unless the stream is manually restarted.

This symptom is important: metadata progression is **not evidence that the audio HTTP response or decoder is healthy**. The app polls metadata separately from Media3 playback.

Before changing code, confirm the current behavior from source and, when a device is available, reproduce it with logcat/state observations. Do not assume the public `RadioPlaybackState.Playing` value means `player.isPlaying == true` at the time of failure; today that state can remain stale because false `isPlaying` transitions do not themselves trigger recovery.

## Confirmed cross-repository protocol/behavior

Re-check these paths at task start in case `main` has changed, but treat the following as the current architectural baseline.

### Radio server: the normal stream response is finite

Repository: `Midori-AI-OSS/Cookie-Club-Bots`

Relevant paths:

- `Webserver-RS/crates/bin/src/routes.rs`
- `Webserver-RS/crates/radio/src/transcode.rs`

`GET /radio/v1/stream` asks the station for a `StreamSource`.

For the normal ready-state source, `StreamSource::CachedTrack { bytes, position_ms }`, the route calls `transcode_from_memory(bytes, position_ms, bitrate_kbps)`.

`transcode_from_memory`:

- starts ffmpeg;
- seeks to the current station position with `-ss`;
- writes the bytes for the current cached track to ffmpeg stdin;
- closes stdin after those bytes are written;
- streams ffmpeg MP3 stdout as the HTTP response body;
- ends when that track/transcoder output ends.

The warmup-file source is explicitly looping, but normal cached-track playback is not one permanent station connection.

**Consequence:** normal song-boundary EOF is part of the client protocol. A client must open a new stream request for the next current track. EOF is not equivalent to a network failure and should not wait behind generic error backoff/health logic.

### Website: use a fresh stream request on `ended`

Repository: `Midori-AI-OSS/website-blog`

Relevant paths:

- `app/radio/RadioPageClient.tsx`
- `app/api/radio/stream/route.ts`

The website keeps playback intentionally simple:

- creates one `HTMLAudioElement`;
- assigns a stream URL;
- calls `load()` + `play()`;
- on the audio element's `ended` event, increments a restart nonce;
- the nonce causes `startPlayback()` to assign a fresh stream URL and issue a new request.

The website's `/api/radio/stream` route simply proxies the upstream radio response body and marks it non-cacheable.

Metadata polling is separate from the `<audio>` element lifecycle.

Use this as the conceptual reference for Android. Do not copy browser-specific mechanics; copy the separation of concerns and fresh-request-on-EOF behavior.

### Android: current playlist/reconnect machinery is larger than the problem

Repository: this repo.

Relevant production paths:

- `app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackService.kt`
- `app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackReconnectPolicy.kt`
- `app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackActivePlaylistPolicy.kt`
- nearby playback policy/helper files that are only supporting the current playlist/reconnect design

Relevant tests:

- `app/src/test/java/xyz/midoriai/radio/playback/RadioPlaybackReconnectPolicyTest.kt`
- `app/src/test/java/xyz/midoriai/radio/playback/RadioPlaybackReconnectScheduleTest.kt`
- `app/src/test/java/xyz/midoriai/radio/playback/RadioPlaybackActivePlaylistPolicyTest.kt`
- `app/src/test/java/xyz/midoriai/radio/playback/RadioPlaybackPauseStopTest.kt`
- `app/src/test/java/xyz/midoriai/radio/playback/RadioPlaybackTransitionPolicyTest.kt`
- `app/src/test/java/xyz/midoriai/radio/playback/RadioSessionPlayerTest.kt`

Current facts to verify before editing:

- `resolveActivePlayerPlaylistPolicy(...)` always returns exactly one channel and `startIndex = 0`.
- `connectToSelectedStream(...)` still goes through playlist synchronization, indices, target seeking, prepare, and reconnect-generation logic.
- `Player.STATE_ENDED` currently enters `queueReconnect("Stream ended")` rather than a dedicated normal-EOF restart path.
- `queueReconnect(...)` uses the same health/retry machinery for EOF and exceptional recovery.
- `onIsPlayingChanged(false)` does not currently schedule recovery while playback is desired; only the `true` branch marks playback recovered.
- `_playbackState` can therefore remain `Playing` after Media3 stops actually playing unless an error/ended callback drives another state change.
- `syncCurrentMediaItemMetadata()` rebuilds a complete `MediaItem` through `buildMediaItem(...)` and calls `replaceMediaItem(...)`, even though only presentation metadata needs to change.

Media3 1.11.0 is already used by this repo. Its `Player.isPlaying` semantic is appropriate for the narrow liveness guard: it represents actual active playback/current-position advancement, not merely the app's desire to play. Do not use `_playbackState == Playing` as the liveness signal.

## Required architecture after the fix

The exact private function names are not prescribed, but the resulting lifecycle must be equivalent to this:

```text
user/session requests playback
        |
        v
open fresh selected stream
  - resolve selected channel
  - resolve active/pending quality
  - build exactly one MediaItem
  - install it as the player's active item
  - prepare
  - request play
  - arm narrow startup/liveness guard
        |
        +----------------------------+
        |                            |
        v                            v
Media3 actually plays          Media3 never/resumes no playback
        |                            |
        |                       liveness timeout
        |                            |
        |                       fresh stream request
        |
        +--> normal EOF ------------------------------+
        |                                             |
        |                                      immediate fresh request
        |
        +--> player error ----------------------------+
                                                      |
                                             bounded delayed retry
```

There should be **one canonical fresh-stream opener/restart primitive** used by:

- initial play;
- channel changes;
- quality changes when they require reconnecting;
- normal EOF restart;
- error retry execution;
- liveness recovery.

Do not maintain separate implementations that each rebuild the player differently.

## Scope

### 1. First write/adjust tests that describe the desired lifecycle

Follow verification-first/TDD behavior from `AGENTS.md` and `CODER.md`.

Before production edits, capture the lifecycle decisions in focused unit tests. Reuse or replace the existing small playback policy helpers; do **not** create a large state-machine abstraction only to make tests possible.

Tests must cover at least these cases:

- normal EOF while playback is desired selects **immediate restart**, not the error backoff path;
- EOF while playback is stopped/paused does not resurrect playback;
- a real player error while playback is desired schedules bounded retry;
- a queued delayed error retry is cancelled/ignored after a newer connection/channel change supersedes it;
- successful actual playback cancels pending liveness recovery and resets error retry state;
- playback desired + Media3 not actually playing for the configured liveness interval triggers exactly one fresh-stream recovery attempt;
- playback becoming active before the liveness interval expires prevents that recovery attempt;
- pause/stop cancels the liveness job and all delayed retries;
- a fresh connection arms liveness coverage even if `isPlaying` was already false before `prepare()` (do not rely only on a `true -> false` callback transition);
- metadata-only updates preserve the current playback source/local configuration and do not change channel/quality/stream URI;
- stale retry/liveness callbacks from an older connection cannot replace a newer selected channel's stream.

If the current `RadioPlaybackReconnectPolicyTest` remains the right home, rewrite it around the simpler lifecycle. If old helpers become obsolete, delete their tests instead of preserving implementation-shaped tests for removed machinery.

### 2. Collapse active playback to one MediaItem, not a one-item "playlist policy"

The player still has a Media3 playlist API, but the radio application should not model active radio playback as a multi-item playlist when it only ever installs one selected channel.

Refactor `RadioPlaybackService` so the canonical fresh-stream opener:

- normalizes/resolves the selected channel using existing normalization rules;
- resolves quality with the existing persisted/pending-quality semantics;
- creates one `MediaItem` for the selected channel + resolved quality;
- installs that one item as the active player content in a way that guarantees a fresh HTTP request when restart is requested;
- calls `prepare()`;
- requests playback;
- updates service state to connecting/reconnecting as appropriate;
- resets/cancels stale jobs from the superseded connection.

Do not retain `lastPlaylistChannels`, `startIndex`, target-index seeking, or playlist-transition restoration solely to support the previous single-item playlist abstraction.

`MediaLibrarySession.Callback.onSetMediaItems(...)` still has to satisfy Media3's session API contract. It may return a one-element list because the API requires a list, but that should not force the service's internal streaming lifecycle back into a playlist/index state machine.

Review `RadioPlaybackActivePlaylistPolicy.kt` and its tests. If, after the refactor, it has no real responsibility beyond returning `listOf(selectedChannel)` and index `0`, delete it and its test file. Do not keep dead abstraction for compatibility.

### 3. Treat normal `STATE_ENDED` as protocol-level continuation

Change the player listener behavior so:

- if `playbackDesired == true` and Media3 reports `Player.STATE_ENDED`, immediately request a fresh selected stream;
- this path does **not** increment an error retry attempt;
- this path does **not** wait for `reconnectDelaysMs`;
- this path does **not** first ask a generic `isPlaybackHealthyForReconnect()` predicate whether EOF should be ignored;
- if playback is no longer desired, EOF does nothing.

A tiny scheduling/yield boundary is acceptable only if required to avoid re-entrant Media3 callback mutation. It must not become an error-style backoff and must not add perceptible song-boundary delay.

Expected behavior: finishing the server's finite current-track response naturally opens a new request at the station's then-current track/position, matching the website's behavior.

### 4. Keep one bounded retry path for actual failures

Actual `PlaybackException` / failed stream acquisition remains exceptional and should retain bounded retry rather than hot-looping.

Simplify the current reconnect implementation to the minimum needed:

- one delayed error-retry job;
- one attempt counter/backoff schedule (the current `50, 100, 200, 400, 800, 1500 ms` schedule may remain unless evidence during implementation shows it is harmful);
- one connection generation/token or equivalent cancellation mechanism if needed to prevent a stale retry from overwriting a newer channel/quality connection;
- reset the error attempt counter when playback actually becomes active again;
- cancel retry on pause/stop/new explicit connection.

Do not use a broad "healthy playback" early-return as a substitute for distinguishing EOF, error, and liveness cases. The code should know *why* it is restarting.

If `RadioPlaybackReconnectPolicy.kt` becomes mostly obsolete, shrink or delete it. The end state should have fewer recovery concepts, not renamed copies of all current concepts.

### 5. Add one narrow liveness guard for the reported silent/stale-playing failure

EOF/error handling alone is insufficient for the reported symptom because Media3 may stop actually playing without delivering the callback that currently drives reconnect.

Add one delayed liveness job with these semantics:

- it only exists while `playbackDesired == true`;
- arm it whenever a fresh stream is opened, because initial player state is not-playing before Media3 becomes ready;
- cancel it as soon as `player.isPlaying == true`;
- if Media3 later changes to `isPlaying == false` while playback is still desired, arm it again;
- when the delay expires, re-check that playback is still desired, the callback belongs to the current connection generation, and `player.isPlaying == false`;
- only then open one fresh stream;
- opening that fresh stream replaces/resets the liveness job, preventing a recovery storm;
- pause/stop must cancel it immediately and must never be undone by the watchdog.

Do not trigger an immediate reconnect for every transient `isPlaying == false`; buffering during connection is normal. The timeout exists to distinguish a transient from a stuck stream.

Do not invent a magic timeout silently. At implementation time:

1. inspect/reproduce the failure on a device if one is available and record what Media3 reports;
2. choose one fixed, conservative timeout that is long enough for ordinary connection buffering but short enough that a silent radio self-recovers promptly;
3. name the constant for its purpose;
4. encode the chosen value in tests;
5. add a short code comment only if the rationale is not obvious from the constant/test name.

Do not add continuous polling, multiple watchdogs, audio-sink-specific retry callbacks, or broad platform fallbacks unless reproduction proves this narrow `isPlaying` guard cannot observe the failure. If that happens, update this task with the evidence before expanding the design.

### 6. Decouple metadata/session presentation updates from the stream source

`/current` and art polling should continue to update UI, notification, lock-screen, and Android Auto/session metadata independently of the HTTP audio response.

However, a metadata refresh must not reconstruct the playback source.

Refactor `syncCurrentMediaItemMetadata()` so a metadata-only change:

- starts from the existing/current `MediaItem` when one exists;
- changes only `MediaMetadata` / presentation fields needed by the session;
- preserves the current media ID and playback/local configuration/URI exactly;
- does not call the same full media-item builder used to open a fresh stream if that builder can recompute the stream URL;
- does not call `prepare()`, seek, or reconnect;
- does not reset liveness/error state.

Media3 supports replacing the current item without interrupting playback when playback-relevant properties are unchanged. Make that invariant explicit in the implementation/tests: **metadata replacement is presentation-only; fresh-stream opening is the only operation that intentionally replaces playback content.**

If an even cleaner MediaSession metadata publication mechanism already exists in the repo/API and avoids replacing the current `MediaItem` entirely, it may be used, but do not broaden scope into a session architecture rewrite.

### 7. Remove obsolete transition/reconnect code after the new path works

After tests pass for the simplified lifecycle, search the playback package for machinery that only existed to support the old one-item playlist/reconnect flow.

Candidates include, but are not limited to:

- active playlist policy + `startIndex` plumbing;
- `lastPlaylistChannels` / target index selection used only for stream reconnect;
- seek-to-reconnect-target policy used only because the same one-item playlist was being reused;
- media-item transition recovery that exists only to restore the selected item after playlist transitions that can no longer occur;
- health predicates whose only role was suppressing the generic reconnect path;
- tests that assert those removed implementation details.

Delete code/tests that are genuinely obsolete. Preserve independent Android Auto browse/catalog behavior, adjacent-channel selection behavior, pause/stop behavior, and session command behavior.

Before deleting a helper, search the whole repository and confirm it has no independent caller/responsibility.

A successful implementation should make `RadioPlaybackService.kt` easier to explain:

> "The service opens one finite stream. EOF opens the next finite stream immediately. Errors retry with backoff. If desired playback stays non-playing, one watchdog reopens it. Metadata is separate."

If the resulting implementation still requires explaining multiple playlist policies, reconnect-health policies, reconnect-target seek policies, and transition-restoration policies, the simplification objective has not been met.

## Explicit non-goals

Do **not** expand this task into any of the following unless new reproduction evidence proves it is necessary:

- changing `Cookie-Club-Bots` / `Webserver-RS` to create one never-ending station stream;
- changing `website-blog` radio playback;
- changing the radio API contract;
- changing metadata/art polling frequency;
- changing the channel list or channel navigation UX;
- redesigning the Now Playing UI;
- redesigning Android Auto browse/catalog behavior;
- changing Media3 versions or adding playback dependencies;
- adding a general-purpose network connectivity framework;
- adding fallback URLs/providers;
- adding broad retry hooks to every Media3/audio callback;
- preserving obsolete helpers for backward compatibility "just in case".

If implementation discovers a separate server bug, Media3 regression, or audio-route problem, record it as a separate task with evidence rather than growing this task indefinitely.

## Required automated verification

At minimum, run and record exact results for:

```bash
./gradlew test
./gradlew :app:assembleDebug
```

Prefer the PixelArch container per `AGENTS.md` when Docker is available. If the local environment lacks Docker/SDK, state the blocker accurately; do not claim commands passed if they were not run.

In addition to the full suite, run the focused playback tests during implementation. The final test set should demonstrate the lifecycle behaviors listed above, especially:

- EOF restart is immediate and separate from error retry;
- liveness timeout recovers desired-but-not-playing state;
- recovery is cancelled by actual playback or user pause/stop;
- stale delayed work cannot override a newer connection;
- metadata-only replacement preserves playback configuration;
- existing pause/stop and channel-selection/session behavior is not regressed.

Do not keep `RadioPlaybackReconnectScheduleTest` in its current source-regex form merely to preserve a test if the reconnect schedule moves out of `RadioPlaybackService.kt`. Prefer testing an actual small policy/function/constant rather than scraping source text.

## Required device/runtime verification

This bug is runtime-specific; automated unit/build success is necessary but not sufficient evidence that silent playback is fixed.

If an Android device + adb are available, install the debug APK and record the device model + Android version, then verify all of the following:

1. **Basic playback:** play, pause, resume, and stop behave normally.
2. **Natural track boundaries:** allow playback to cross at least three real song boundaries. Audio must resume/continue after each finite response ends; title and art must continue matching the station.
3. **Channel switch:** switch channels while playing, including several rapid adjacent changes. The final selected channel must be the one playing; stale delayed recovery must not jump back to an older channel.
4. **Quality change:** exercise the existing quality-change behavior and confirm the new stream uses the chosen quality without leaving playback silent.
5. **Network interruption:** interrupt connectivity long enough to force a real player failure/stall, restore it, and confirm audio self-recovers without a manual pause/play cycle.
6. **Silent/non-playing recovery evidence:** if the original failure can be reproduced, capture the relevant `playbackState`, `playWhenReady`, and `isPlaying` observations before recovery and verify the liveness guard opens a fresh stream.
7. **Pause longer than watchdog:** pause for longer than the liveness timeout and confirm the watchdog does not restart audio.
8. **Stop longer than watchdog:** stop for longer than the timeout and confirm playback stays stopped.
9. **Background/session metadata:** while audio continues, background/lock the app long enough to observe at least one metadata change. Session/notification metadata may update without disrupting audio.
10. **No reconnect storm:** observe logs/network behavior around EOF and recovery. One EOF should produce one fresh stream connection, not repeated overlapping connects.

Use `.agents/tasks/wip/dcaae75d-verify-phone-playback.md` as the existing device-verification record. If you have a suitable device, update that task's blocked results with the new real pass/fail evidence rather than creating a second contradictory phone-verification record.

If no suitable Android device/adb exists in the execution environment:

- complete the implementation + automated verification that can be performed;
- record runtime verification as **BLOCKED — no device/adb** in this task;
- do not state that the user-reported runtime bug is fully verified fixed;
- leave the existing phone verification task blocked for a device-capable agent.

## Acceptance criteria

- [ ] Current behavior/protocol was re-verified from the Android app, website client, and radio server before coding; any material drift from this task was recorded in the task file.
- [ ] Android active playback uses exactly one selected-channel `MediaItem`; no internal multi-item/one-item playlist policy remains solely for stream recovery.
- [ ] There is one canonical function/path that opens a fresh selected stream and is reused by initial play, channel/quality reconnect, EOF continuation, error retry, and liveness recovery.
- [ ] Normal `Player.STATE_ENDED` while playback is desired immediately opens a fresh stream and does not consume/error-backoff a reconnect attempt.
- [ ] Player errors use one bounded delayed retry path and stale delayed retries cannot override a newer explicit connection.
- [ ] Fresh connections and later `isPlaying == false` transitions are covered by one delayed liveness guard.
- [ ] The liveness guard cancels on actual playback and on pause/stop, and cannot resurrect user-stopped playback.
- [ ] The chosen liveness timeout is named, unit-tested, and justified by reproduction/connection behavior rather than silently introduced.
- [ ] Public/service `Playing` state is only re-established from actual Media3 playback (`isPlaying == true`); the service does not knowingly leave stale "Playing" state as its sole health signal.
- [ ] Metadata/art/session updates do not rebuild or modify playback-relevant `MediaItem` configuration/URI and do not trigger reconnects.
- [ ] Obsolete playlist/reconnect/transition helpers and implementation-shaped tests are removed after repository-wide caller checks.
- [ ] Existing pause/stop, channel selection, quality selection, MediaSession, browse/catalog, and Android Auto-facing behavior remains intact.
- [ ] Focused unit tests cover EOF, error retry, stale job cancellation, liveness recovery/cancellation, pause/stop safety, and metadata-only source preservation.
- [ ] `./gradlew test` passes and the exact command/result is recorded.
- [ ] `./gradlew :app:assembleDebug` passes and the exact command/result is recorded.
- [ ] Physical-device checks above are completed and recorded when a device/adb is available; otherwise runtime verification is explicitly recorded as blocked and no false runtime-success claim is made.

## Suggested implementation sequence

Use small, reviewable commits. A sensible sequence is:

### Commit 1 — capture the lifecycle in tests

- Add/adjust focused playback policy tests for EOF vs error vs liveness.
- Add metadata-only `MediaItem` preservation coverage.
- Do not make production tests green by encoding the current generic reconnect behavior; tests should describe the target lifecycle.

Suggested commit shape:

```text
[TEST] Capture finite radio stream recovery behavior
```

### Commit 2 — simplify the stream opener and EOF/error paths

- Collapse active stream setup to one `MediaItem`.
- Route normal EOF directly to fresh stream opening.
- Retain only the bounded error retry machinery that is still required.
- Keep stale-work cancellation/generation protection.

Suggested commit shape:

```text
[FIX] Simplify finite radio stream recovery
```

### Commit 3 — add narrow liveness recovery and decouple metadata

- Arm/cancel the one liveness job from actual Media3 playback state.
- Ensure metadata replacement preserves playback configuration.
- Remove old helpers/tests made obsolete by the simpler path.

Suggested commit shape:

```text
[FIX] Recover stalled radio playback without rebuilding metadata source
```

### Commit 4 — verification/task updates only if needed

- Record exact Gradle results.
- If a physical device is available, record runtime results here and in the existing phone verification task.
- Do not mix unrelated cleanup into this commit.

## Completion note format

Before moving/archiving this task, append a short completion section containing:

```markdown
## Completion

- Implementation summary: <what was simplified/removed>
- Liveness timeout: <value and evidence/rationale>
- Obsolete files/helpers removed: <list>
- `./gradlew test`: PASS/FAIL/BLOCKED — <details>
- `./gradlew :app:assembleDebug`: PASS/FAIL/BLOCKED — <details>
- Device: <model / Android version, or BLOCKED — no device/adb>
- Natural track boundaries: PASS/FAIL/BLOCKED
- Network interruption recovery: PASS/FAIL/BLOCKED
- Pause/stop watchdog safety: PASS/FAIL/BLOCKED
- Remaining known issue(s): <none, or task references>
```

Do not mark the runtime bug "verified fixed" when physical-device validation was blocked.