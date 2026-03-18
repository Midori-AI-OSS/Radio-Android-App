package xyz.midoriai.radio.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioPlaybackActivePlaylistPolicyTest {
    @Test
    fun resolveActivePlayerPlaylistPolicy_keepsOnlySelectedChannelWhenUiListHasNeighbors() {
        val policy = resolveActivePlayerPlaylistPolicy(
            selectedChannel = "focus",
            availableChannels = listOf("all", "focus", "chill"),
        )

        assertEquals(listOf("focus"), policy.channels)
        assertEquals(0, policy.startIndex)
    }

    @Test
    fun resolveActivePlayerPlaylistPolicy_normalizesSelectedChannel() {
        val policy = resolveActivePlayerPlaylistPolicy(
            selectedChannel = " Focus ",
            availableChannels = listOf("all", " focus ", "chill"),
        )

        assertEquals(listOf("focus"), policy.channels)
        assertEquals(0, policy.startIndex)
    }

    @Test
    fun resolveActivePlayerPlaylistPolicy_keepsSelectedChannelWhenUiListIsStale() {
        val policy = resolveActivePlayerPlaylistPolicy(
            selectedChannel = "focus",
            availableChannels = listOf("all", "chill"),
        )

        assertEquals(listOf("focus"), policy.channels)
        assertEquals(0, policy.startIndex)
    }

    @Test
    fun resolveActivePlayerPlaylistPolicy_fallsBackToAllForBlankSelection() {
        val policy = resolveActivePlayerPlaylistPolicy(
            selectedChannel = "   ",
            availableChannels = listOf("focus", "chill"),
        )

        assertEquals(listOf("all"), policy.channels)
        assertEquals(0, policy.startIndex)
    }
}
