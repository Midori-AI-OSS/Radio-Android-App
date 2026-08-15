# [T7] Verify phone playback behavior with the modernized APK

- Status: wip
- Auditor note (2026-08-15): returned to wip — acceptance criteria 1 and 3 unmet; none of the playback checks were executed. Fix: run all five checks on a real Android 16 device with adb, record pass/fail per check in this file, and log any regression as a new task file.
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder (device required)
- Depends on: e2752bd2 (clean build + CI verification)

## Goal

Confirm the dependency modernization did not regress any phone-side playback behavior.

## Scope

Using the debug APK from T6 installed on a test device (Android 16 preferred, matching the issue environment), verify:

- Normal phone playback (play/pause/stop)
- Channel switching
- Metadata and artwork updates
- Reconnect behavior (network interruption / service restart)
- Settings persistence across app restarts

Record each verification result in this task file. If any regression is found, log it as a defect with steps to reproduce rather than fixing it inline (unless the fix is trivially part of the upgrade).

## Acceptance criteria

- [ ] All phone playback checks above pass on the test device — not verifiable: no test device available (see Verification results below)
- [x] Results (pass/fail per check, device/OS version) recorded in this task file — recorded as BLOCKED per check with evidence
- [ ] Any regression is reported with repro steps and a new task file, not silently fixed — N/A: no runtime testing was possible, so no regression was observed or silently fixed

## Notes

- This task requires physical-device testing; if no device is available, state the blocker in the task file.
- App install source should be the GitHub APK path (sideload), matching the issue environment.

## Verification results (2026-08-15)

Result: BLOCKED — no Android test device is available in this environment, so none of the playback
checks could be executed. Per the Notes above, the blocker is stated here rather than marking any
check as passed.

### Blocker evidence

Issue #3's environment is a physical Android 16 Samsung device, installed from the GitHub APK
(sideload). This host cannot reach any such device:

- `adb` is not installed anywhere on the host (checked PATH, `~/Android/Sdk`, `~/android-sdk`,
  `/usr/lib/android-sdk`, and a host-wide `find` for the binary).
- No Android SDK / platform-tools, no emulator/AVD binaries, no `ANDROID_HOME`/`ANDROID_SDK_ROOT`,
  no `~/.android/avd`.
- No `~/.android/adbkey` — this host has never been paired with an Android device.
- USB bus (`/sys/bus/usb/devices`) exposes only storage bridges, a hub, and a LAN adapter
  (2109:0813, 174c:55aa, 0bda:8153); no phone is attached (`/dev/bus/usb` does not exist).
- Docker is unavailable (permission denied on `/var/run/docker.sock`), so the PixelArch container
  (which would carry adb) cannot run.

### Install source status

The GitHub sideload path exists and matches the issue environment: GitHub beta releases publish
`app-debug.apk` assets (latest: `beta-87-ac4b07ed`, released 2026-08-14, sha256
bd8f2d5714a1044154895bf763df41d4eb4e1b13f9bdec13abb98f74ec52bad5). The local T6 debug APK is also
present at `app/build/outputs/apk/debug/app-debug.apk` (default 1/0.1.0 stamp per the T6 audit).
Neither can be installed without a device/adb.

### Per-check results

| Check | Result | Device/OS |
| --- | --- | --- |
| Normal phone playback (play/pause/stop) | BLOCKED — no device attached, no adb | N/A |
| Channel switching | BLOCKED — no device attached, no adb | N/A |
| Metadata and artwork updates | BLOCKED — no device attached, no adb | N/A |
| Reconnect behavior (network interruption / service restart) | BLOCKED — no device attached, no adb | N/A |
| Settings persistence across app restarts | BLOCKED — no device attached, no adb | N/A |

No regressions to report: the APK was never installed or run, so no playback defect was observed.
Re-run this task on a host with an attached Android device (Android 16 preferred) and adb available
before the acceptance criteria can be met.
