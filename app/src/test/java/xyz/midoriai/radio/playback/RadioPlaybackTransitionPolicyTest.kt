package xyz.midoriai.radio.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackTransitionPolicyTest {
    @Test
    fun shouldRestoreSelectedChannelAfterTransition_restoresAutoAdvanceToDifferentChannel() {
        assertTrue(
            shouldRestoreSelectedChannelAfterTransition(
                playbackDesired = true,
                selectedChannel = "focus",
                transitionedChannel = "chill",
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            ),
        )
    }

    @Test
    fun shouldRestoreSelectedChannelAfterTransition_restoresRepeatWrapToDifferentChannel() {
        assertTrue(
            shouldRestoreSelectedChannelAfterTransition(
                playbackDesired = true,
                selectedChannel = "focus",
                transitionedChannel = "all",
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
            ),
        )
    }

    @Test
    fun shouldRestoreSelectedChannelAfterTransition_allowsExplicitTransitions() {
        assertFalse(
            shouldRestoreSelectedChannelAfterTransition(
                playbackDesired = true,
                selectedChannel = "focus",
                transitionedChannel = "chill",
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
            ),
        )
        assertFalse(
            shouldRestoreSelectedChannelAfterTransition(
                playbackDesired = true,
                selectedChannel = "focus",
                transitionedChannel = "chill",
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            ),
        )
    }

    @Test
    fun shouldRestoreSelectedChannelAfterTransition_ignoresSameChannelOrStoppedPlayback() {
        assertFalse(
            shouldRestoreSelectedChannelAfterTransition(
                playbackDesired = true,
                selectedChannel = " Focus ",
                transitionedChannel = "focus",
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
            ),
        )
        assertFalse(
            shouldRestoreSelectedChannelAfterTransition(
                playbackDesired = false,
                selectedChannel = "focus",
                transitionedChannel = "chill",
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            ),
        )
    }
}
