package xyz.midoriai.radio.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** Joins MediaSession selection resolution to canonical service-side source installation. */
internal class MediaSessionStreamOpening(
    private val resolveRequestedChannel: (List<MediaItem>) -> String,
    private val effectiveQualityForNextOpen: () -> String,
    private val buildPlaybackItems: (channel: String, quality: String) -> List<MediaItem>,
    private val openSelectedStream: (String) -> FreshStreamOpenResult,
) {
    fun wrapPlayer(
        player: Player,
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onSelectAdjacentChannel: (Int) -> Unit,
    ): Player {
        return RadioSessionPlayer(
            player = player,
            onPlay = onPlay,
            onPause = onPause,
            onSelectAdjacentChannel = onSelectAdjacentChannel,
            onSetMediaItems = ::installResolvedSelection,
        )
    }

    fun resolvePlaybackItems(requestedItems: List<MediaItem>): List<MediaItem> {
        val channel = normalizePersistedChannel(resolveRequestedChannel(requestedItems))
        val quality = effectiveQualityForNextOpen()
        return buildPlaybackItems(channel, quality)
    }

    fun installResolvedSelection(installedItems: List<MediaItem>) {
        val channel = normalizePersistedChannel(resolveRequestedChannel(installedItems))
        check(openSelectedStream(channel) == FreshStreamOpenResult.Opened) {
            "Session stream installation was rejected"
        }
    }
}
