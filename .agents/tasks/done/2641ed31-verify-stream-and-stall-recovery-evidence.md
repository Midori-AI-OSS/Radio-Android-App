# [TASK] Verify stream termination and stalled-playback evidence

- Status: done (evidence gathered; blockers recorded)
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`

## Goal

Resolve the evidence-dependent recovery decisions before implementing them: what a normal stream end means, and which signal/interval identifies stalled foreground playback without treating ordinary buffering as failure.

## Scope

- Re-check the authoritative current `/radio/v1/stream` implementation and, separately, deployed behavior. Start with `Cookie-Club-Bots/Webserver-RS/crates/bin/src/routes.rs` and `Cookie-Club-Bots/Webserver-RS/crates/radio/src/transcode.rs` when available. Record the exact server revision/version, whether it is known to match the deployment, and whether the normal ready-track response is finite per track or remains open across tracks. If the deployed version or behavior cannot be verified, mark it unknown rather than treating source inspection as proof of deployment; do not rely on older task notes as proof.
- If a device is available, observe a natural stream boundary and a playback stall/interruption. Record the device/OS and app build, what triggered each observation, whether audio was audible, Media3 state/callback observations (`playbackState`, `playWhenReady`, `isPlaying`), and whether the stream request ended, failed, or remained active.
- Assess whether `player.isPlaying` observes the reported stall and how long ordinary startup/rebuffering lasts. Recommend a named watchdog interval with evidence and a quick-recovery rationale.
- Record unknowns and blockers in this task; do not change application source or choose an unsupported timeout.

## Acceptance criteria

- [x] The stream-end classification is supported by current server/source or runtime evidence, with its revision/version and deployment match recorded; any unverified deployed behavior is explicitly marked unknown.
- [x] For each available normal-end, error, and stall observation, the trigger, audibility, request outcome, and Media3 observations are recorded; unavailable observations are explicitly marked unknown or blocked.
- [x] A watchdog signal and interval are recommended with evidence, or the missing evidence is recorded as a blocker. No timeout is treated as settled without this decision.
- [x] No application source is changed.

## Evidence (2026-10-24)

### Source and deployed version

- Android source checked at branch `midoriaiagents/ginger-acdda906fc`, commit `82c789f27bf4a41eeca86b19fa2868e88ab81360`. `app/build.gradle.kts` declares default `versionCode = 1` and `versionName = "0.1.0"`; no installed app build was available to identify.
- The requested `Cookie-Club-Bots/Webserver-RS/crates/bin/src/routes.rs` and `Cookie-Club-Bots/Webserver-RS/crates/radio/src/transcode.rs` source was not available in a local checkout. The GitHub API lookup returned 404 and `git ls-remote` could not authenticate, so the exact server source revision is **UNKNOWN** (the repository may be private or unavailable from this environment).
- The live Android default host, `https://radio.midori-ai.xyz`, returned `/health` version `webserver.health.v1` and `/radio/v1/current` version `radio.v1`. These identify API schemas, not a server build/revision. Whether the deployed service matches any source revision is **UNKNOWN**.

### Deployed stream response

- At 2026-10-24 15:58 UTC, `/radio/v1/current?channel=` reported the ready `Chill Mix` track (`vibes/Chill Mix.mp3`, duration 189600 ms, position 84889 ms; `warmup_active: false`). A direct `GET https://radio.midori-ai.xyz/radio/v1/stream?channel=&q=low` returned HTTP/2 200 with `Content-Type: audio/mpeg`; curl completed normally in 3.795072 seconds after receiving 1,032,600 bytes. `ffprobe` measured the complete response body as 85.920 seconds of audio, title `Chill Mix`.
- A subsequent current-track query at 15:58:36 still reported `Chill Mix`, now at position 109853 ms. The completed response body and track-duration/position observations support that this sampled normal ready-track response is finite per-track content, not one open multi-track response. This is one deployed runtime sample; no following-track request or player boundary was observed. The deployment build identity remains **UNKNOWN**.

### Playback, error, and stall observations

| Case | Trigger / audibility | Request outcome and Media3 observations |
| --- | --- | --- |
| Direct stream response EOF | Direct HTTP GET; audibility not observed | HTTP/2 200 response completed as above; no device/player was involved, so no `playbackState`, `playWhenReady`, or `isPlaying` observations. |
| Natural playback boundary | **BLOCKED** — no device/adb available; audibility unknown | No Media3 callback or follow-on request observed. |
| Player error | **BLOCKED** — no device/adb available; no error was injected | Audibility, request outcome, and Media3 state unknown. |
| Stall/interruption | **BLOCKED** — no device/adb available; no stall was reproduced | Audibility, request outcome, and Media3 state unknown. |

`adb devices -l` could not run because `adb` is not installed (`command not found`). Device model, OS, installed app build, and natural playback observations are therefore unavailable.

### Watchdog assessment

- Media3 1.11.0 is configured in `app/build.gradle.kts:89-90`; `Player.isPlaying` is defined from `STATE_READY`, `playWhenReady`, and no playback suppression (see AndroidX Media3 `BasePlayer.isPlaying`). It represents the player's active-playing state, not proof that audio is physically audible. In `app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackService.kt:1191-1218`, the current `PlayerEventListener` handles `isPlaying == true` by marking playback recovered, but its false branch does not schedule recovery; `STATE_ENDED` goes through `queueReconnect("Stream ended")`.
- Whether `isPlaying` becomes false for the reported silent-playback failure is **UNKNOWN** without reproducing it on a device. Ordinary Android startup/rebuffer durations are also **UNKNOWN**. The 3.795-second HTTP transfer is not a playback startup/rebuffer measurement (the downloaded payload contains 85.920 seconds of audio).
- `player.isPlaying` remains a candidate liveness signal, but there is insufficient evidence to recommend a watchdog interval. **Blocker:** obtain device callback/audibility observations and ordinary startup/rebuffer timing before selecting or settling a timeout.

No application source was changed; app tests/builds were not run for this evidence-only task.
