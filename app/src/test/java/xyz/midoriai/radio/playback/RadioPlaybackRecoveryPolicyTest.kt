package xyz.midoriai.radio.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackRecoveryPolicyTest {
    @Test
    fun endOfStream_desiredPlaybackAndCurrentSourceContinuesImmediately() {
        assertTrue(
            shouldContinueEndOfStreamRecovery(
                playbackDesired = true,
                playbackState = Player.STATE_ENDED,
                sourceIsCurrent = true,
            ),
        )
    }

    @Test
    fun endOfStream_pausedOrStoppedPlaybackDoesNotReopen() {
        assertFalse(
            shouldContinueEndOfStreamRecovery(
                playbackDesired = false,
                playbackState = Player.STATE_ENDED,
                sourceIsCurrent = true,
            ),
        )
    }

    @Test
    fun endOfStream_staleOrDuplicateCallbackDoesNotReopenNewerSource() {
        assertFalse(
            shouldContinueEndOfStreamRecovery(
                playbackDesired = true,
                playbackState = Player.STATE_ENDED,
                sourceIsCurrent = false,
            ),
        )
        assertFalse(
            shouldContinueEndOfStreamRecovery(
                playbackDesired = true,
                playbackState = Player.STATE_BUFFERING,
                sourceIsCurrent = true,
            ),
        )
    }

    @Test
    fun terminalOpenCoalescer_rejectsDuplicateUntilANewSourceOpens() {
        val coalescer = TerminalOpenCoalescer()

        assertTrue(coalescer.tryBegin(8L))
        assertFalse(coalescer.tryBegin(8L))
        assertTrue(coalescer.tryBegin(9L))
        coalescer.cancel()
        assertTrue(coalescer.tryBegin(9L))
        coalescer.sourceOpened()
        assertTrue(coalescer.tryBegin(10L))
    }

    @Test
    fun failedEofOpen_usesErrorRetryTimingWhileSuccessfulOrRejectedOpenDoesNot() {
        assertTrue(shouldRetryFailedRecoveryOpen(FreshStreamOpenResult.Failed))
        assertFalse(shouldRetryFailedRecoveryOpen(FreshStreamOpenResult.Opened))
        assertFalse(shouldRetryFailedRecoveryOpen(FreshStreamOpenResult.Rejected))
        assertEquals(50L, errorReconnectDelayMs(1))
    }

    @Test
    fun healthyErrorSignal_restoresPlaybackInsteadOfSchedulingRecovery() {
        assertTrue(
            shouldRestoreHealthyPlaybackAfterError(
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = true,
            ),
        )
        assertFalse(
            shouldRestoreHealthyPlaybackAfterError(
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                isPlaying = false,
            ),
        )
    }
}
