# [FIX] Remove duplicate bottom navigation inset from Now Playing dock

- Status: implemented, verification blocked-by-environment
- Source: agreed Now Playing dock sizing correction
- Owner: coder

## Change

`app/src/main/java/xyz/midoriai/radio/ui/screens/nowplaying/NowPlayingScreen.kt`: removed
`.navigationBarsPadding()` from the `NowPlayingControlDock` content Box, and its now-unused
import. The app-level `Scaffold` in `MidoriAIRadioApp.kt` already supplies bottom system-bar
clearance via `innerPadding`, so the dock was applying the navigation bar inset twice.

Unchanged on purpose: dock min height (118.dp), content padding (20.dp horizontal / 18.dp
vertical), button sizes (56/68/56/48.dp), icon sizes (30/38/30/24.dp), and 18.dp spacers.
`UI_UX_STANDARDS.md` documents only the unchanged 18.dp dock padding, so no doc update needed.

## Verification

- Historical verification at task creation: `./gradlew test` was blocked because no Android SDK
  was available; `:app:assembleDebug` was not run for the same reason.
- Docker/PixelArch container — unavailable (permission denied on `/var/run/docker.sock`).
- Static checks passed: no remaining `navigationBarsPadding` references in `app/src/`;
  the diff touches only one modifier chain plus its import.

## Verification update — 2026-11-21

- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug --rerun-tasks` — PASS (36 actionable tasks).
- `./gradlew test --rerun-tasks` — BLOCKED before test execution: the environment proxy returns HTTP 403 when Maven Central redirects Robolectric's `nativeruntime-dist-compat:1.0.18` JAR to `gcp.mirrors.maven.org`.
- Static search still finds no `navigationBarsPadding` references in `app/src/`.

Keep verification open until the standard unit-test task runs successfully on a host that can fetch Robolectric's runtime dependencies.
