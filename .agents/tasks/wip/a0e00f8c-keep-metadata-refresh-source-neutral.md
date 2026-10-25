# [TASK] Keep metadata refreshes separate from the playback source

- Status: ready
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

- [ ] Metadata refresh updates `title`, `artist`, `displayTitle`, and `artworkUri` as appropriate for the refreshed track/art, while preserving other `MediaMetadata` fields and every non-metadata `MediaItem` property.
- [ ] A focused behavior test verifies the active item's media ID, URI, quality/local configuration, and other playback-relevant properties are unchanged; it also verifies no stream-opening, prepare, seek, or retry/watchdog state change occurs.
- [ ] A metadata refresh causes no new stream request and does not alter playback/recovery state.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass.
