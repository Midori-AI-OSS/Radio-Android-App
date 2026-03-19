package xyz.midoriai.radio.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RadioPlaybackAdjacentChannelPolicyTest {
    @Test
    fun resolveAdjacentChannelSelection_wrapsAcrossAvailableChannels() {
        assertEquals(
            "all",
            resolveAdjacentChannelSelection(
                selectedChannel = "chill",
                availableChannels = listOf("all", "focus", "chill"),
                direction = 1,
            ),
        )
        assertEquals(
            "chill",
            resolveAdjacentChannelSelection(
                selectedChannel = "all",
                availableChannels = listOf("all", "focus", "chill"),
                direction = -1,
            ),
        )
    }

    @Test
    fun resolveAdjacentChannelSelection_normalizesInputsAndFallsBackWhenSelectedIsMissing() {
        assertEquals(
            "chill",
            resolveAdjacentChannelSelection(
                selectedChannel = " Focus ",
                availableChannels = listOf(" all ", " focus ", "chill"),
                direction = 1,
            ),
        )
        assertEquals(
            "focus",
            resolveAdjacentChannelSelection(
                selectedChannel = "missing",
                availableChannels = listOf("all", "focus", "chill"),
                direction = 1,
            ),
        )
    }

    @Test
    fun resolveAdjacentChannelSelection_returnsNullForZeroDirectionOrEmptyPlaylist() {
        assertNull(
            resolveAdjacentChannelSelection(
                selectedChannel = "focus",
                availableChannels = listOf("all", "focus", "chill"),
                direction = 0,
            ),
        )
        assertNull(
            resolveAdjacentChannelSelection(
                selectedChannel = "focus",
                availableChannels = emptyList(),
                direction = 1,
            ),
        )
    }

    @Test
    fun createAdjacentChannelSessionCommandRequest_skipsZeroDirection() {
        assertNull(createAdjacentChannelSessionCommandRequest(0))
        assertEquals(1, createAdjacentChannelSessionCommandRequest(1)?.direction)
        assertEquals(-1, createAdjacentChannelSessionCommandRequest(-1)?.direction)
    }

    @Test
    fun parseAdjacentChannelSessionCommandRequest_matchesOnlyAdjacentChannelAction() {
        val request = parseAdjacentChannelSessionCommandRequest(
            customAction = CUSTOM_COMMAND_SELECT_ADJACENT_CHANNEL,
            direction = -1,
        )

        assertNotNull(request)
        assertEquals(-1, request?.direction)
        assertEquals("direction", ARG_DIRECTION)
        assertNull(
            parseAdjacentChannelSessionCommandRequest(
                customAction = CUSTOM_COMMAND_SET_QUALITY,
                direction = 1,
            ),
        )
    }
}
