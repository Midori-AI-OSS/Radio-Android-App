package xyz.midoriai.radio.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RadioPlaybackRecoveryOrchestratorTest {
    @Test
    fun eof_opensImmediatelyWithoutErrorDelay() {
        val orchestrator = RadioPlaybackRecoveryOrchestrator()
        orchestrator.sourceOpened(4L)
        val event = checkNotNull(orchestrator.captureTerminalEvent())
        val effects = mutableListOf<String>()

        orchestrator.handleTerminalEvent(
            event = event,
            playbackDesired = true,
            playbackState = Player.STATE_ENDED,
            sourceIsCurrent = true,
            canOpen = true,
            openImmediately = {
                effects += "open"
                FreshStreamOpenResult.Opened
            },
            scheduleErrorRetry = { effects += "retry" },
        )

        assertEquals(listOf("open"), effects)
        assertEquals(0, orchestrator.errorAttempt())
    }

    @Test
    fun failedEofOpen_fallsBackToDelayedUnboundedErrorRetry() {
        val orchestrator = RadioPlaybackRecoveryOrchestrator()
        orchestrator.sourceOpened(8L)
        val event = checkNotNull(orchestrator.captureTerminalEvent())
        val retries = mutableListOf<RadioPlaybackRecoveryOrchestrator.ErrorRetry>()

        orchestrator.handleTerminalEvent(
            event = event,
            playbackDesired = true,
            playbackState = Player.STATE_ENDED,
            sourceIsCurrent = true,
            canOpen = true,
            openImmediately = { FreshStreamOpenResult.Failed },
            scheduleErrorRetry = { retries += orchestrator.nextErrorRetry() },
        )
        repeat(8) { retries += orchestrator.nextErrorRetry() }

        assertEquals(50L, retries.first().delayMs)
        assertEquals(List(4) { 1500L }, retries.takeLast(4).map { it.delayMs })
        assertEquals(9, orchestrator.errorAttempt())
    }

    @Test
    fun rejectedEofOpen_doesNotBecomeAnErrorRetry() {
        val orchestrator = RadioPlaybackRecoveryOrchestrator()
        orchestrator.sourceOpened(3L)
        var retryCount = 0

        orchestrator.handleTerminalEvent(
            event = checkNotNull(orchestrator.captureTerminalEvent()),
            playbackDesired = true,
            playbackState = Player.STATE_ENDED,
            sourceIsCurrent = true,
            canOpen = true,
            openImmediately = { FreshStreamOpenResult.Rejected },
            scheduleErrorRetry = { retryCount += 1 },
        )

        assertEquals(0, retryCount)
    }

    @Test
    fun eof_suppressesPausedDuplicateAndStaleSourceEvents() {
        val orchestrator = RadioPlaybackRecoveryOrchestrator()
        orchestrator.sourceOpened(1L)
        val staleEvent = checkNotNull(orchestrator.captureTerminalEvent())
        orchestrator.sourceOpened(2L)
        val currentEvent = checkNotNull(orchestrator.captureTerminalEvent())
        var opens = 0
        val invoke = { event: RadioPlaybackRecoveryOrchestrator.TerminalEvent, desired: Boolean ->
            orchestrator.handleTerminalEvent(
                event = event,
                playbackDesired = desired,
                playbackState = Player.STATE_ENDED,
                sourceIsCurrent = true,
                canOpen = true,
                openImmediately = {
                    opens += 1
                    orchestrator.sourceOpened(3L)
                    FreshStreamOpenResult.Opened
                },
                scheduleErrorRetry = {},
            )
        }

        invoke(staleEvent, true)
        invoke(currentEvent, false)
        invoke(currentEvent, true)
        invoke(currentEvent, true)
        val invalidatedEvent = checkNotNull(orchestrator.captureTerminalEvent())
        orchestrator.invalidateInstalledSource()
        invoke(invalidatedEvent, true)

        assertEquals(1, opens)
    }

    @Test
    fun healthyAndPauseCleanup_resetErrorRecoveryState() {
        val orchestrator = RadioPlaybackRecoveryOrchestrator()
        orchestrator.nextErrorRetry()
        orchestrator.nextErrorRetry()

        orchestrator.markHealthy()
        assertEquals(0, orchestrator.errorAttempt())
        assertEquals(50L, orchestrator.nextErrorRetry().delayMs)

        orchestrator.cancelPendingRecovery()
        assertEquals(0, orchestrator.errorAttempt())
        assertEquals(50L, orchestrator.nextErrorRetry().delayMs)
    }

    @Test
    fun cancellationDuringOpen_isRethrownInsteadOfConvertedToFailure() {
        val cancellation = CancellationException("cancelled")
        val coordinator = FreshStreamOpenCoordinator()

        val thrown = assertThrows(CancellationException::class.java) {
            runFreshStreamOpenCatchingFailure {
                runFreshStreamOpenTransaction(
                    coordinator = coordinator,
                    cancelPendingReconnect = {},
                ) {
                    throw cancellation
                }
            }
        }

        assertEquals(cancellation, thrown)
        assertEquals(2L, coordinator.tryBeginOpen())
        coordinator.finishOpen()
        assertEquals(
            FreshStreamOpenResult.Failed,
            runFreshStreamOpenCatchingFailure { throw IllegalStateException("failed") },
        )
    }
}
