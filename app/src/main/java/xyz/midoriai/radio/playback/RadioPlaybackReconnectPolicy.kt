package xyz.midoriai.radio.playback

import androidx.media3.common.Player

internal fun isHealthyPlayback(
    playbackState: Int,
    playWhenReady: Boolean,
    isPlaying: Boolean,
): Boolean {
    return playbackState == Player.STATE_READY && playWhenReady && isPlaying
}

internal fun shouldExecuteDelayedReconnect(
    playbackDesired: Boolean,
    queuedGeneration: Long,
    currentGeneration: Long,
    playbackState: Int,
    playWhenReady: Boolean,
    isPlaying: Boolean,
): Boolean {
    return playbackDesired &&
        queuedGeneration == currentGeneration &&
        !isHealthyPlayback(
            playbackState = playbackState,
            playWhenReady = playWhenReady,
            isPlaying = isPlaying,
        )
}

internal fun shouldSeekToReconnectTarget(
    currentMediaItemIndex: Int,
    targetIndex: Int,
    playbackState: Int,
): Boolean {
    return currentMediaItemIndex != targetIndex || playbackState == Player.STATE_ENDED
}
