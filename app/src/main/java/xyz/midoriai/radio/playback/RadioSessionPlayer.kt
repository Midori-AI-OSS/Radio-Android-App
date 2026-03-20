package xyz.midoriai.radio.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

internal class RadioSessionPlayer(
    player: Player,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSelectAdjacentChannel: (Int) -> Unit,
) : ForwardingPlayer(player) {
    override fun play() {
        onPlay()
    }

    override fun pause() {
        onPause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            onPlay()
        } else {
            onPause()
        }
    }

    override fun stop() {
        onPause()
    }

    override fun seekToNextMediaItem() {
        onSelectAdjacentChannel(1)
    }

    override fun seekToPreviousMediaItem() {
        onSelectAdjacentChannel(-1)
    }

    override fun seekToNext() {
        onSelectAdjacentChannel(1)
    }

    override fun seekToPrevious() {
        onSelectAdjacentChannel(-1)
    }

    override fun getDuration(): Long = C.TIME_UNSET

    override fun getContentDuration(): Long = C.TIME_UNSET

    override fun isCurrentMediaItemLive(): Boolean = currentMediaItem != null

    override fun isCurrentMediaItemSeekable(): Boolean = false
}
