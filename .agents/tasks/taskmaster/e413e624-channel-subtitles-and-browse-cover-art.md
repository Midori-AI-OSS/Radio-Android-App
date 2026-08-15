# [T10] Remove Midori AI prefix from channel subtitles and expose cover art on every Android Auto browse channel

- Status: done
- Source: user-approved change (channel subtitle branding + browse cover art)
- Owner: coder
- Depends on: none (user-approved independent change)

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
  - `fetchArtForChannel` now returns `Boolean` (effective artwork changed and channel still in the catalog). Notifications moved to the callers: `refreshSelectedAndAdjacentArt` notifies per changed artwork (selected + each adjacent), `refreshBrowseArtCoverage` aggregates and emits at most one `notifyBrowseRootChildrenChanged()` per coverage pass. Blank→blank does not notify; no infinite loop (notify → re-query → cooldown skips).
   - `buildLibraryChannelItem` documents that artwork is absent until its background fetch completes or when the API reports no art.
- Root/album branding untouched (`app_name` root title, `albumTitle`, non-selected playlist artist, "Browse channels" root artist).
- Tests:
  - `RadioPresentationResolverTest.kt`: subtitle assertions updated to `"All"` / `"Chill"`; added `toChannelSubtitle` prefix-omission tests and `hasArtworkChanged` unit tests (arrival, unchanged, new URL, loss, blank→blank).
  - `RadioPlaybackBrowseArtCoverageTest.kt` (new, mirrors `RadioPlaybackPauseStopTest` source-scanning style): asserts the browse root triggers coverage, coverage reuses the prefetch path off the browse thread, `fetchArtForChannel` reports effective artwork changes to callers without notifying itself, coverage emits at most one browse notification per pass (guarded by an `artworkChanged` flag), `refreshSelectedAndAdjacentArt` preserves per-change notifications, and failed fetches record their attempt in the per-channel cooldown map.

## Audit follow-up (fix commit: this commit)

Findings from the T10 audit of the cover-art implementation:

1. **Notification amplification**: the all-channel coverage pass called `notifyBrowseRootChildrenChanged()` once per changed channel, so a large catalog could emit many browse-root notifications per browse query.
   - Fix: `fetchArtForChannel` no longer notifies; `refreshBrowseArtCoverage` aggregates changes and emits **at most one** `notifyBrowseRootChildrenChanged()` per pass, only when at least one channel's artwork actually changed.
2. **Failed fetches bypassed the cooldown**: `artFetchAtMsByChannel` was only written on success, so the prefetch cooldown (`cached != null` gate) never applied to channels whose fetch failed — they were re-requested on every browse query.
   - Fix: the `RadioApiResult.Failure` branch and the generic exception catch now record `artFetchAtMsByChannel[normalizedChannel] = System.currentTimeMillis()`; the prefetch gate now also skips when `lastFetchAt > 0L`, so failed channels are retried at most once per `adjacentArtPrefetchCooldownMs` (15s) instead of per query.

Behavior preserved: `refreshSelectedAndAdjacentArt` still notifies the browse tree per changed artwork (selected + each adjacent), and forced selected refreshes (`isPrefetch = false`) are unaffected by the cooldown.

## Acceptance criteria

- [x] No channel subtitle (browse, playlist, in-app Now Playing) contains the "Midori AI Radio: " prefix; subtitles show only the channel name
- [x] Root title/subtitle and channel `albumTitle` branding (`app_name`, "Browse channels") are unchanged
- [x] `./gradlew test` passes, including updated `RadioPresentationResolverTest` subtitle assertions (auditor-verified 2026-08-15: BUILD SUCCESSFUL, JDK 17 + Android SDK platform 34)
- [x] `./gradlew :app:assembleDebug` succeeds (auditor-verified 2026-08-15: BUILD SUCCESSFUL, app-debug.apk produced)
- [x] After background coverage completes, every channel with API-reported art has a non-blank `artworkUri`; initial fetches and API-reported blank art intentionally carry none
- [x] Artwork updates propagate to the browse tree (browse children refresh when art arrives)

## Verification

- Implemented against commit `3a59240` on branch `midoriaiagents/06caea82e6`; code+tests commit: `22a4b17`; task closeout commit: that commit; audit follow-up commit: this commit.
- Local verification was not possible: this host has no JDK (no `java` on PATH, no JVM under `/usr/lib/jvm`, `/opt`, or SDKMAN dirs, `JAVA_HOME` empty) and no Docker (`docker info` fails), so `./gradlew test` / `./gradlew :app:assembleDebug` could not be executed.
- To verify (PixelArch container or CI with Docker), run:
  - `./gradlew test`
  - `./gradlew :app:assembleDebug`
- Source-scanning test regexes re-verified against the post-fix `RadioPlaybackService.kt` (each extraction target matches exactly once and terminates at the correct closing brace; 17 assertions checked locally via a Python mirror of the exact regexes — Kotlin execution still deferred).

## Notes

- Only the channel-subtitle prefix is removed; app/album/root branding tokens stay untouched.
- Reuse the existing `artByChannel` cache and fetch path; avoid new background-fetch machinery beyond what is needed to cover all channels and refresh the browse tree on art arrival.
- This user-approved change is independent of the issue #3 task queue.
