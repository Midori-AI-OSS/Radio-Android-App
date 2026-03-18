package xyz.midoriai.radio.playback

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import xyz.midoriai.radio.radioapi.ArtPayload
import xyz.midoriai.radio.radioapi.CurrentPayload

private const val SESSION_ARTWORK_TRACK_QUERY_PARAM = "midoriai_track_id"

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

internal fun buildSessionArtworkUrl(
    artUrl: String,
    trackId: String,
): String {
    val normalizedArtUrl = artUrl.trim()
    if (normalizedArtUrl.isBlank()) {
        return normalizedArtUrl
    }

    val normalizedTrackId = trackId.trim()
    if (normalizedTrackId.isBlank() || normalizedTrackId == "unknown") {
        return normalizedArtUrl
    }

    normalizedArtUrl.toHttpUrlOrNull()?.let { parsed ->
        return parsed.newBuilder()
            .setQueryParameter(SESSION_ARTWORK_TRACK_QUERY_PARAM, normalizedTrackId)
            .build()
            .toString()
    }

    val separator = if ('?' in normalizedArtUrl) '&' else '?'
    val encodedTrackId = URLEncoder.encode(normalizedTrackId, StandardCharsets.UTF_8.toString())
    return "$normalizedArtUrl$separator$SESSION_ARTWORK_TRACK_QUERY_PARAM=$encodedTrackId"
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
