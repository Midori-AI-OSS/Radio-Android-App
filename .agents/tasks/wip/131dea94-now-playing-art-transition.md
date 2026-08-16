# [TASK] Smooth Now Playing artwork transitions for song and channel changes

- Status: implemented, verified (host SDK available)
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
- Coil 3.5.0 `useExistingImageAsPlaceholder(true)` keeps the actual old painter as
  the placeholder, so the old artwork stays visible through the 220ms crossfade
  instead of fading from a blank placeholder.
- The initial 1:1 artwork bounds settle to the intrinsic ratio (e.g. 4:7) over 220ms
  via animated width/height instead of snapping. Final behavior preserved: 1:1 art
  crops at `artSize`, non-square art fully fits at its intrinsic ratio.
- The intrinsic ratio is hoisted above `NowPlayingHeroCard`, keyed by displayed
  image cache key, and shared by the channel-slide (from/to) and steady-state card
  instances: a truly new artwork resizes exactly once when its ratio first becomes
  known, and the steady-state card reuses the slide card's ratio instead of
  animating a second time. The last known ratio keeps bounds stable while a new
  artwork is still loading.
- `useExistingImageAsPlaceholder(true)` only helps the steady-state card's own
  painter, not the fresh AsyncImage instances used by the channel-slide cards. The
  painter for the last successfully loaded artwork is now retained above the hero
  card and passed as the `placeholder` to every hero-card instance, so fresh
  slide cards paint the previous art instantly and Coil's 220ms crossfade fades to
  the new art. Only successes whose cache key matches the currently displayed art
  update the retained painter, and a confirmed artless payload clears it, so stale
  art cannot persist after a no-art state. Bounds/ratio/crop/fit logic is untouched
  (no new modifiers), so resize-once and 1:1 crop / non-square full-fit behavior
  are unchanged.

Unchanged on purpose: Android Auto/session presentation, browse coverage, polling,
service/API behavior, dock, placeholders, and dependencies.
`UI_UX_STANDARDS.md` animation table gained the two 220ms rows.

## Verification

- `./gradlew test` — PASSED (SDK available via `local.properties` at
  `/tmp/agents-artifacts/android-sdk`).
- `./gradlew :app:assembleDebug` — PASSED.
- Channel-slide painter retention — `./gradlew test` PASSED,
  `./gradlew :app:assembleDebug` PASSED after the retained-painter change
  (host SDK; Docker unavailable on this host).
