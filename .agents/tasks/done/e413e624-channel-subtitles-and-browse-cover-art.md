# [T10] Remove Midori AI prefix from channel subtitles and expose cover art on every Android Auto browse channel

- Status: wip
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

## Acceptance criteria

- [ ] No channel subtitle (browse, playlist, in-app Now Playing) contains the "Midori AI Radio: " prefix; subtitles show only the channel name
- [ ] Root title/subtitle and channel `albumTitle` branding (`app_name`, "Browse channels") are unchanged
- [ ] `./gradlew test` passes, including updated `RadioPresentationResolverTest` subtitle assertions
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] Every channel item in `onGetChildren(RADIO_BROWSE_ROOT_ID)` has a non-blank `artworkUri` (each documented exception justified)
- [ ] Artwork updates propagate to the browse tree (browse children refresh when art arrives)

## Notes

- Only the channel-subtitle prefix is removed; app/album/root branding tokens stay untouched.
- Reuse the existing `artByChannel` cache and fetch path; avoid new background-fetch machinery beyond what is needed to cover all channels and refresh the browse tree on art arrival.
- Positioned after the issue #3 queue because it edits files the queue also touches (`RadioPlaybackService.kt`, `RadioPresentationResolver.kt`, `RadioPresentationResolverTest.kt`); implement against the modernized stack.
