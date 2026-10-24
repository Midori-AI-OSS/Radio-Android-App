package xyz.midoriai.radio.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal class RadioSessionPlayer(
    player: Player,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSelectAdjacentChannel: (Int) -> Unit,
    private val onSetMediaItems: () -> Unit,
) : ForwardingPlayer(player) {
    private var canonicalPreparePending = false

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

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        openCanonicalSource()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        openCanonicalSource()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        openCanonicalSource()
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        openCanonicalSource()
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        openCanonicalSource()
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        openCanonicalSource()
    }

    override fun prepare() {
        if (canonicalPreparePending) {
            canonicalPreparePending = false
            return
        }

        super.prepare()
    }

    private fun openCanonicalSource() {
        canonicalPreparePending = true
        try {
            onSetMediaItems()
        } catch (failure: Throwable) {
            canonicalPreparePending = false
            throw failure
        }
    }

    override fun getDuration(): Long = C.TIME_UNSET

    override fun getContentDuration(): Long = C.TIME_UNSET

    override fun isCurrentMediaItemLive(): Boolean = currentMediaItem != null

    override fun isCurrentMediaItemSeekable(): Boolean = false
}
