package xyz.midoriai.radio.playback

/**
 * Checks decoder progress for the installed finite HTTP response. A player may report READY and
 * isPlaying after its audio has ended if the server leaves the response open without delivering EOF.
 *
 * The service samples at a modest interval only while playback is desired. An unchanged position
 * must persist across three samples before the canonical opener is used.
 */
internal class RadioPlaybackProgressGuard(
    private val scheduler: RecoveryRetryScheduler,
    private val generation: () -> Long,
    private val playbackDesired: () -> Boolean,
    private val sourceIsCurrent: () -> Boolean,
    private val positionMs: () -> Long,
    private val open: () -> Unit,
) {
    private val intervalMs = 5_000L
    private val stalledIntervalsBeforeRecovery = 3
    private var handle: RecoveryRetryHandle? = null
    private var token = 0L
    private var observedPosition: Long? = null
    private var stagnantIntervals = 0

    fun sourceOpened() {
        cancel()
        if (!playbackDesired()) return
        observedPosition = null
        arm(generation())
    }

    fun cancel() {
        token += 1
        handle?.cancel()
        handle = null
        observedPosition = null
        stagnantIntervals = 0
    }

    private fun arm(expectedGeneration: Long) {
        val expectedToken = ++token
        handle = scheduler.schedule(intervalMs) check@{
            if (expectedToken != token) return@check
            handle = null
            if (!playbackDesired() || generation() != expectedGeneration || !sourceIsCurrent()) {
                cancel()
                return@check
            }
            val current = positionMs()
            val previous = observedPosition
            if (current < 0 || previous == null || current > previous) {
                stagnantIntervals = 0
            } else {
                stagnantIntervals += 1
            }
            observedPosition = current
            if (stagnantIntervals >= stalledIntervalsBeforeRecovery) {
                cancel()
                open()
            } else {
                arm(expectedGeneration)
            }
        }
    }
}
