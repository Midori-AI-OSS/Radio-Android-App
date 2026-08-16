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

- `./gradlew test` — BLOCKED: no Android SDK on this host (`ANDROID_HOME` unset, no
  `local.properties`; "SDK location not found").
- `./gradlew :app:assembleDebug` — not run; same SDK blocker.
- Docker/PixelArch container — unavailable (permission denied on `/var/run/docker.sock`).
- Static checks passed: no remaining `navigationBarsPadding` references in `app/src/`;
  the diff touches only one modifier chain plus its import.

Run `./gradlew test` and `./gradlew :app:assembleDebug` in the PixelArch container (or a
host with an Android SDK) before closing this out.
