# [T8] Verify Android Auto discovery and browsing with the modernized APK

- Status: wip
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
