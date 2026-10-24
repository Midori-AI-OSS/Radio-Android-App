package xyz.midoriai.radio.playback

import androidx.media3.common.Player

internal val errorReconnectDelaysMs: List<Long> = listOf(50L, 100L, 200L, 400L, 800L, 1500L)

internal fun errorReconnectDelayMs(attempt: Int): Long {
    require(attempt > 0) { "attempt must be positive" }
    return errorReconnectDelaysMs[(attempt - 1).coerceAtMost(errorReconnectDelaysMs.lastIndex)]
}

internal fun shouldContinueEndOfStreamRecovery(
    playbackDesired: Boolean,
    playbackState: Int,
    sourceIsCurrent: Boolean,
): Boolean {
    return playbackDesired &&
        playbackState == Player.STATE_ENDED &&
        sourceIsCurrent
}

internal fun shouldRestoreHealthyPlaybackAfterError(
    playbackState: Int,
    playWhenReady: Boolean,
    isPlaying: Boolean,
): Boolean = isHealthyPlayback(playbackState, playWhenReady, isPlaying)

internal fun shouldRetryFailedRecoveryOpen(result: FreshStreamOpenResult): Boolean {
    return result == FreshStreamOpenResult.Failed
}

/** Coalesces repeated terminal callbacks for the same installed player source. */
internal class TerminalOpenCoalescer {
    private var pendingSourceGeneration: Long? = null

    fun tryBegin(sourceGeneration: Long): Boolean {
        if (pendingSourceGeneration == sourceGeneration) {
            return false
        }
        pendingSourceGeneration = sourceGeneration
        return true
    }

    fun sourceOpened() {
        pendingSourceGeneration = null
    }

    fun cancel() {
        pendingSourceGeneration = null
    }
}
