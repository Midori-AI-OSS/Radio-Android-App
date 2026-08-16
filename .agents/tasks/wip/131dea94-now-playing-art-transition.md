# [TASK] Smooth Now Playing artwork transitions for song and channel changes

- Status: implemented, verification blocked-by-environment
- Source: approved Now Playing transition plan
- Owner: coder

## Change

`app/src/main/java/xyz/midoriai/radio/ui/screens/nowplaying/NowPlayingScreen.kt`:

- Art for the selected channel is no longer blanked while its track ID briefly lags
  the current track (current track and art arrive on separate polls). Art is shown
  whenever it exists for the selected channel, regardless of track ID match.
- While art for the selected channel is pending (service still reports the previous
  channel's art, or none yet), the last displayed art and its gradient stay visible;
  they are replaced only when real art arrives, and cleared only when the service
  confirms the selected channel has no art (`has_art=false`). Rapid channel switches
  cannot strand stale art: art for a deselected channel is ignored, and a confirmed
  artless payload clears the sticky art.
- New art crossfades in over 220ms (Coil `crossfade`, `FastOutSlowInEasing`).
- The initial 1:1 artwork bounds settle to the intrinsic ratio (e.g. 4:7) over 220ms
  via animated width/height instead of snapping. Final behavior preserved: 1:1 art
  crops at `artSize`, non-square art fully fits at its intrinsic ratio.
- The loaded ratio is remembered across snapshots so pending/crossfading art never
  resets the bounds.

Unchanged on purpose: Android Auto/session presentation, browse coverage, polling,
service/API behavior, dock, placeholders, and dependencies.
`UI_UX_STANDARDS.md` animation table gained the two 220ms rows.

## Verification

- `./gradlew test` — BLOCKED: no Android SDK on this host (`SDK location not found`,
  no `local.properties`, `ANDROID_HOME` unset).
- `./gradlew :app:assembleDebug` — not run; same SDK blocker.
- Docker/PixelArch container — unavailable (permission denied on `/var/run/docker.sock`).
- Static checks passed: brace/paren balance on the edited file; no leftover references
  to the removed identifiers; no service/API/Android Auto files touched; no test scans
  `NowPlayingScreen.kt`.

Run `./gradlew test` and `./gradlew :app:assembleDebug` in the PixelArch container (or a
host with an Android SDK) before closing this out.
