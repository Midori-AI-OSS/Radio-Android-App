package xyz.midoriai.radio.playback

/** Small state seam joining installed-source EOF events to error retry policy. */
internal class RadioPlaybackRecoveryOrchestrator {
    class InstalledSource internal constructor()

    class TerminalEvent internal constructor(internal val source: InstalledSource)

    data class ErrorRetry(val attempt: Int, val delayMs: Long)

    private var installedSource: InstalledSource? = null
    private var handledTerminalSource: InstalledSource? = null
    private var errorAttempt = 0

    fun sourceOpened(): InstalledSource {
        return InstalledSource().also {
            installedSource = it
            handledTerminalSource = null
        }
    }

    fun captureTerminalEvent(): TerminalEvent? = installedSource?.let(::TerminalEvent)

    fun invalidateInstalledSource() {
        installedSource = null
        handledTerminalSource = null
    }

    fun handleTerminalEvent(
        event: TerminalEvent,
        playbackDesired: Boolean,
        playbackState: Int,
        sourceIsCurrent: Boolean,
        canOpen: Boolean,
        openImmediately: () -> FreshStreamOpenResult,
        scheduleErrorRetry: () -> Unit,
    ) {
        val currentSource = installedSource
        if (
            event.source !== currentSource ||
            handledTerminalSource === currentSource ||
            !canOpen ||
            !shouldContinueEndOfStreamRecovery(
                playbackDesired = playbackDesired,
                playbackState = playbackState,
                sourceIsCurrent = sourceIsCurrent,
            )
        ) {
            return
        }

        handledTerminalSource = currentSource
        if (shouldRetryFailedRecoveryOpen(openImmediately())) {
            scheduleErrorRetry()
        }
    }

    fun nextErrorRetry(): ErrorRetry {
        if (errorAttempt < Int.MAX_VALUE) {
            errorAttempt += 1
        }
        return ErrorRetry(errorAttempt, errorReconnectDelayMs(errorAttempt))
    }

    fun errorAttempt(): Int = errorAttempt

    fun resetErrorAttempts() {
        errorAttempt = 0
    }

    fun markHealthy() {
        resetErrorAttempts()
        handledTerminalSource = null
    }

    fun cancelPendingRecovery() {
        resetErrorAttempts()
        handledTerminalSource = null
    }
}

internal inline fun runFreshStreamOpenCatchingFailure(
    open: () -> FreshStreamOpenResult,
): FreshStreamOpenResult {
    return try {
        open()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        FreshStreamOpenResult.Failed
    }
}
