package xyz.midoriai.radio.playback

import java.util.Locale
import xyz.midoriai.radio.radioapi.ArtPayload
import xyz.midoriai.radio.radioapi.CurrentPayload

internal data class RadioPresentationState(
    val normalizedSelectedChannel: String,
    val channelLabel: String,
    val channelSubtitle: String,
    val trackTitle: String,
    val artUrl: String?,
    val trackId: String,
)

internal fun resolveRadioPresentationState(
    selectedChannel: String,
    currentTrack: CurrentPayload?,
    art: ArtPayload?,
): RadioPresentationState {
    val normalizedSelectedChannel = normalizePersistedChannel(selectedChannel)
    val artForSelectedChannel = art?.takeIf {
        it.hasArt && normalizePersistedChannel(it.channel) == normalizedSelectedChannel
    }
    val currentTrackForSelectedChannel = currentTrack?.takeIf {
        normalizePersistedChannel(it.channel) == normalizedSelectedChannel
    }

    val isArtForCurrentTrack = if (currentTrackForSelectedChannel != null && artForSelectedChannel != null) {
        artForSelectedChannel.trackId == currentTrackForSelectedChannel.trackId
    } else {
        true
    }

    val artUrl = if (isArtForCurrentTrack) {
        artForSelectedChannel?.artUrl
    } else {
        null
    }

    return RadioPresentationState(
        normalizedSelectedChannel = normalizedSelectedChannel,
        channelLabel = toChannelLabel(normalizedSelectedChannel),
        channelSubtitle = toChannelSubtitle(normalizedSelectedChannel),
        trackTitle = currentTrackForSelectedChannel?.title ?: "Fetching current track...",
        artUrl = artUrl,
        trackId = currentTrackForSelectedChannel?.trackId ?: artForSelectedChannel?.trackId ?: "unknown",
    )
}

internal fun normalizePersistedChannel(value: String): String {
    val raw = value.trim().lowercase()
    return if (raw.isBlank()) {
        "all"
    } else {
        raw
    }
}

internal fun toChannelLabel(channel: String): String {
    return if (normalizePersistedChannel(channel) == "all") {
        "All"
    } else {
        normalizePersistedChannel(channel)
    }
}

internal fun toChannelDisplayName(channel: String): String {
    val normalized = normalizePersistedChannel(channel)
    return if (normalized == "all") {
        "All"
    } else {
        normalized.replaceFirstChar { first ->
            if (first.isLowerCase()) {
                first.titlecase(Locale.US)
            } else {
                first.toString()
            }
        }
    }
}

internal fun toChannelSubtitle(channel: String): String {
    return "Midori AI Radio: ${toChannelDisplayName(channel)}"
}
