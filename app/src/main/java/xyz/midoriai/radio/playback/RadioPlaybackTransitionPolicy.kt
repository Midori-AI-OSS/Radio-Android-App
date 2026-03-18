package xyz.midoriai.radio.playback

import androidx.media3.common.Player

internal fun isAutomaticChannelTransitionReason(reason: Int): Boolean {
    return reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
}

internal fun shouldRestoreSelectedChannelAfterTransition(
    playbackDesired: Boolean,
    selectedChannel: String,
    transitionedChannel: String,
    reason: Int,
): Boolean {
    return playbackDesired &&
        isAutomaticChannelTransitionReason(reason) &&
        normalizePersistedChannel(selectedChannel) != normalizePersistedChannel(transitionedChannel)
}
