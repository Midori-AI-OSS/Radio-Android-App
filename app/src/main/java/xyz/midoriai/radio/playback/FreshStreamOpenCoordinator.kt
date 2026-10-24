package xyz.midoriai.radio.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
            if (
                openResult != null &&
                openResult != FreshStreamOpenResult.Opened &&
                playbackDesired
            ) {
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
    Failed,
}

/** Restores the previous player source if an admitted installation does not complete. */
internal class FreshStreamPlayerInstaller(
    private val player: Player,
) {
    fun install(
        replaceSource: () -> Unit,
        playWhenReady: Boolean,
        onFailed: () -> Unit,
    ) {
        val snapshot = Snapshot(
            mediaItem = player.currentMediaItem,
            playWhenReady = player.playWhenReady,
            wasPrepared = player.playbackState != Player.STATE_IDLE,
        )

        try {
            replaceSource()
            player.prepare()
            player.playWhenReady = playWhenReady
        } catch (failure: Throwable) {
            runCatching { restore(snapshot) }
                .exceptionOrNull()
                ?.let(failure::addSuppressed)
            runCatching(onFailed)
                .exceptionOrNull()
                ?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun restore(snapshot: Snapshot) {
        val previousItem = snapshot.mediaItem
        if (previousItem == null) {
            player.clearMediaItems()
        } else {
            player.setMediaItem(previousItem)
            if (snapshot.wasPrepared) {
                player.prepare()
            }
        }
        player.playWhenReady = snapshot.playWhenReady
    }

    private data class Snapshot(
        val mediaItem: MediaItem?,
        val playWhenReady: Boolean,
        val wasPrepared: Boolean,
    )
}

internal fun completeFreshStreamOpen(
    result: FreshStreamOpenResult,
    onOpened: () -> Unit,
): FreshStreamOpenResult {
    if (result == FreshStreamOpenResult.Opened) {
        onOpened()
    }
    return result
}

internal fun openAndCommitChannelSelection(
    channel: String,
    openSelectedStream: (channel: String, onOpened: () -> Unit) -> FreshStreamOpenResult,
    commitSelection: (String) -> Unit,
): FreshStreamOpenResult {
    val normalizedChannel = normalizePersistedChannel(channel)
    return openSelectedStream(normalizedChannel) {
        commitSelection(normalizedChannel)
    }
}

internal fun runFreshStreamOpenTransaction(
    coordinator: FreshStreamOpenCoordinator,
    cancelPendingReconnect: () -> Unit,
    onFinished: () -> Unit = {},
    transaction: (openGeneration: Long) -> Unit,
): FreshStreamOpenResult {
    return coordinator.runOpenTransaction(onFinished = onFinished) { openGeneration ->
        cancelPendingReconnect()
        transaction(openGeneration)
    }
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
