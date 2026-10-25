package xyz.midoriai.radio.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal val RADIO_SESSION_PLAYER_COMMANDS: Player.Commands = Player.Commands.Builder()
    .add(Player.COMMAND_PLAY_PAUSE)
    .add(Player.COMMAND_PREPARE)
    .add(Player.COMMAND_STOP)
    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    .add(Player.COMMAND_SEEK_TO_NEXT)
    .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
    .add(Player.COMMAND_GET_TIMELINE)
    .add(Player.COMMAND_GET_METADATA)
    .add(Player.COMMAND_SET_MEDIA_ITEM)
    .build()

internal class RadioSessionPlayer(
    player: Player,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSelectAdjacentChannel: (Int) -> Unit,
    private val onSetMediaItems: (List<MediaItem>) -> Unit,
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

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        openCanonicalSource(mediaItems)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        openCanonicalSource(mediaItems)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        openCanonicalSource(mediaItems)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        openCanonicalSource(listOf(mediaItem))
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        openCanonicalSource(listOf(mediaItem))
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        openCanonicalSource(listOf(mediaItem))
    }

    private fun openCanonicalSource(mediaItems: List<MediaItem>) {
        onSetMediaItems(mediaItems)
    }

    override fun getDuration(): Long = C.TIME_UNSET

    override fun getContentDuration(): Long = C.TIME_UNSET

    override fun isCurrentMediaItemLive(): Boolean = currentMediaItem != null

    override fun isCurrentMediaItemSeekable(): Boolean = false
}
