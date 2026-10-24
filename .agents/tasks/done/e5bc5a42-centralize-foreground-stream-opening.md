# [TASK] Centralize foreground stream opening and selection

- Status: ready
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`
- Depends on: `2641ed31-verify-stream-and-stall-recovery-evidence.md`

## Goal

Make one service path responsible for admitting each foreground stream open/reopen. It must open the currently selected channel at the effective quality, and stale or overlapping work must not replace a newer source.

## Scope

- Route initial play, explicit channel changes, and existing recovery/open entry points through one fresh-stream opener. A quality change uses this opener when existing active/pending-quality semantics require a reconnect; do not make a playing quality change reconnect immediately solely as part of this task.
- Resolve the selected channel and effective active/pending quality when an open is admitted, rather than capturing them when delayed work is scheduled.
- Invalidate or cancel stale delayed work using a generation/token or equivalent current-source check. Since player callbacks do not identify their originating source, callback/retry work must be checked against playback desire, the current connection, and the current selection before it can reopen.
- Make source-replacement/prepare admission single-flight (not serialization behind the lifetime of the active stream). A newer explicit selection may supersede pending work, but older work must not open afterward. Coordinate admission with the 220 ms channel-switch fade so a retry cannot overlap the switch, undo its volume handling, or restore an older channel/quality.
- Keep MediaSession/Android Auto-initiated foreground playback on the same opener path while preserving the MediaSession callback's API-required return shape and browse behavior. Recovery trigger/timing policy remains in the linked recovery tasks; this task centralizes and guards the open operation.
- Simplify one-item playlist/index/transition recovery machinery only where it exists to support the old stream-opening flow. Preserve MediaSession API requirements, Android Auto browse behavior, and adjacent-channel selection.

## Acceptance criteria

- [ ] All foreground stream source replacements, including session-originated playback and recovery opens, converge on one canonical service-side opener.
- [ ] A stale callback/retry, or rapid channel/quality changes, cannot open an older selection over the current one; selection and effective quality are resolved at open admission using existing active/pending-quality semantics.
- [ ] At most one source-replacement/prepare transaction is admitted at a time. A newer explicit selection supersedes older pending work, and opening/retry work cannot race the channel fade into duplicate connections, incorrect selection, or incorrect volume restoration.
- [ ] Behavior-focused tests demonstrate single-flight admission, latest effective channel/quality winning after rapid changes, stale callback/retry rejection, retry/fade overlap, and pause/stop invalidation of delayed opening work.
- [ ] `./gradlew test` and `./gradlew :app:assembleDebug` pass.
