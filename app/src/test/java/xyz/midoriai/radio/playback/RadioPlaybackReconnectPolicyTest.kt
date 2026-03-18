package xyz.midoriai.radio.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackReconnectPolicyTest {
    @Test
    fun isHealthyPlayback_requiresReadyWhenReadyAndPlaying() {
        assertTrue(
            isHealthyPlayback(
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = true,
            ),
        )

        assertFalse(
            isHealthyPlayback(
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                isPlaying = true,
            ),
        )
        assertFalse(
            isHealthyPlayback(
                playbackState = Player.STATE_READY,
                playWhenReady = false,
                isPlaying = true,
            ),
        )
        assertFalse(
            isHealthyPlayback(
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun shouldExecuteDelayedReconnect_requiresDesiredPlaybackAndMatchingGeneration() {
        assertTrue(
            shouldExecuteDelayedReconnect(
                playbackDesired = true,
                queuedGeneration = 7L,
                currentGeneration = 7L,
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                isPlaying = false,
            ),
        )

        assertFalse(
            shouldExecuteDelayedReconnect(
                playbackDesired = false,
                queuedGeneration = 7L,
                currentGeneration = 7L,
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                isPlaying = false,
            ),
        )
        assertFalse(
            shouldExecuteDelayedReconnect(
                playbackDesired = true,
                queuedGeneration = 7L,
                currentGeneration = 8L,
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun shouldExecuteDelayedReconnect_abortsAfterPlaybackRecovers() {
        assertFalse(
            shouldExecuteDelayedReconnect(
                playbackDesired = true,
                queuedGeneration = 7L,
                currentGeneration = 7L,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = true,
            ),
        )
    }
}
