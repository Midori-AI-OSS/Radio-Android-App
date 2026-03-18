package xyz.midoriai.radio.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioAutoCatalogTest {
    @Test
    fun toBrowseChannelMediaId_roundTripsChannel() {
        val mediaId = toBrowseChannelMediaId(" Chill ")

        assertEquals("midori:channel:chill", mediaId)
        assertEquals("chill", channelFromBrowseMediaId(mediaId))
    }

    @Test
    fun channelFromBrowseMediaId_rejectsUnknownPrefix() {
        assertNull(channelFromBrowseMediaId("focus"))
    }

    @Test
    fun resolveChannelForSearch_matchesDirectChannelName() {
        val result = resolveChannelForSearch(
            query = "Play Focus",
            availableChannels = listOf("all", "focus", "chill"),
        )

        assertEquals("focus", result)
    }

    @Test
    fun resolveChannelForSearch_matchesTokenizedChannelName() {
        val result = resolveChannelForSearch(
            query = "play lo fi beats",
            availableChannels = listOf("all", "lofi", "focus"),
        )

        assertEquals("lofi", result)
    }

    @Test
    fun resolveChannelForSearch_fallsBackToAllWhenNoGoodMatch() {
        val result = resolveChannelForSearch(
            query = "play something unexpected",
            availableChannels = listOf("all", "focus", "chill"),
        )

        assertEquals("all", result)
    }
}
