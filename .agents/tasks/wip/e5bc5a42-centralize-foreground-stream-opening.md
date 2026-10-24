# [TASK] Centralize foreground stream opening and selection

- Status: ready
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`
- Depends on: `2641ed31-verify-stream-and-stall-recovery-evidence.md`

## Goal

Make one service path responsible for opening/reopening the selected foreground stream, with no stale or overlapping open able to replace the current channel or quality.

## Scope

- Route initial play, explicit channel/quality changes, and recovery attempts through one fresh-stream opener.
- Resolve the effective channel and active/pending quality when an open is admitted, so a delayed recovery uses the latest selection rather than an older captured value.
- Add narrow generation/cancellation or equivalent current-source checks for delayed work. Player callbacks do not identify their originating source, so stale callback/retry work must be validated against the current connection and selected source before reopening.
- Make opening single-flight. Coordinate it with the 220 ms channel-switch fade so a retry cannot overlap the switch, undo its volume handling, or restore an older channel/quality.
- Simplify one-item playlist/index/transition recovery machinery only where it exists to support the old stream-opening flow. Preserve MediaSession API requirements, Android Auto browse behavior, and adjacent-channel selection.

## Acceptance criteria

- [ ] All foreground stream opens use one canonical path.
- [ ] A stale callback/retry and rapid channel or quality changes cannot open an older selection over the current one.
- [ ] At most one fresh-stream opening is in flight; opening and channel-fade work cannot race into duplicate or incorrectly selected connections.
- [ ] Behavior-focused tests cover single-flight, pause/stop invalidation, latest channel/quality selection, and retry/fade overlap.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass.
