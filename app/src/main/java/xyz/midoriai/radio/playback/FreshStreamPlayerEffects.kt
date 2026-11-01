package xyz.midoriai.radio.playback

import androidx.media3.common.Player
import xyz.midoriai.radio.ui.screens.nowplaying.RadioPlaybackState

/** Applies player and playback-state effects owned by fresh-open transactions. */
internal class FreshStreamPlayerEffects(
    private val player: Player,
    private val normalVolume: Float,
    private val updatePlaybackState: (RadioPlaybackState) -> Unit,
) {
    fun restoreNormalVolume() {
        player.volume = normalVolume
    }

    fun finishChannelSwitch(
        coordinator: FreshStreamOpenCoordinator,
        switchGeneration: Long,
        openResult: FreshStreamOpenResult?,
        playbackDesired: Boolean,
    ) {
        coordinator.finishChannelSwitch(
            queuedGeneration = switchGeneration,
            openResult = openResult,
            playbackDesired = playbackDesired,
            restoreVolume = ::restoreNormalVolume,
            onRejectedOpen = {
                updatePlaybackState(RadioPlaybackState.Connecting)
            },
        )
    }
}
