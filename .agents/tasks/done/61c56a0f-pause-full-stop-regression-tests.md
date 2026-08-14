# Regression tests for pause-as-stop behavior (issue #4)

Ref: https://github.com/Midori-AI-OSS/Radio-Android-App/issues/4

Depends on: `961b6fc0-pause-full-stop-teardown.md`

## Context (verified)

`pausePlayback()` is private inside `RadioPlaybackService` (an Android service; no Robolectric in the repo). The established pattern for guarding service internals is a source-level test: `RadioPlaybackReconnectScheduleTest` parses `RadioPlaybackService.kt` via regex and asserts the reconnect schedule.

`RadioSessionPlayerTest.liveStreamSessionPlayer_preservesTransportCallbacks` already asserts `stop()` maps to the `onPause` callback — that mapping is unchanged by this work and must stay green.

## Change

Add regression coverage for the issue's core invariant, following the `RadioPlaybackReconnectScheduleTest` source-parse style:

- Assert `pausePlayback()` performs a full stop of the underlying ExoPlayer (e.g. calls `player.stop()` and/or `player.clearMediaItems()`) and does not leave the player paused-but-prepared (no bare `player.pause()`-only path).
- Keep assertions narrow and one-invariant-per-test; no duplication of behavior already covered elsewhere.

## Acceptance criteria

- New test(s) would fail against the pre-fix `pausePlayback()` (which only sets `playWhenReady = false` and calls `pause()`).
- `./gradlew test` passes, including the new test(s) and the unchanged `RadioSessionPlayerTest` / `RadioPlaybackReconnectScheduleTest`.
