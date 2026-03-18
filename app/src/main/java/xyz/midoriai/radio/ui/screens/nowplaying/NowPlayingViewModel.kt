package xyz.midoriai.radio.ui.screens.nowplaying

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import xyz.midoriai.radio.playback.RadioPlaybackControllerConnection

class NowPlayingViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val connection = RadioPlaybackControllerConnection(
        context = application,
        scope = viewModelScope,
    )

    val playbackState = connection.playbackState
    val currentTrack = connection.currentTrack
    val art = connection.art
    val channels = connection.channels
    val selectedChannel = connection.selectedChannel
    val selectedQuality = connection.selectedQuality
    val pendingQuality = connection.pendingQuality

    fun play() {
        connection.play()
    }

    fun pause() {
        connection.pause()
    }

    fun selectAdjacentChannel(direction: Int) {
        connection.selectAdjacentChannel(direction)
    }

    fun setQuality(quality: String) {
        connection.setQuality(quality)
    }

    override fun onCleared() {
        connection.close()
        super.onCleared()
    }
}
