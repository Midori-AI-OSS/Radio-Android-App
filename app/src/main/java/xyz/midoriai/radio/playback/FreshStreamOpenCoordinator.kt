package xyz.midoriai.radio.playback

import xyz.midoriai.radio.radioapi.normalizeQuality

/** Coordinates short-lived player source replacement transactions. */
internal class FreshStreamOpenCoordinator {
    private var generation = 0L
    private var channelSwitchGeneration = 0L
    private var opening = false

    fun invalidatePendingWork(): Long {
        generation += 1
        return generation
    }

    fun supersedeForSelection(onSupersededSwitch: () -> Unit): Long {
        val invalidatedGeneration = invalidatePendingWork()
        invalidateChannelSwitch()
        onSupersededSwitch()
        return invalidatedGeneration
    }

    fun currentGeneration(): Long = generation

    fun beginChannelSwitch(): Long {
        invalidatePendingWork()
        channelSwitchGeneration += 1
        return channelSwitchGeneration
    }

    fun invalidateChannelSwitch() {
        channelSwitchGeneration += 1
    }

    fun isCurrentChannelSwitch(queuedGeneration: Long): Boolean {
        return queuedGeneration == channelSwitchGeneration
    }

    fun finishChannelSwitch(
        queuedGeneration: Long,
        openResult: FreshStreamOpenResult?,
        playbackDesired: Boolean,
        restoreVolume: () -> Unit,
        onRejectedOpen: () -> Unit,
    ) {
        if (isCurrentChannelSwitch(queuedGeneration)) {
            restoreVolume()
            if (openResult == FreshStreamOpenResult.Rejected && playbackDesired) {
                onRejectedOpen()
            }
        }
    }

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

    fun runOpenTransaction(
        onFinished: () -> Unit = {},
        transaction: (openGeneration: Long) -> Unit,
    ): FreshStreamOpenResult {
        val openGeneration = tryBeginOpen() ?: return FreshStreamOpenResult.Rejected

        try {
            transaction(openGeneration)
        } finally {
            try {
                onFinished()
            } finally {
                finishOpen()
            }
        }

        return FreshStreamOpenResult.Opened
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

internal enum class FreshStreamOpenResult {
    Opened,
    Rejected,
}

internal fun shouldContinueChannelSwitch(
    openResult: FreshStreamOpenResult,
    isCurrentSwitch: Boolean,
    playbackDesired: Boolean,
): Boolean {
    return openResult == FreshStreamOpenResult.Opened && isCurrentSwitch && playbackDesired
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
