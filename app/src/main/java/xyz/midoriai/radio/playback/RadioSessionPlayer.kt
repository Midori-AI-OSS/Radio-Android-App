package xyz.midoriai.radio.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

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

@UnstableApi
internal class RadioSessionPlayer(
    player: Player,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSelectAdjacentChannel: (Int) -> Unit,
    private val onSetMediaItems: (List<MediaItem>) -> Unit,
) : ForwardingPlayer(player) {
    private val sessionListeners = mutableSetOf<Player.Listener>()
    private var presentationSource: MediaItem? = null
    private var presentationItem: MediaItem? = null

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        sessionListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        sessionListeners.remove(listener)
    }

    override fun getCurrentMediaItem(): MediaItem? {
        val source = super.getCurrentMediaItem() ?: return null
        return if (source === presentationSource) presentationItem ?: source else source
    }

    override fun getMediaMetadata(): MediaMetadata {
        val source = super.getCurrentMediaItem()
        return if (source != null && source === presentationSource) {
            checkNotNull(presentationItem).mediaMetadata
        } else {
            super.getMediaMetadata()
        }
    }

    /**
     * Publish the currently playing track to MediaSession listeners without mutating ExoPlayer's
     * MediaItem. Replacing that item would reset a finite stream on some Media3 versions.
     */
    fun publishPresentationMetadata(metadata: MediaMetadata) {
        val source = super.getCurrentMediaItem() ?: return
        if (currentMediaItem?.mediaMetadata == metadata) return

        presentationSource = source
        presentationItem = source.buildUpon().setMediaMetadata(metadata).build()
        val changed = Player.Events(
            FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build(),
        )
        sessionListeners.toList().forEach { listener ->
            listener.onMediaMetadataChanged(metadata)
            listener.onEvents(this, changed)
        }
    }

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
