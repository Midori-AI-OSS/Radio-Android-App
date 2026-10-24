# [TASK] Verify stream termination and stalled-playback evidence

- Status: ready
- Priority: high
- Source: approved foreground audio reliability fix
- Owner: coder
- Parent: `.agents/tasks/8a0774c5-simplify-radio-stream-recovery.md`

## Goal

Resolve the evidence-dependent recovery decisions before implementing them: what a normal stream end means, and which signal/interval identifies stalled foreground playback without treating ordinary buffering as failure.

## Scope

- Re-check the authoritative current `/radio/v1/stream` implementation and deployed behavior. Start with `Cookie-Club-Bots/Webserver-RS/crates/bin/src/routes.rs` and `Cookie-Club-Bots/Webserver-RS/crates/radio/src/transcode.rs` when available. Record the server revision/version and whether the normal response is finite per track or remains open across tracks; do not rely on older task notes as proof.
- If a device is available, observe a natural stream boundary and a playback stall/interruption. Capture Media3 state/callback observations (`playbackState`, `playWhenReady`, `isPlaying`) and whether the stream request ended or failed.
- Assess whether `player.isPlaying` observes the reported stall and how long ordinary startup/rebuffering lasts. Recommend a named watchdog interval with evidence and a quick-recovery rationale.
- Record unknowns and blockers in this task; do not change application source or choose an unsupported timeout.

## Acceptance criteria

- [ ] The stream-end classification is supported by current server/source or runtime evidence, with its revision/version recorded; otherwise it is explicitly marked unknown.
- [ ] The expected Media3 observations for normal end, error, and stall are distinguished where evidence is available.
- [ ] A watchdog signal and interval are recommended with evidence, or the missing evidence is recorded as a blocker. No timeout is treated as settled without this decision.
- [ ] No application source is changed.
