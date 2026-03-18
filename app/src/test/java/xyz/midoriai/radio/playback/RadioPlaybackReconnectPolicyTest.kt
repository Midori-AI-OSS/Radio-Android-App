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

    @Test
    fun shouldSeekToReconnectTarget_seeksWhenReconnectingEndedSameItem() {
        assertTrue(
            shouldSeekToReconnectTarget(
                currentMediaItemIndex = 0,
                targetIndex = 0,
                playbackState = Player.STATE_ENDED,
            ),
        )
    }

    @Test
    fun shouldSeekToReconnectTarget_avoidsSeekWhenTargetAlreadyActiveAndNotEnded() {
        assertFalse(
            shouldSeekToReconnectTarget(
                currentMediaItemIndex = 0,
                targetIndex = 0,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun shouldSeekToReconnectTarget_seeksWhenChangingItems() {
        assertTrue(
            shouldSeekToReconnectTarget(
                currentMediaItemIndex = 0,
                targetIndex = 1,
                playbackState = Player.STATE_READY,
            ),
        )
    }
}
