# [TASK] Keep metadata refreshes separate from the playback source

- Status: ready
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`

## Goal

Allow current-track and artwork updates to refresh session presentation without rebuilding or interrupting the active audio source.

## Scope

- Update presentation metadata from the current `MediaItem` where available, preserving its media ID, stream URI, quality/local playback configuration, and other playback-relevant fields.
- Do not prepare, seek, reopen the stream, or reset retry/watchdog state for a metadata-only change.
- Add a behavior-focused test that compares playback-relevant source properties before and after a metadata refresh.

## Acceptance criteria

- [ ] Metadata refresh changes only the required presentation fields and preserves the active playback source/configuration.
- [ ] A metadata refresh does not cause a new stream request or alter playback/recovery state.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass.
