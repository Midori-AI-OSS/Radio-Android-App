package xyz.midoriai.radio.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.midoriai.radio.radioapi.ArtPayload
import xyz.midoriai.radio.radioapi.CurrentPayload
import xyz.midoriai.radio.radioapi.QualityLevel

class RadioPresentationResolverTest {
    @Test
    fun resolveRadioPresentationState_prefersMatchingTrackAndArt() {
        val current = CurrentPayload(
            stationLabel = "Midori AI Radio",
            channel = "all",
            trackId = "track-123",
            title = "Dream Circuit",
            durationMs = 1_000L,
            positionMs = 500L,
            startedAt = "2026-03-18T00:00:00Z",
            warmupActive = false,
            qualityLevels = listOf(QualityLevel(name = "high", bitrateKbps = 320)),
        )
        val art = ArtPayload(
            channel = "all",
            trackId = "track-123",
            hasArt = true,
            artUrl = "https://radio.midori-ai.xyz/art/track-123.jpg",
        )

        val result = resolveRadioPresentationState(
            selectedChannel = "all",
            currentTrack = current,
            art = art,
        )

        assertEquals("all", result.normalizedSelectedChannel)
        assertEquals("All", result.channelLabel)
        assertEquals("Midori AI Radio: All", result.channelSubtitle)
        assertEquals("Dream Circuit", result.trackTitle)
        assertEquals("track-123", result.trackId)
        assertEquals("https://radio.midori-ai.xyz/art/track-123.jpg", result.artUrl)
    }

    @Test
    fun resolveRadioPresentationState_dropsArtWhenTrackDoesNotMatch() {
        val current = CurrentPayload(
            stationLabel = "Midori AI Radio",
            channel = "focus",
            trackId = "track-current",
            title = "Current Song",
            durationMs = 1_000L,
            positionMs = 500L,
            startedAt = "2026-03-18T00:00:00Z",
            warmupActive = false,
            qualityLevels = listOf(QualityLevel(name = "medium", bitrateKbps = 192)),
        )
        val art = ArtPayload(
            channel = "focus",
            trackId = "track-old",
            hasArt = true,
            artUrl = "https://radio.midori-ai.xyz/art/track-old.jpg",
        )

        val result = resolveRadioPresentationState(
            selectedChannel = "focus",
            currentTrack = current,
            art = art,
        )

        assertEquals("Current Song", result.trackTitle)
        assertEquals("track-current", result.trackId)
        assertNull(result.artUrl)
    }

    @Test
    fun resolveRadioPresentationState_usesFallbackTrackIdWhenCurrentIsMissing() {
        val art = ArtPayload(
            channel = " chill ",
            trackId = "track-art-only",
            hasArt = true,
            artUrl = "https://radio.midori-ai.xyz/art/track-art-only.jpg",
        )

        val result = resolveRadioPresentationState(
            selectedChannel = " Chill ",
            currentTrack = null,
            art = art,
        )

        assertEquals("chill", result.normalizedSelectedChannel)
        assertEquals("chill", result.channelLabel)
        assertEquals("Midori AI Radio: Chill", result.channelSubtitle)
        assertEquals("Fetching current track...", result.trackTitle)
        assertEquals("track-art-only", result.trackId)
        assertEquals("https://radio.midori-ai.xyz/art/track-art-only.jpg", result.artUrl)
    }
}
