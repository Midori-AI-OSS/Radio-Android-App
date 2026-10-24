package xyz.midoriai.radio.playback

import xyz.midoriai.radio.radioapi.normalizeQuality

/** Coordinates short-lived player source replacement transactions. */
internal class FreshStreamOpenCoordinator {
    private var generation = 0L
    private var opening = false

    fun invalidatePendingWork(): Long {
        generation += 1
        return generation
    }

    fun currentGeneration(): Long = generation

    fun tryBeginOpen(): Long? {
        if (opening) {
            return null
        }

        opening = true
        return invalidatePendingWork()
    }

    fun finishOpen() {
        opening = false
    }

    fun canRunDelayedOpen(
        queuedGeneration: Long,
        playbackDesired: Boolean,
        channelSwitchInProgress: Boolean,
    ): Boolean {
        return playbackDesired &&
            !opening &&
            !channelSwitchInProgress &&
            queuedGeneration == generation
    }
}

internal data class FreshStreamSelection(
    val channel: String,
    val quality: String,
)

internal fun resolveFreshStreamSelection(
    selectedChannel: String,
    activeQuality: String,
    pendingQuality: String?,
): FreshStreamSelection {
    return FreshStreamSelection(
        channel = normalizePersistedChannel(selectedChannel),
        quality = pendingQuality
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeQuality)
            ?: normalizeQuality(activeQuality),
    )
}
