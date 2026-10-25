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

Use a debug APK built from a revision containing all completed dependent recovery changes. This task is runtime verification only: do not change application code, and do not count automated tests/builds as device results. Record the device model, Android version/API level, source revision, APK version name/code, and APK SHA-256.

Run and record:

- Play, pause, resume, and stop.
- Natural stream completion/boundary behavior as established by `2641ed31-verify-stream-and-stall-recovery-evidence.md`; cross at least three real track boundaries.
- Rapid channel changes and a quality change near a recovery attempt; confirm the final selected channel and quality remain active.
- Network interruption/error recovery. Also test desired-but-not-playing stall recovery if its trigger is reproducible on the device; otherwise record `BLOCKED — stall not reproduced` with the attempted trigger and evidence.
- Pause and stop for longer than the watchdog interval selected by the evidence task and implemented in the APK; record that interval and confirm neither restarts playback.
- A metadata/art update during playback; confirm audio continues without a new source open.
- Stream/open counts around EOF, recovery, metadata refresh, and pause/stop. Attribute each observed open to its cause; confirm no duplicate/overlapping open is attributable to one event and no metadata refresh or paused/stopped watchdog opens a source. Further opens are acceptable only when attributable to a distinct documented retry.

For each check, record a timestamped trigger/procedure, pass/fail/blocked result, whether audio was audible, the selected and actually playing channel/quality, and the observed outcome. For recovery checks, include relevant `playbackState`, `playWhenReady`, and `isPlaying` observations. Support open-count claims with attributable logcat, request/network capture, or server-log evidence; include a short excerpt or artifact path. If a required state or request count cannot be observed, mark that check blocked rather than infer a pass.

Keep detailed results for this fix here. If the run also covers checks in the related phone-verification task, append a dated update there without replacing its historical results. If no suitable device/adb is available, record `BLOCKED — no device/adb` with the attempted device/adb check and its output; do not claim runtime verification passed.

## Acceptance criteria

- [ ] Each applicable check has a pass/fail result and device/OS details; unsupported checks are explicitly blocked with evidence.
- [ ] The tested APK is identified by source revision, version name/code, and SHA-256; runtime evidence is timestamped and attributable to the listed checks.
- [ ] At least three real track boundaries are observed. Stream-open claims are backed by request/log evidence; checks without observable required evidence are blocked, not inferred as passes.
- [ ] No stale channel/quality recovery, duplicate open, pause/stop resurrection, or metadata-induced source replacement is observed.
- [ ] Runtime verification is not marked passed when device testing was blocked.
