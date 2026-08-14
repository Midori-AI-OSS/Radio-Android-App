# Pause should fully stop the live stream (issue #4)

Ref: https://github.com/Midori-AI-OSS/Radio-Android-App/issues/4

## Context (verified)

`RadioPlaybackService.pausePlayback()` (`app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackService.kt`, lines 260-270) sets `playbackDesired = false`, cancels reconnect/channel-switch work, but only pauses the underlying ExoPlayer (`playWhenReady = false`, `pause()`). The player stays prepared on the stream (STATE_READY) with media items retained — no `player.stop()`/media-item reset exists anywhere in main source.

All pause sources (phone UI, system media controls, Android Auto) route through MediaSession -> `RadioSessionPlayer.pause()` / `setPlayWhenReady(false)` / `stop()` -> `pausePlayback()`.

`play()` (lines 243-258) already takes the fresh-connect path via `connectToSelectedStream(...)` when `playbackDesired` is false, so only the teardown side is missing.

## Change

Make `pausePlayback()` tear down the active stream connection so nothing prepared/buffered remains, per the issue's implementation notes (`player.stop()` plus any necessary state/media-item reset):

- Stop the underlying ExoPlayer from `pausePlayback()`; no prepared/resumable stream state may be retained.
- Preserve selected channel and quality settings; only active playback/connection state is discarded.
- Operate on the underlying ExoPlayer directly — do not route through `RadioSessionPlayer.stop()`, which maps back to `pausePlayback()` (recursion).
- Keep the existing reconnect/channel-switch cancellation; confirm no reconnect job can fire after stop (listener callbacks already early-return when `playbackDesired` is false — keep that behavior).
- Verify the next `play()` flows through the existing fresh-connect path (`connectToSelectedStream` -> `syncPlayerPlaylist`/`player.prepare()`) and does not resume a paused/buffered stream.

## Acceptance criteria

- Pressing Pause results in no active/prepared stream connection being retained (player no longer STATE_READY on the stream).
- Playback state reports `Stopped` (unchanged behavior).
- Pressing Play afterward establishes a fresh stream connection and prepares from scratch; selected channel and quality remain unchanged.
- Pending reconnect jobs do not fire after Pause.
- Channel switching and reconnect behavior while actively playing remains unchanged.
- `./gradlew test` and `./gradlew :app:assembleDebug` pass.
- If Docker + adb are available: `./buildapk.sh` and manually verify Pause from the app UI and system media controls fully stops the stream, and next Play reconnects fresh.
