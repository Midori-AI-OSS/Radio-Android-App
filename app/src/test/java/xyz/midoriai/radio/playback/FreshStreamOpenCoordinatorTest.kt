package xyz.midoriai.radio.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshStreamOpenCoordinatorTest {
    @Test
    fun tryBeginOpen_admitsOnlyOneReplacementTransactionAtATime() {
        val coordinator = FreshStreamOpenCoordinator()

        assertEquals(1L, coordinator.tryBeginOpen())
        assertNull(coordinator.tryBeginOpen())

        coordinator.finishOpen()

        assertEquals(2L, coordinator.tryBeginOpen())
    }

    @Test
    fun canRunDelayedOpen_rejectsWorkInvalidatedByNewerSelection() {
        val coordinator = FreshStreamOpenCoordinator()
        val queuedGeneration = coordinator.currentGeneration()

        coordinator.invalidatePendingWork()

        assertFalse(
            coordinator.canRunDelayedOpen(
                queuedGeneration = queuedGeneration,
                playbackDesired = true,
                channelSwitchInProgress = false,
            ),
        )
    }

    @Test
    fun canRunDelayedOpen_rejectsRetryWhileChannelFadeIsInProgress() {
        val coordinator = FreshStreamOpenCoordinator()

        assertFalse(
            coordinator.canRunDelayedOpen(
                queuedGeneration = coordinator.currentGeneration(),
                playbackDesired = true,
                channelSwitchInProgress = true,
            ),
        )
    }

    @Test
    fun canRunDelayedOpen_rejectsWorkAfterPauseInvalidatesIt() {
        val coordinator = FreshStreamOpenCoordinator()
        val queuedGeneration = coordinator.currentGeneration()

        coordinator.invalidatePendingWork()

        assertFalse(
            coordinator.canRunDelayedOpen(
                queuedGeneration = queuedGeneration,
                playbackDesired = false,
                channelSwitchInProgress = false,
            ),
        )
    }

    @Test
    fun canRunDelayedOpen_acceptsCurrentRetryAfterAnOpenCompletes() {
        val coordinator = FreshStreamOpenCoordinator()
        val openGeneration = coordinator.tryBeginOpen()
        coordinator.finishOpen()

        assertTrue(
            coordinator.canRunDelayedOpen(
                queuedGeneration = openGeneration!!,
                playbackDesired = true,
                channelSwitchInProgress = false,
            ),
        )
    }

    @Test
    fun resolveFreshStreamSelection_usesLatestChannelAndPendingQualityAtAdmission() {
        assertEquals(
            FreshStreamSelection(channel = "focus", quality = "high"),
            resolveFreshStreamSelection(
                selectedChannel = " Focus ",
                activeQuality = "low",
                pendingQuality = "high",
            ),
        )
    }

    @Test
    fun resolveFreshStreamSelection_usesActiveQualityWithoutPendingChange() {
        assertEquals(
            FreshStreamSelection(channel = "all", quality = "medium"),
            resolveFreshStreamSelection(
                selectedChannel = " ",
                activeQuality = "medium",
                pendingQuality = null,
            ),
        )
    }
}
