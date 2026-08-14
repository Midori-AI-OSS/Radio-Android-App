# [T10] Remove Midori AI prefix from channel subtitles and expose cover art on every Android Auto browse channel

- Status: done
- Source: user-approved change (channel subtitle branding + browse cover art)
- Owner: coder
- Depends on: 036b4428 (issue #3 closeout)

## Goal

Remove the "Midori AI Radio: " prefix from channel subtitles so channel metadata reads as just the channel name (app/album/root branding stays), and make every Android Auto browse channel expose its current cover art.

## Scope

- Remove the "Midori AI Radio: " prefix from channel subtitles:
  - `RadioPresentationResolver.kt` `toChannelSubtitle(...)` (currently returns `"Midori AI Radio: ${toChannelDisplayName(channel)}"`) — feeds the browse channel subtitle (`RadioPlaybackService.kt` `buildLibraryChannelItem` `.setArtist(...)`) and the playlist item artist
  - The parallel `toChannelSubtitle(...)` in `NowPlayingScreen.kt` (in-app Now Playing subtitle) so the string stays consistent
- Keep app/album/root branding intact: `app_name` stays as the browse root title, the channel `albumTitle`, and the non-selected playlist artist; `auto_browse_root_subtitle` ("Browse channels") stays as the root artist/subtitle
- Update subtitle assertions in `app/src/test/java/xyz/midoriai/radio/playback/RadioPresentationResolverTest.kt` to the unprefixed values
- Make every Android Auto browse channel expose its current cover art:
  - `buildLibraryChannelItem` currently sets `artworkUri` only when the `artByChannel` cache already holds an art payload, and the cache is populated only for the selected channel plus its adjacent neighbors (`refreshSelectedAndAdjacentArt`); channels outside that window get no art
  - Ensure every channel returned from `onGetChildren(RADIO_BROWSE_ROOT_ID)` carries a non-blank `artworkUri` (or a documented exception when the API reports no art for that channel)
  - Ensure the browse tree refreshes when art arrives for a channel (currently `notifyBrowseRootChildrenChanged` fires only on channel-list changes), so newly available art surfaces in Android Auto

## Results

- `RadioPresentationResolver.kt`: `toChannelSubtitle` now returns `toChannelDisplayName(channel)`; added pure helper `hasArtworkChanged(previous, updated)` comparing effective artwork URIs (blank treated as "no art").
- `NowPlayingScreen.kt`: in-app `toChannelSubtitle` returns `displayChannel` (no prefix).
- `RadioPlaybackService.kt`:
  - `onGetChildren(RADIO_BROWSE_ROOT_ID)` schedules `refreshBrowseArtCoverage(channels)` before building the channel items, so every browse channel's art is fetched in the background.
  - `refreshBrowseArtCoverage` normalizes+dedupes the channel list and launches a `serviceScope` coroutine calling the existing `fetchArtForChannel(..., forceRefresh = false, isPrefetch = true)` per channel; bounded by the existing in-flight dedupe and the 15s prefetch cooldown (stale cached channels get refreshed, hence "coverage" also refreshes).
  - `fetchArtForChannel` Success branch now snapshots the previous cached payload and calls `notifyBrowseRootChildrenChanged()` when `hasArtworkChanged(...)` and the channel is still in the catalog, so art arrival/loss/change surfaces in the browse tree. Blank→blank does not notify; no infinite loop (notify → re-query → cooldown skips).
  - `buildLibraryChannelItem` documents the no-art exception inline: channels the API reports without art (blank artUrl) intentionally carry no `artworkUri`.
- Root/album branding untouched (`app_name` root title, `albumTitle`, non-selected playlist artist, "Browse channels" root artist).
- Tests:
  - `RadioPresentationResolverTest.kt`: subtitle assertions updated to `"All"` / `"Chill"`; added `toChannelSubtitle` prefix-omission tests and `hasArtworkChanged` unit tests (arrival, unchanged, new URL, loss, blank→blank).
  - `RadioPlaybackBrowseArtCoverageTest.kt` (new, mirrors `RadioPlaybackPauseStopTest` source-scanning style): asserts the browse root triggers coverage, coverage reuses the prefetch path off the browse thread, and art arrival notifies the browse tree.

## Acceptance criteria

- [x] No channel subtitle (browse, playlist, in-app Now Playing) contains the "Midori AI Radio: " prefix; subtitles show only the channel name
- [x] Root title/subtitle and channel `albumTitle` branding (`app_name`, "Browse channels") are unchanged
- [ ] `./gradlew test` passes, including updated `RadioPresentationResolverTest` subtitle assertions (deferred: no JDK/Docker on this host — run in PixelArch/CI)
- [ ] `./gradlew :app:assembleDebug` succeeds (deferred: no JDK/Docker on this host — run in PixelArch/CI)
- [x] Every channel item in `onGetChildren(RADIO_BROWSE_ROOT_ID)` has a non-blank `artworkUri` (each documented exception justified — API-reported blank art intentionally carries none, documented in `buildLibraryChannelItem`)
- [x] Artwork updates propagate to the browse tree (browse children refresh when art arrives)

## Verification

- Implemented against commit `3a59240` on branch `midoriaiagents/06caea82e6`; code+tests commit: `22a4b17`; task closeout commit: this commit.
- Local verification was not possible: this host has no JDK (no `java` on PATH, no JVM under `/usr/lib/jvm`, `/opt`, or SDKMAN dirs, `JAVA_HOME` empty) and no Docker (`docker info` fails), so `./gradlew test` / `./gradlew :app:assembleDebug` could not be executed.
- To verify (PixelArch container or CI with Docker), run:
  - `./gradlew test`
  - `./gradlew :app:assembleDebug`
- Source-scanning test regexes verified against `RadioPlaybackService.kt` (each extraction target matches exactly once and terminates at the correct closing brace).

## Notes

- Only the channel-subtitle prefix is removed; app/album/root branding tokens stay untouched.
- Reuse the existing `artByChannel` cache and fetch path; avoid new background-fetch machinery beyond what is needed to cover all channels and refresh the browse tree on art arrival.
- Positioned after the issue #3 queue because it edits files the queue also touches (`RadioPlaybackService.kt`, `RadioPresentationResolver.kt`, `RadioPresentationResolverTest.kt`); implement against the modernized stack.
