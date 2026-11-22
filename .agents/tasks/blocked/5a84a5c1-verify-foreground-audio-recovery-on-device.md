# [TASK] Verify foreground audio recovery on an Android device

- Status: blocked-by-environment (physical device required)
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder (device required)
- Parent: `.agents/tasks/blocked/8a0774c5-simplify-radio-stream-recovery.md`
- Depends on: `e5bc5a42-centralize-foreground-stream-opening.md`, `72398e06-separate-eof-and-error-recovery.md`, `dd9c189c-add-evidence-based-stall-recovery.md`, `a0e00f8c-keep-metadata-refresh-source-neutral.md`
- Related: `.agents/tasks/blocked/dcaae75d-verify-phone-playback.md`

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

## Environment update — 2026-11-21

- Android SDK and adb are available at `/tmp/agents-artifacts/android-sdk`; an API 36 x86_64 AVD (`sdk_gphone64_x86_64`) booted and accepted the debug APK. No physical Android device is attached.
- `/dev/kvm` is unavailable, so the AVD runs in TCG software emulation. Android System UI and launcher repeatedly reported ANRs; the app remained at “Fetching current track…”. The emulator log also reports `Could not init 'pa' audio driver` (no PulseAudio output). It cannot supply the required audible-playback, stable state-observation, three-track-boundary, or attributable open-count evidence.
- Host requests to `/health` and `/radio/v1/current?channel=all` returned HTTP 200, and the stream returned HTTP 200 plus 1.5 MB during a five-second sample. The emulator had no direct DNS (`ping radio.midori-ai.xyz`: `unknown host`); proxy setup did not produce a reliable playback run.
- Result: physical-device acceptance remains BLOCKED. No phone playback, boundary, recovery, or audible success is claimed. Run this task on the physical Android 16 phone with the listed logcat/network evidence before marking it done.

## VM runtime attempt — 2026-11-21 (~16:50–17:00 America/Los_Angeles)

- Runtime: AVD `midori-api36`, device `sdk_gphone64_x86_64` / `emu64xa`, Android 16 / API 36, software TCG (no `/dev/kvm`). System UI and launcher ANRs recurred. The emulator had no audio backend and was started with `-no-audio`.
- Source revision: `a280bde7ba20952f63960b32103cffb5f0a785f9`. Tested package: `xyz.midoriai.radio`, version `0.1.0` (versionCode 1), APK SHA-256 `5d61179dbc79fecd6c2c5abc056dd8d69343008315d6f1647c9373ce9cfaf452`. This was a local test-only debug APK with a temporary proxy CA trust overlay in `app/src/debug`; those files have since been removed. It is not the ordinary debug/release trust configuration.
- Direct radio DNS did not work in the AVD. Playback was exercised through a temporary host CONNECT relay and the test-only CA overlay. The relay carried encrypted TLS payloads, so it did not expose request paths or allow stream-open counts to be attributed.
- Around 16:50 PST, dispatching Play produced MediaSession `BUFFERING(6)` and then `PLAYING(3)`. MediaSession metadata changed during the run (including “Midnight Telegraph,” “We Remain, We Transmit (Dream Pop),” and “Lunar Resolve (GBA)”). The channel UI showed `All`; quality was not captured. MediaSession reported position and buffered position `-1` and speed `0.0` while in `PLAYING`.
- Around 16:59 PST, dispatching Pause returned MediaSession to `NONE(0)`; after a 25-second hold it remained `NONE`. Dispatching Play returned it to `PLAYING(3)`. Dispatching Stop returned it to `NONE(0)`; after another 25-second hold it remained `NONE`. The hold exceeded the approximately 20-second guard threshold (5-second samples and three stagnant intervals).
- These results confirm only observable MediaSession transitions in this software AVD. No audio was audible. The app's internal `playbackState`, `playWhenReady`, and `isPlaying` were not captured. The metadata changes do not establish three real EOF boundaries or a metadata-only refresh. Rapid channel/quality changes, network interruption, attributable recovery/open counts, and Android Auto were not verified.
- Result: partial VM interaction evidence; acceptance remains BLOCKED. Do not mark the device task done or infer audible recovery from MediaSession state.

## VM runtime attempt — 2026-11-22 (~05:00 UTC)

- Runtime: AVD `midori-api36`, device `sdk_gphone64_x86_64` / `emu64xa`, Android 16 / API 36, x86_64 software TCG because `/dev/kvm` is unavailable. The AVD booted, but `system_server` was CPU-bound and later appeared in uninterruptible `D` state.
- APK: `xyz.midoriai.radio`, version `0.1.0` (versionCode 1), SHA-256 `92d6e5715b0f164290ff43a1485f26b6c01c6896c166098deeee1bfcec246f0d`. The app code matches source revision `9b9b3343373253d9bddb8a386b584a7d49721f69`; the current worktree changes at the time were test and task-document changes only. `adb install -r` returned `Success`.
- Launch attempts: `adb shell monkey -p xyz.midoriai.radio 1` and `adb shell am start -W -n xyz.midoriai.radio/.MainActivity`. `am start` reported `Failure calling service activity: Broken pipe (32)`; `dumpsys activity` still showed the Nexus launcher as top-resumed, `ps -A` showed no app process, and the 480×1067 screenshot was black. `dumpsys media_session` showed no app session.
- Evidence is saved at `/tmp/agents-artifacts/radio-avd-logcat-2026-11-22.txt`, `/tmp/agents-artifacts/radio-avd-activity-2026-11-22.txt`, and `/tmp/agents-artifacts/android-screen.png`. The AVD was started with `-no-audio`; audible output, playback state, stream requests/counts, and track boundaries were not testable in this run.
- Result: blocked by the unstable software-emulated Android runtime, in addition to the missing physical phone. No runtime recovery pass is claimed; all physical-device acceptance criteria remain open.
