# [TASK] Keep metadata refreshes separate from the playback source

- Status: in-progress (implementation complete; verification blocked-by-environment)
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`

## Goal

Allow current-track and artwork updates to refresh session presentation without rebuilding or interrupting the active audio source.

## Scope

- Use the active current `MediaItem` as the base for a metadata refresh. Update only the presentation fields driven by current-track/art polling (`title`, `artist`, `displayTitle`, and `artworkUri`); preserve other `MediaMetadata` fields.
- Preserve every non-metadata `MediaItem` property, including media ID, URI, quality/local playback configuration, and any other playback-relevant configuration.
- A metadata-only refresh must not prepare or seek the player, open/reopen the stream, or change retry/watchdog state.
- Add a behavior-focused test that verifies refreshed presentation fields, unchanged source/configuration properties, and no stream-opening or playback/recovery lifecycle action.

## Acceptance criteria

- [x] Metadata refresh updates `title`, `artist`, `displayTitle`, and `artworkUri` as appropriate for the refreshed track/art, while preserving other `MediaMetadata` fields and every non-metadata `MediaItem` property.
- [x] A focused behavior test verifies the active item's media ID, URI, quality/local configuration, and other playback-relevant properties are unchanged; it also verifies no stream-opening, prepare, or seek action occurs. Recovery state is not an input to this presentation-only path.
- [x] A metadata refresh causes no new stream request and does not alter playback/recovery state.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass. The debug build passed; the full test run is blocked before execution by dependency downloads (details below).

## Implementation update — 2026-11-21

- `RadioSessionPlayer.publishPresentationMetadata()` now uses the active source metadata as its base and replaces only the four track/art presentation fields. Other metadata and the source item's local configuration remain intact.
- `RadioSessionPlayerTest.sessionMetadataPublishesWithoutMutatingOrPreparingTheAudioSource` covers album, genre, playable state, extras, media ID, URI/local configuration, stream-open callback count, `setMediaItem`, prepare, seek, and duplicate metadata notification behavior. `:app:compileDebugUnitTestKotlin` passes.
- `:app:assembleDebug --rerun-tasks` passes (36 actionable tasks). `test --rerun-tasks` cannot resolve Robolectric's `nativeruntime-dist-compat:1.0.18` JAR: Maven Central redirects it to `gcp.mirrors.maven.org`, where the environment proxy returns HTTP 403. Excluding that large artifact in a temporary init script reaches Robolectric's runtime fetch, but its `android-all-instrumented` download is also blocked by the proxy; no test execution is claimed.
- Host requests to `https://radio.midori-ai.xyz/health` and `/radio/v1/current?channel=all` returned HTTP 200; the stream endpoint returned HTTP 200 and 1.5 MB during a five-second sample. This confirms endpoint reachability, not a full track boundary.
- The API 36 x86_64 AVD has no `/dev/kvm`; TCG startup produced Android system ANRs and the host has no PulseAudio backend. The app was installed/launched, but emulator runtime playback is not reliable evidence. Physical-device verification remains tracked separately.
