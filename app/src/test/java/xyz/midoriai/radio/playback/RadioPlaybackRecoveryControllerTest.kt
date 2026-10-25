package xyz.midoriai.radio.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import xyz.midoriai.radio.ui.screens.nowplaying.RadioPlaybackState

class RadioPlaybackRecoveryControllerTest {
    @Test
    fun productionEofRouting_opensImmediatelyThenRetriesFailedAcquisitionsWithoutLimit() {
        val scheduler = RecordingRetryScheduler()
        val coordinator = FreshStreamOpenCoordinator()
        var opens = 0
        val states = mutableListOf<RadioPlaybackState>()
        val controller = controller(
            coordinator = coordinator,
            scheduler = scheduler,
            playbackState = { Player.STATE_ENDED },
            openFreshStream = {
                opens += 1
                FreshStreamOpenResult.Failed
            },
            updatePlaybackState = states::add,
        )
        controller.sourceOpened()
        val listener = RadioPlaybackPlayerEventListener(
            playbackDesired = { true },
            currentPlaybackState = { Player.STATE_ENDED },
            onPlayerFailure = {},
            onPlaybackState = controller::onPlaybackStateChanged,
            onStopped = {},
            onMediaItem = {},
        )

        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        repeat(8) { scheduler.runNext() }

        assertEquals(9, opens)
        assertEquals(
            listOf(50L, 100L, 200L, 400L, 800L, 1500L, 1500L, 1500L, 1500L),
            scheduler.scheduledDelays,
        )
        assertEquals(
            (1..9).toList(),
            states.filterIsInstance<RadioPlaybackState.Reconnecting>().map { it.attempt },
        )
    }

    @Test
    fun explicitOpenFailure_usesRetryJobThatSelectionAndPauseCancelAndReset() {
        val scheduler = RecordingRetryScheduler()
        val coordinator = FreshStreamOpenCoordinator()
        var desired = true
        var retryOpens = 0
        val controller = controller(
            coordinator = coordinator,
            scheduler = scheduler,
            playbackDesired = { desired },
        )

        controller.retryFailedOpen(FreshStreamOpenResult.Failed) {
            retryOpens += 1
            FreshStreamOpenResult.Opened
        }
        val selectionRetry = scheduler.latest()
        coordinator.invalidatePendingWork()
        controller.cancelPendingRecovery()
        selectionRetry.runEvenIfCancelled()

        assertEquals(0, retryOpens)
        assertFalse(selectionRetry.isActive)

        controller.retryFailedOpen(FreshStreamOpenResult.Failed) {
            retryOpens += 1
            FreshStreamOpenResult.Opened
        }
        val pauseRetry = scheduler.latest()
        desired = false
        coordinator.invalidatePendingWork()
        controller.cancelPendingRecovery()
        pauseRetry.runEvenIfCancelled()

        assertEquals(0, retryOpens)
        assertFalse(pauseRetry.isActive)

        desired = true
        controller.retryFailedOpen(FreshStreamOpenResult.Failed)
        assertEquals(50L, scheduler.latest().delayMs)
    }

    @Test
    fun explicitSelectionFailure_retriesEvenWhenInstallerRestoredHealthyOldSource() {
        val scheduler = RecordingRetryScheduler()
        var retryOpens = 0
        val controller = controller(
            coordinator = FreshStreamOpenCoordinator(),
            scheduler = scheduler,
            playbackState = { Player.STATE_READY },
            playWhenReady = { true },
            isPlaying = { true },
        )

        controller.retryFailedOpen(FreshStreamOpenResult.Failed) {
            retryOpens += 1
            FreshStreamOpenResult.Opened
        }
        scheduler.runNext()

        assertEquals(1, retryOpens)
    }

    @Test
    fun staleExplicitSelectionRetry_doesNotOpenSupersededTarget() {
        val scheduler = RecordingRetryScheduler()
        var targetIsCurrent = true
        var retryOpens = 0
        val controller = controller(
            coordinator = FreshStreamOpenCoordinator(),
            scheduler = scheduler,
        )

        controller.retryFailedOpen(
            result = FreshStreamOpenResult.Failed,
            retryOpen = {
                retryOpens += 1
                FreshStreamOpenResult.Opened
            },
            isStillCurrent = { targetIsCurrent },
        )
        targetIsCurrent = false
        scheduler.runNext()

        assertEquals(0, retryOpens)
    }

    @Test
    fun failedChannelSwitch_replacesGenericErrorRetryWithAuthoritativeTarget() {
        val scheduler = RecordingRetryScheduler()
        var genericOpens = 0
        var targetOpens = 0
        val controller = controller(
            coordinator = FreshStreamOpenCoordinator(),
            scheduler = scheduler,
            openFreshStream = {
                genericOpens += 1
                FreshStreamOpenResult.Opened
            },
        )

        controller.onPlayerError()
        val genericRetry = scheduler.latest()
        controller.replacePendingRetryWithFailedOpen(
            result = FreshStreamOpenResult.Failed,
            isStillCurrent = { true },
            retryOpen = {
                targetOpens += 1
                FreshStreamOpenResult.Opened
            },
        )
        genericRetry.runEvenIfCancelled()
        scheduler.latest().run()

        assertEquals(0, genericOpens)
        assertEquals(1, targetOpens)
        assertFalse(genericRetry.isActive)
    }

    @Test
    fun rejectedExplicitOpen_doesNotScheduleRecovery() {
        val scheduler = RecordingRetryScheduler()
        val controller = controller(
            coordinator = FreshStreamOpenCoordinator(),
            scheduler = scheduler,
        )

        controller.retryFailedOpen(FreshStreamOpenResult.Rejected)

        assertEquals(emptyList<Long>(), scheduler.scheduledDelays)
    }

    private fun controller(
        coordinator: FreshStreamOpenCoordinator,
        scheduler: RecordingRetryScheduler,
        playbackDesired: () -> Boolean = { true },
        playbackState: () -> Int = { Player.STATE_IDLE },
        playWhenReady: () -> Boolean = { false },
        isPlaying: () -> Boolean = { false },
        openFreshStream: () -> FreshStreamOpenResult = { FreshStreamOpenResult.Opened },
        updatePlaybackState: (RadioPlaybackState) -> Unit = {},
    ): RadioPlaybackRecoveryController {
        return RadioPlaybackRecoveryController(
            coordinator = coordinator,
            scheduler = scheduler,
            playbackDesired = playbackDesired,
            playbackState = playbackState,
            playWhenReady = playWhenReady,
            isPlaying = isPlaying,
            sourceIsCurrent = { true },
            channelSwitchInProgress = { false },
            openFreshStream = openFreshStream,
            updatePlaybackState = updatePlaybackState,
        )
    }

    private class RecordingRetryScheduler : RecoveryRetryScheduler {
        private val tasks = mutableListOf<ScheduledRetry>()
        val scheduledDelays: List<Long>
            get() = tasks.map { it.delayMs }

        override fun schedule(delayMs: Long, action: () -> Unit): RecoveryRetryHandle {
            return ScheduledRetry(delayMs, action).also(tasks::add)
        }

        fun runNext() {
            tasks.first { it.isActive }.run()
        }

        fun latest(): ScheduledRetry = tasks.last()
    }

    private class ScheduledRetry(
        val delayMs: Long,
        private val action: () -> Unit,
    ) : RecoveryRetryHandle {
        var isActive = true
            private set

        override fun cancel() {
            isActive = false
        }

        fun run() {
            check(isActive)
            isActive = false
            action()
        }

        fun runEvenIfCancelled() {
            action()
        }
    }
}
