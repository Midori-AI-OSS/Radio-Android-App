package xyz.midoriai.radio.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.midoriai.radio.radioapi.ArtPayload
import xyz.midoriai.radio.radioapi.CurrentPayload
import xyz.midoriai.radio.ui.screens.nowplaying.RadioPlaybackState

class RadioPlaybackControllerConnection(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, RadioPlaybackService::class.java),
    )

    private val _playbackState = MutableStateFlow<RadioPlaybackState>(RadioPlaybackState.Idle)
    val playbackState: StateFlow<RadioPlaybackState> = _playbackState.asStateFlow()

    private val _currentTrack = MutableStateFlow<CurrentPayload?>(null)
    val currentTrack: StateFlow<CurrentPayload?> = _currentTrack.asStateFlow()

    private val _art = MutableStateFlow<ArtPayload?>(null)
    val art: StateFlow<ArtPayload?> = _art.asStateFlow()

    private val _channels = MutableStateFlow(listOf("all"))
    val channels: StateFlow<List<String>> = _channels.asStateFlow()

    private val _selectedChannel = MutableStateFlow("all")
    val selectedChannel: StateFlow<String> = _selectedChannel.asStateFlow()

    private val _selectedQuality = MutableStateFlow("medium")
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    private val _pendingQuality = MutableStateFlow<String?>(null)
    val pendingQuality: StateFlow<String?> = _pendingQuality.asStateFlow()

    private val connectedController = MutableStateFlow<MediaController?>(null)

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isClosed = false

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) {
            applySnapshot(RadioSessionSnapshotCodec.fromBundle(extras))
        }

        override fun onDisconnected(controller: MediaController) {
            if (this@RadioPlaybackControllerConnection.controller === controller) {
                this@RadioPlaybackControllerConnection.controller = null
                connectedController.value = null
            }

            if (!isClosed) {
                connectController()
            }
        }
    }

    init {
        connectController()
    }

    fun play() {
        withController { it.play() }
    }

    fun pause() {
        withController { it.pause() }
    }

    fun selectAdjacentChannel(direction: Int) {
        val request = createAdjacentChannelSessionCommandRequest(direction) ?: return

        withController { controller ->
            controller.sendCustomCommand(
                SELECT_ADJACENT_CHANNEL_SESSION_COMMAND,
                bundleOf(ARG_DIRECTION to request.direction),
            )
        }
    }

    fun setQuality(quality: String) {
        withController { controller ->
            controller.sendCustomCommand(
                SET_QUALITY_SESSION_COMMAND,
                bundleOf(ARG_QUALITY to quality),
            )
        }
    }

    fun close() {
        isClosed = true
        controllerFuture = null
        val connected = controller
        controller = null
        connectedController.value = null
        connected?.release()
    }

    private fun connectController() {
        if (isClosed || controller != null || controllerFuture != null) {
            return
        }

        val future = MediaController.Builder(appContext, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                controllerFuture = null
                val builtController = runCatching { future.get() }.getOrNull() ?: return@addListener
                if (isClosed) {
                    builtController.release()
                    return@addListener
                }

                controller = builtController
                connectedController.value = builtController
                applySnapshot(RadioSessionSnapshotCodec.fromBundle(builtController.sessionExtras))
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: scope.launch {
            val connected = connectedController.filterNotNull().first()
            action(connected)
        }
    }

    private fun applySnapshot(snapshot: RadioSessionSnapshot?) {
        if (snapshot == null) {
            return
        }

        _playbackState.value = snapshot.playbackState
        _currentTrack.value = snapshot.currentTrack
        _art.value = snapshot.art
        _channels.value = snapshot.channels
        _selectedChannel.value = snapshot.selectedChannel
        _selectedQuality.value = snapshot.selectedQuality
        _pendingQuality.value = snapshot.pendingQuality
    }
}
