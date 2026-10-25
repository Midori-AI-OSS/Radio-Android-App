# Audit: EOF and error recovery task

- Date: 2026-10-25
- Reviewed task: `.agents/tasks/wip/72398e06-separate-eof-and-error-recovery.md`
- Result: incomplete; returned to WIP.

## Findings

1. **EOF source identity is still callback-time state.** `RadioPlaybackService.continueAfterEndOfStream()` reads `activeSourceGeneration` when called and uses it for `TerminalOpenCoalescer`; the player listener supplies only the state value. The current player-state/channel/quality checks are useful but do not bind the terminal event to the source that produced it. This contradicts the task's source-generation acceptance criterion.
2. **Recovery orchestration is not covered by tests.** `RadioPlaybackRecoveryPolicyTest` and `FreshStreamOpenCoordinatorTest` cover helper behavior, but no test exercises `PlayerEventListener` → EOF open, error retry scheduling, or service retry-state cleanup. The existing `MediaSessionMutationIntegrationTest` covers session mutation rather than recovery.
3. **Cancellation may be converted into a retryable failure.** `openFreshStream()` catches `RuntimeException` around the open transaction. A `CancellationException` thrown through that synchronous path is therefore converted to `FreshStreamOpenResult.Failed`; it should be rethrown before the general runtime catch and covered by a test.
4. **Retry wording needed an explicit decision.** The task now states the approved behavior: error attempts continue indefinitely with delays `50, 100, 200, 400, 800, 1500 ms`, capped at `1500 ms`. EOF itself opens immediately and does not consume an error attempt; only a `Failed` immediate open enters error backoff, while `Rejected` does not.
5. **Finite-response evidence is not sufficiently traceable for closure.** The current task records deployed finite-response evidence, but its referenced evidence task is absent from the current `.agents/tasks` tree. The parent design document describes the finite-response protocol, while the Media3 `Player` EOF boundary remains unverified. Keep the runtime claim blocked and link the evidence used for classification; device verification remains in `.agents/tasks/wip/5a84a5c1-verify-foreground-audio-recovery-on-device.md`.

## Verification

- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew test` — PASS; Gradle reported tasks up-to-date.
- `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ANDROID_SDK_ROOT=/tmp/agents-artifacts/android-sdk ./gradlew :app:assembleDebug` — PASS; Gradle reported tasks up-to-date.
- Docker verification was unavailable because access to `/var/run/docker.sock` was denied.

No app source or other task files were changed.
