# [TASK] Verify foreground audio recovery on an Android device

- Status: ready
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder (device required)
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`
- Depends on: `e5bc5a42-centralize-foreground-stream-opening.md`, `72398e06-separate-eof-and-error-recovery.md`, `dd9c189c-add-evidence-based-stall-recovery.md`, `a0e00f8c-keep-metadata-refresh-source-neutral.md`
- Related: `.agents/tasks/wip/dcaae75d-verify-phone-playback.md`

## Goal

Verify the implemented foreground recovery on a physical Android device; automated tests/builds alone do not establish that audio resumes correctly at runtime.

## Scope

Using a debug APK containing the completed recovery changes, record device model, Android version, APK revision, and results for:

- Play, pause, resume, and stop.
- Natural stream completion/boundary behavior as established by `2641ed31-verify-stream-and-stall-recovery-evidence.md`.
- Rapid channel changes and a quality change near a recovery attempt; confirm the final selected channel and quality remain active.
- Network interruption/error recovery and, if reproducible, the desired-but-not-playing stall recovery.
- Pause and stop for longer than the evidence-backed watchdog interval; neither may restart playback.
- A metadata/art update during playback; confirm audio continues without a new source open.
- Stream/open counts around recovery; confirm one recovery cause does not create overlapping or repeated opens.

Keep the results for this fix here. Coordinate device availability with the related phone-verification task without replacing its historical results. If no suitable device/adb is available, record `BLOCKED — no device/adb` and do not claim runtime verification passed.

## Acceptance criteria

- [ ] Each applicable check has a pass/fail result and device/OS details; unsupported checks are explicitly blocked with evidence.
- [ ] No stale channel/quality recovery, duplicate open, pause/stop resurrection, or metadata-induced source replacement is observed.
- [ ] Runtime verification is not marked passed when device testing was blocked.
