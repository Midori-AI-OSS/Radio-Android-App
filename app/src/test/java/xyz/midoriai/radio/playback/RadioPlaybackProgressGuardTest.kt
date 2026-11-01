package xyz.midoriai.radio.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioPlaybackProgressGuardTest {
    @Test
    fun stalledSourceOpensOnceAfterThreeUnchangedProgressChecks() {
        val scheduler = Scheduler()
        var generation = 1L
        var position = 100L
        var desired = true
        var opens = 0
        val guard = RadioPlaybackProgressGuard(
            scheduler = scheduler,
            generation = { generation },
            playbackDesired = { desired },
            sourceIsCurrent = { true },
            positionMs = { position },
            open = { opens++ },
        )
        guard.sourceOpened()
        scheduler.runNext()
        position = 250L
        scheduler.runNext()
        assertEquals(0, opens)
        repeat(3) { scheduler.runNext() }
        assertEquals(1, opens)
        assertEquals(listOf(5_000L, 5_000L, 5_000L, 5_000L, 5_000L), scheduler.delays)
    }

    @Test
    fun pauseAndSourceSupersessionInvalidateQueuedChecks() {
        val scheduler = Scheduler()
        var generation = 1L
        var desired = true
        var opens = 0
        val guard = RadioPlaybackProgressGuard(
            scheduler = scheduler,
            generation = { generation },
            playbackDesired = { desired },
            sourceIsCurrent = { true },
            positionMs = { 100L },
            open = { opens++ },
        )
        guard.sourceOpened()
        val stale = scheduler.latest()
        generation++
        stale.fireEvenIfCancelled()
        assertEquals(0, opens)
        desired = false
        guard.sourceOpened()
        assertEquals(1, scheduler.delays.size)
        desired = true
        guard.sourceOpened()
        guard.cancel()
        scheduler.latest().fireEvenIfCancelled()
        assertEquals(0, opens)
    }

    @Test
    fun continuedPositionProgressNeverReopens() {
        val scheduler = Scheduler()
        var position = 0L
        var opens = 0
        val guard = RadioPlaybackProgressGuard(
            scheduler = scheduler,
            generation = { 1L },
            playbackDesired = { true },
            sourceIsCurrent = { true },
            positionMs = { position },
            open = { opens++ },
        )
        guard.sourceOpened()
        repeat(12) {
            position += 5_000L
            scheduler.runNext()
        }
        assertEquals(0, opens)
    }

    private class Scheduler : RecoveryRetryScheduler {
        val delays = mutableListOf<Long>()
        val actions = mutableListOf<Action>()
        override fun schedule(delayMs: Long, action: () -> Unit): RecoveryRetryHandle {
            delays += delayMs
            return Action(action).also(actions::add)
        }
        fun latest() = actions.last()
        fun runNext() = actions.first { it.active }.fire()
    }

    private class Action(private val callback: () -> Unit) : RecoveryRetryHandle {
        var active = true
        override fun cancel() { active = false }
        fun fire() {
            check(active)
            active = false
            callback()
        }
        fun fireEvenIfCancelled() = callback()
    }
}
