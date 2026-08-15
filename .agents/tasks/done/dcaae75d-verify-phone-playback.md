# [T7] Verify phone playback behavior with the modernized APK

- Status: wip
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

- [ ] All phone playback checks above pass on the test device
- [ ] Results (pass/fail per check, device/OS version) recorded in this task file
- [ ] Any regression is reported with repro steps and a new task file, not silently fixed

## Notes

- This task requires physical-device testing; if no device is available, state the blocker in the task file.
- App install source should be the GitHub APK path (sideload), matching the issue environment.
