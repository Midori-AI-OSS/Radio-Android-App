package xyz.midoriai.radio.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl
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
        assertEquals("All", result.channelSubtitle)
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
        assertEquals("Chill", result.channelSubtitle)
        assertEquals("Fetching current track...", result.trackTitle)
        assertEquals("track-art-only", result.trackId)
        assertEquals("https://radio.midori-ai.xyz/art/track-art-only.jpg", result.artUrl)
    }

    @Test
    fun buildSessionArtworkUrl_changesWhenTrackChangesAtStableArtEndpoint() {
        val baseUrl = "https://radio.midori-ai.xyz/radio/v1/art/image?channel=all"

        val first = buildSessionArtworkUrl(
            artUrl = baseUrl,
            trackId = "track-123",
        )
        val second = buildSessionArtworkUrl(
            artUrl = baseUrl,
            trackId = "track-456",
        )

        val parsedFirst = first.toHttpUrl()
        assertEquals("all", parsedFirst.queryParameter("channel"))
        assertEquals("track-123", parsedFirst.queryParameter("midoriai_track_id"))
        assertNotEquals(first, second)
    }

    @Test
    fun buildSessionArtworkUrl_keepsOriginalUrlWhenTrackIdIsUnknown() {
        val baseUrl = "https://radio.midori-ai.xyz/radio/v1/art/image?channel=focus"

        val result = buildSessionArtworkUrl(
            artUrl = baseUrl,
            trackId = "unknown",
        )

        assertEquals(baseUrl, result)
    }

    @Test
    fun toChannelSubtitle_showsOnlyTheChannelName() {
        assertEquals("All", toChannelSubtitle("all"))
        assertEquals("All", toChannelSubtitle(" ALL "))
        assertEquals("Chill", toChannelSubtitle("chill"))
        assertEquals("Lo-fi focus", toChannelSubtitle("lo-fi focus"))
    }

    @Test
    fun toChannelSubtitle_neverContainsBrandPrefix() {
        listOf("all", "chill", "focus", "lo-fi", "late night").forEach { channel ->
            assertFalse(
                "toChannelSubtitle(\"$channel\") must omit the Midori AI Radio: prefix",
                toChannelSubtitle(channel).contains("Midori AI Radio"),
            )
        }
    }

    @Test
    fun hasArtworkChanged_detectsArtArrival() {
        assertTrue(
            hasArtworkChanged(
                previous = null,
                updated = artPayload(artUrl = "https://radio.midori-ai.xyz/art/track-123.jpg"),
            ),
        )
    }

    @Test
    fun hasArtworkChanged_ignoresUnchangedArt() {
        val payload = artPayload(artUrl = "https://radio.midori-ai.xyz/art/track-123.jpg")

        assertFalse(hasArtworkChanged(previous = payload, updated = payload))
        assertFalse(
            hasArtworkChanged(
                previous = payload,
                updated = artPayload(artUrl = "https://radio.midori-ai.xyz/art/track-123.jpg"),
            ),
        )
    }

    @Test
    fun hasArtworkChanged_detectsNewArtUrl() {
        assertTrue(
            hasArtworkChanged(
                previous = artPayload(artUrl = "https://radio.midori-ai.xyz/art/track-old.jpg"),
                updated = artPayload(artUrl = "https://radio.midori-ai.xyz/art/track-new.jpg"),
            ),
        )
    }

    @Test
    fun hasArtworkChanged_detectsArtLoss() {
        val previous = artPayload(artUrl = "https://radio.midori-ai.xyz/art/track-123.jpg")
        val lost = ArtPayload(
            channel = "all",
            trackId = "track-123",
            hasArt = false,
            artUrl = "",
        )

        assertTrue(hasArtworkChanged(previous = previous, updated = lost))
    }

    @Test
    fun hasArtworkChanged_ignoresBlankToBlank() {
        val blank = ArtPayload(
            channel = "all",
            trackId = "track-123",
            hasArt = false,
            artUrl = "",
        )

        assertFalse(hasArtworkChanged(previous = null, updated = blank))
        assertFalse(hasArtworkChanged(previous = blank, updated = blank))
    }

    private fun artPayload(artUrl: String): ArtPayload {
        return ArtPayload(
            channel = "all",
            trackId = "track-123",
            hasArt = true,
            artUrl = artUrl,
        )
    }
}
