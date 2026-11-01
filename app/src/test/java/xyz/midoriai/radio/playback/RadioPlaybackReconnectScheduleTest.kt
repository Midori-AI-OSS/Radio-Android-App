package xyz.midoriai.radio.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioPlaybackReconnectScheduleTest {
    @Test
    fun reconnectDelaySchedule_matchesApprovedAggressiveValues() {
        assertEquals(
            listOf(50L, 100L, 200L, 400L, 800L, 1500L),
            errorReconnectDelaysMs,
        )
    }

    @Test
    fun reconnectDelaySchedule_appliesExpectedDelayPerAttemptSequence() {
        assertEquals(
            listOf(50L, 100L, 200L, 400L, 800L, 1500L, 1500L, 1500L),
            (1..8).map(::errorReconnectDelayMs),
        )
    }
}
