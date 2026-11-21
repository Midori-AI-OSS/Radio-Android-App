package xyz.midoriai.radio.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import xyz.midoriai.radio.ui.screens.nowplaying.RadioPlaybackState

internal fun interface RecoveryRetryHandle {
    fun cancel()
}

internal fun interface RecoveryRetryScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): RecoveryRetryHandle
}

/** Thin production listener seam that keeps Media3 events independently testable. */
internal class RadioPlaybackPlayerEventListener(
    private val playbackDesired: () -> Boolean,
    private val currentPlaybackState: () -> Int,
    private val onPlayerFailure: (PlaybackException) -> Unit,
    private val onPlaybackState: (Int) -> Unit,
    private val onStopped: () -> Unit,
    private val onMediaItem: (MediaItem?) -> Unit,
) : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        onPlayerFailure(error)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (playbackDesired() && isPlaying) {
            onPlaybackState(currentPlaybackState())
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        onPlaybackState(playbackState)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playbackDesired() && !playWhenReady) {
            onStopped()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        onMediaItem(mediaItem)
    }
}

/** Owns EOF routing and the single delayed retry job used by playback failures. */
internal class RadioPlaybackRecoveryController(
    private val coordinator: FreshStreamOpenCoordinator,
    private val scheduler: RecoveryRetryScheduler,
    private val playbackDesired: () -> Boolean,
    private val playbackState: () -> Int,
    private val playWhenReady: () -> Boolean,
    private val isPlaying: () -> Boolean,
    private val sourceIsCurrent: () -> Boolean,
    private val channelSwitchInProgress: () -> Boolean,
    private val openFreshStream: () -> FreshStreamOpenResult,
    private val updatePlaybackState: (RadioPlaybackState) -> Unit,
) {
    private val orchestrator = RadioPlaybackRecoveryOrchestrator()
    private var retryHandle: RecoveryRetryHandle? = null
    private var retryToken = 0L
    private var pendingRecoveryTargetSatisfied: (() -> Boolean)? = null

    fun sourceOpened() {
        orchestrator.sourceOpened()
    }

    fun invalidateInstalledSource() {
        orchestrator.invalidateInstalledSource()
    }

    fun onPlaybackStateChanged(state: Int) {
        if (!playbackDesired()) return

        if (
            isHealthyPlayback(state, playWhenReady(), isPlaying()) &&
            pendingRecoveryTargetSatisfied?.invoke() != false
        ) {
            markPlaybackRecovered()
        }
        if (state == Player.STATE_ENDED) {
            val event = orchestrator.captureTerminalEvent() ?: return
            orchestrator.handleTerminalEvent(
                event = event,
                playbackDesired = playbackDesired(),
                playbackState = playbackState(),
                sourceIsCurrent = sourceIsCurrent(),
                canOpen = coordinator.canRunTerminalOpen(
                    playbackDesired = playbackDesired(),
                    channelSwitchInProgress = channelSwitchInProgress(),
                ),
                openImmediately = openFreshStream,
                scheduleErrorRetry = {
                    queueErrorReconnect(
                        retryOpen = openFreshStream,
                        isStillCurrent = sourceIsCurrent,
                        stopWhenHealthy = true,
                    )
                },
            )
        }
    }

    fun onPlayerError() {
        val healthy = shouldRestoreHealthyPlaybackAfterError(
            playbackState = playbackState(),
            playWhenReady = playWhenReady(),
            isPlaying = isPlaying(),
        )
        if (healthy) {
            if (pendingRecoveryTargetSatisfied?.invoke() != false) {
                markPlaybackRecovered()
            }
            return
        }
        if (pendingRecoveryTargetSatisfied != null) return

        queueErrorReconnect(
            retryOpen = openFreshStream,
            isStillCurrent = sourceIsCurrent,
            stopWhenHealthy = true,
        )
    }

    fun retryFailedOpen(
        result: FreshStreamOpenResult,
        isStillCurrent: () -> Boolean = { true },
        recoveryTargetSatisfied: () -> Boolean = sourceIsCurrent,
        retryOpen: () -> FreshStreamOpenResult = openFreshStream,
    ) {
        if (shouldRetryFailedRecoveryOpen(result)) {
            queueErrorReconnect(
                retryOpen = retryOpen,
                isStillCurrent = isStillCurrent,
                recoveryTargetSatisfied = recoveryTargetSatisfied,
                stopWhenHealthy = false,
            )
        }
    }

    fun replacePendingRetryWithFailedOpen(
        result: FreshStreamOpenResult,
        isStillCurrent: () -> Boolean,
        recoveryTargetSatisfied: () -> Boolean,
        retryOpen: () -> FreshStreamOpenResult,
    ) {
        cancelPendingRetry()
        retryFailedOpen(
            result = result,
            isStillCurrent = isStillCurrent,
            recoveryTargetSatisfied = recoveryTargetSatisfied,
            retryOpen = retryOpen,
        )
    }

    fun resetErrorAttempts() {
        orchestrator.resetErrorAttempts()
    }

    fun cancelPendingRetry() {
        cancelRetryDelayForOpen()
        pendingRecoveryTargetSatisfied = null
    }

    fun cancelRetryDelayForOpen() {
        retryToken += 1
        retryHandle?.cancel()
        retryHandle = null
    }

    fun cancelPendingRecovery() {
        cancelPendingRetry()
        orchestrator.cancelPendingRecovery()
    }

    private fun markPlaybackRecovered() {
        cancelPendingRetry()
        orchestrator.markHealthy()
        updatePlaybackState(RadioPlaybackState.Playing)
    }

    private fun queueErrorReconnect(
        retryOpen: () -> FreshStreamOpenResult,
        isStillCurrent: () -> Boolean,
        recoveryTargetSatisfied: () -> Boolean = sourceIsCurrent,
        stopWhenHealthy: Boolean,
    ) {
        if (!playbackDesired() || retryHandle != null) return

        pendingRecoveryTargetSatisfied = recoveryTargetSatisfied
        val retry = orchestrator.nextErrorRetry()
        val capturedGeneration = coordinator.currentGeneration()
        val token = ++retryToken
        updatePlaybackState(RadioPlaybackState.Reconnecting(retry.attempt, retry.delayMs))
        retryHandle = scheduler.schedule(retry.delayMs) retry@{
            if (token != retryToken) return@retry
            retryHandle = null

            if (!isStillCurrent()) {
                pendingRecoveryTargetSatisfied = null
                return@retry
            }
            if (
                stopWhenHealthy &&
                recoveryTargetSatisfied() &&
                shouldRestoreHealthyPlaybackAfterError(
                    playbackState = playbackState(),
                    playWhenReady = playWhenReady(),
                    isPlaying = isPlaying(),
                )
            ) {
                markPlaybackRecovered()
                return@retry
            }
            if (
                !coordinator.canRunDelayedOpen(
                    queuedGeneration = capturedGeneration,
                    playbackDesired = playbackDesired(),
                    channelSwitchInProgress = channelSwitchInProgress(),
                )
            ) {
                pendingRecoveryTargetSatisfied = null
                return@retry
            }

            val result = retryOpen()
            if (shouldRetryFailedRecoveryOpen(result)) {
                queueErrorReconnect(
                    retryOpen = retryOpen,
                    isStillCurrent = isStillCurrent,
                    recoveryTargetSatisfied = recoveryTargetSatisfied,
                    stopWhenHealthy = stopWhenHealthy,
                )
            } else {
                pendingRecoveryTargetSatisfied = null
            }
        }
    }
}
