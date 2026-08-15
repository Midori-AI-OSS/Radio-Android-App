# [T8] Verify Android Auto discovery and browsing with the modernized APK

- Status: blocked-by-environment
- Source: issue #3 (Modernize Android dependency stack and verify Android Auto discovery)
- Owner: coder (device + Android Auto required)
- Depends on: dcaae75d (phone playback verification)

## Goal

Verify the modernized APK is discovered by Android Auto and supports browse/playback/channel controls, matching the issue's acceptance criteria.

## Scope

Using the T6 debug APK sideloaded on the Android 16 test device with Android Auto developer mode and **Unknown sources** enabled:

- Confirm the app appears in Android Auto -> Customize launcher
- Confirm media browsing works (channels listed and browsable)
- Confirm playback and channel controls work through Android Auto
- Check for any errors in logcat related to the MediaLibraryService/MediaSessionService/legacy MediaBrowserService during discovery

Record all results in this task file. Keep the issue's historical note accurate: the earlier intermittent disappearance was not tied to a specific app bug and remains observational.

## Acceptance criteria

- [ ] App discovered in Android Auto launcher / Customize launcher with Unknown sources enabled
- [ ] Browse/playback/channel controls work through Android Auto
- [ ] Results (pass/fail per check, device/OS versions) recorded in this task file
- [ ] Any discovery failure is documented with logcat evidence and a new task file

## Notes

- If Android Auto (with a connected car head unit or the Android Auto head unit emulator) is unavailable, state the blocker in the task file; do not mark the criterion met without a device run.
- This is the validation task for the acceptance criterion that motivated the issue.

## Blocker evidence (2026-08-15)

Same host as dcaae75d: no Android device attached, no adb installed, no Android SDK/emulator,
no Docker access (permission denied on the Docker socket), so no head-unit run, DHU session, or
logcat capture is possible here. None of the acceptance criteria can be executed in this
environment.

## Taskmaster resolution (2026-08-15)

Blocked by environment; stays in `.agents/tasks/wip/` as actionable for a device host.
Dependency dcaae75d (T7) must be unblocked first, since this task reuses the same sideloaded
T6 debug APK on the same device.

### Next steps (for a coder on a host with a device)

1. Complete dcaae75d first (same host/device; its next steps apply).
2. With the APK installed: enable Android Auto developer mode and **Unknown sources**; use a
   connected head unit or the Android Auto head unit emulator (DHU).
3. Execute the four Scope checks; record pass/fail per check plus device/OS in this file.
4. Capture `adb logcat` during discovery and check for MediaLibraryService/MediaSessionService/
   legacy MediaBrowserService errors; document any failure with logcat evidence and a new task file.
5. With all checks passing, set `Status: done` and archive to `.agents/tasks/done/`.
