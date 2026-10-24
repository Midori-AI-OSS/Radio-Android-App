package xyz.midoriai.radio.playback

import androidx.media3.common.Player
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
                queuedGeneration = 8L,
                currentGeneration = 8L,
            ),
        )
    }

    @Test
    fun endOfStream_pausedOrStoppedPlaybackDoesNotReopen() {
        assertFalse(
            shouldContinueEndOfStreamRecovery(
                playbackDesired = false,
                playbackState = Player.STATE_ENDED,
                queuedGeneration = 8L,
                currentGeneration = 8L,
            ),
        )
    }

    @Test
    fun endOfStream_staleOrNoLongerEndedCallbackDoesNotReopenNewerSource() {
        assertFalse(
            shouldContinueEndOfStreamRecovery(
                playbackDesired = true,
                playbackState = Player.STATE_ENDED,
                queuedGeneration = 8L,
                currentGeneration = 9L,
            ),
        )
        assertFalse(
            shouldContinueEndOfStreamRecovery(
                playbackDesired = true,
                playbackState = Player.STATE_BUFFERING,
                queuedGeneration = 8L,
                currentGeneration = 8L,
            ),
        )
    }
}
