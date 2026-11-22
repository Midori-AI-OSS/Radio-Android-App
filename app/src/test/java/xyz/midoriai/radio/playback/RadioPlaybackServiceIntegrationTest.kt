package xyz.midoriai.radio.playback

import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import xyz.midoriai.radio.radioapi.ArtPayload
import xyz.midoriai.radio.radioapi.ChannelsPayload
import xyz.midoriai.radio.radioapi.CurrentPayload
import xyz.midoriai.radio.radioapi.HealthPayload
import xyz.midoriai.radio.radioapi.RadioApi
import xyz.midoriai.radio.radioapi.RadioApiResult
import xyz.midoriai.radio.ui.screens.nowplaying.RadioPlaybackState

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class RadioPlaybackServiceIntegrationTest {
    private lateinit var player: RecordingExoPlayer
    private lateinit var service: RadioPlaybackServiceIntegrationHarness
    private var serviceController: org.robolectric.android.controller.ServiceController<RadioPlaybackServiceIntegrationHarness>? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private lateinit var controller: MediaController

    @Before
    fun setUp() {
        player = RecordingExoPlayer()
        RadioPlaybackServiceIntegrationHarness.player = player
        serviceController = Robolectric
            .buildService(RadioPlaybackServiceIntegrationHarness::class.java)
            .create()
        service = checkNotNull(serviceController).get()
        drainMainLooper()

        val session = checkNotNull(service.connectedSession)
        controllerFuture = MediaController.Builder(
            ApplicationProvider.getApplicationContext(),
            session.token,
        ).buildAsync()
        drainMainLooper()
        controller = checkNotNull(controllerFuture).get(5, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        controllerFuture?.let(MediaController::releaseFuture)
        serviceController?.destroy()
        RadioPlaybackServiceIntegrationHarness.player = null
        drainMainLooper()
    }

    @Test
    fun registeredServiceListener_reopensEndedSourceOnce() {
        controller.play()
        drainMainLooper()
        player.emitState(Player.STATE_READY, isPlaying = true)
        drainMainLooper()
        assertEquals(RadioPlaybackState.Playing, snapshot().playbackState)

        val serviceListener = player.listeners
            .filterIsInstance<RadioPlaybackPlayerEventListener>()
            .single()
        val preparesBeforeEnd = player.prepareCount

        player.emitState(Player.STATE_ENDED)
        drainMainLooper()

        assertEquals(preparesBeforeEnd + 1, player.prepareCount)
        assertEquals(Player.STATE_BUFFERING, player.playbackState)

        // A delayed duplicate callback can arrive after the replacement source is installed.
        serviceListener.onPlaybackStateChanged(Player.STATE_ENDED)
        drainMainLooper()

        assertEquals(preparesBeforeEnd + 1, player.prepareCount)
    }

    @Test
    fun playerErrorQueuesRetry_andReadyRestoredSourceDoesNotCancelTargetRetry() {
        startPlayback()
        player.emitError()
        drainMainLooper()
        assertEquals(RadioPlaybackState.Reconnecting(1, 50L), snapshot().playbackState)

        player.failNextMediaItemId = "focus"
        runCatching {
            controller.setMediaItem(MediaItem.Builder().setMediaId("focus").build())
        }
        drainMainLooper()
        assertEquals("all", player.mediaItem?.mediaId)
        assertEquals(RadioPlaybackState.Reconnecting(1, 50L), snapshot().playbackState)

        // The old source is healthy again, but it does not satisfy the pending focus selection.
        player.emitState(Player.STATE_READY, isPlaying = true)
        drainMainLooper()
        assertEquals(RadioPlaybackState.Reconnecting(1, 50L), snapshot().playbackState)

        val preparesBeforeRetry = player.prepareCount
        idleMainLooper(100L)

        assertEquals("focus", player.mediaItem?.mediaId)
        assertEquals(preparesBeforeRetry + 1, player.prepareCount)
    }

    @Test
    fun pauseAndStopCallbacksCancelPendingRetries() {
        startPlayback()
        player.emitError()
        drainMainLooper()
        assertEquals(RadioPlaybackState.Reconnecting(1, 50L), snapshot().playbackState)
        var preparesAfterError = player.prepareCount

        controller.pause()
        drainMainLooper()
        idleMainLooper(100L)

        assertEquals(preparesAfterError, player.prepareCount)
        assertEquals(RadioPlaybackState.Stopped, snapshot().playbackState)

        controller.play()
        drainMainLooper()
        player.emitError()
        drainMainLooper()
        preparesAfterError = player.prepareCount

        controller.stop()
        drainMainLooper()
        idleMainLooper(100L)

        assertEquals(preparesAfterError, player.prepareCount)
        assertEquals(RadioPlaybackState.Stopped, snapshot().playbackState)
    }

    @Test
    fun newerSessionSelectionCancelsPendingRetry() {
        startPlayback()
        player.emitError()
        drainMainLooper()
        assertEquals(RadioPlaybackState.Reconnecting(1, 50L), snapshot().playbackState)

        val preparesBeforeSelection = player.prepareCount
        controller.setMediaItem(MediaItem.Builder().setMediaId("focus").build())
        drainMainLooper()
        assertEquals("focus", player.mediaItem?.mediaId)
        assertEquals(preparesBeforeSelection + 1, player.prepareCount)
        assertEquals("focus", snapshot().selectedChannel)

        idleMainLooper(100L)

        assertEquals("focus", player.mediaItem?.mediaId)
        assertEquals(preparesBeforeSelection + 1, player.prepareCount)
    }

    private fun startPlayback() {
        controller.play()
        drainMainLooper()
        player.emitState(Player.STATE_READY, isPlaying = true)
        drainMainLooper()
        assertEquals(RadioPlaybackState.Playing, snapshot().playbackState)
    }

    private fun snapshot(): RadioSessionSnapshot {
        val session = checkNotNull(service.connectedSession)
        return checkNotNull(RadioSessionSnapshotCodec.fromBundle(session.sessionExtras))
    }

    private fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idleMainLooper(delayMs: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(delayMs, TimeUnit.MILLISECONDS)
    }
}

@UnstableApi
internal class RadioPlaybackServiceIntegrationHarness : RadioPlaybackService() {
    companion object {
        var player: RecordingExoPlayer? = null
    }

    var connectedSession: MediaLibrarySession? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val field = RadioPlaybackService::class.java.getDeclaredField("mediaSession")
        field.isAccessible = true
        connectedSession = field.get(this) as? MediaLibrarySession
    }

    override fun createExoPlayer(): ExoPlayer = checkNotNull(player).proxy

    override fun createRadioApi(): RadioApi = NoNetworkRadioApi

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return super.onGetSession(controllerInfo).also { connectedSession = it }
    }
}

private object NoNetworkRadioApi : RadioApi {
    override suspend fun fetchHealth(): RadioApiResult<HealthPayload> = error("network disabled in test")
    override suspend fun fetchChannels(): RadioApiResult<ChannelsPayload> = error("network disabled in test")
    override suspend fun fetchCurrent(channel: String?): RadioApiResult<CurrentPayload> = error("network disabled in test")
    override suspend fun fetchArt(channel: String?): RadioApiResult<ArtPayload> = error("network disabled in test")
}

internal class RecordingExoPlayer {
    val listeners = linkedSetOf<Player.Listener>()
    val proxy: ExoPlayer = Proxy.newProxyInstance(
        ExoPlayer::class.java.classLoader,
        arrayOf(ExoPlayer::class.java),
    ) { instance, method, args -> invoke(instance, method, args) } as ExoPlayer

    var playbackState = Player.STATE_IDLE
        private set
    var playWhenReady = false
        private set
    var isPlaying = false
        private set
    var mediaItem: MediaItem? = null
        private set
    var prepareCount = 0
        private set
    var failNextMediaItemId: String? = null
    private var volume = 1f
    private var repeatMode = Player.REPEAT_MODE_OFF

    fun emitState(state: Int, isPlaying: Boolean = false) {
        playbackState = state
        this.isPlaying = isPlaying
        listeners.toList().forEach { it.onPlaybackStateChanged(state) }
        if (isPlaying) {
            listeners.toList().forEach { it.onIsPlayingChanged(true) }
        }
    }

    fun emitError() {
        playbackState = Player.STATE_IDLE
        isPlaying = false
        val error = PlaybackException(
            "test player error",
            null,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )
        listeners.toList().forEach { it.onPlayerError(error) }
    }

    private fun invoke(instance: Any, method: Method, args: Array<out Any?>?): Any? {
        return when (method.name) {
            "getApplicationLooper" -> Looper.getMainLooper()
            "canAdvertiseSession" -> true
            "getAvailableCommands" -> Player.Commands.Builder().addAllCommands().build()
            "isCommandAvailable" -> true
            "getCurrentTimeline" -> Timeline.EMPTY
            "getCurrentMediaItemIndex", "getCurrentPeriodIndex", "getCurrentWindowIndex" -> 0
            "getCurrentAdGroupIndex", "getCurrentAdIndexInAdGroup" -> C.INDEX_UNSET
            "getMediaItemCount" -> if (mediaItem == null) 0 else 1
            "getPlaybackState" -> playbackState
            "getPlayWhenReady" -> playWhenReady
            "isPlaying" -> isPlaying
            "isLoading" -> false
            "getRepeatMode" -> repeatMode
            "getPlaybackSuppressionReason" -> Player.PLAYBACK_SUPPRESSION_REASON_NONE
            "getMediaMetadata" -> mediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
            "getPlaylistMetadata" -> MediaMetadata.EMPTY
            "getDeviceInfo" -> DeviceInfo.UNKNOWN
            "getAudioAttributes" -> AudioAttributes.DEFAULT
            "getPlaybackParameters" -> PlaybackParameters.DEFAULT
            "getCurrentTracks" -> Tracks.EMPTY
            "getTrackSelectionParameters" -> TrackSelectionParameters.Builder().build()
            "getVideoSize" -> VideoSize.UNKNOWN
            "getCurrentCues" -> CueGroup.EMPTY_TIME_ZERO
            "getPlayerError", "getCurrentMediaItem" -> if (method.name == "getCurrentMediaItem") mediaItem else null
            "getCurrentPosition", "getBufferedPosition", "getTotalBufferedDuration" -> 0L
            "getDuration", "getContentDuration" -> C.TIME_UNSET
            "getVolume" -> volume
            "getSeekBackIncrement", "getSeekForwardIncrement", "getMaxSeekToPreviousPosition" -> 0L
            "addListener" -> { listeners += args?.get(0) as Player.Listener; null }
            "removeListener" -> { listeners -= args?.get(0) as Player.Listener; null }
            "setAudioAttributes", "setHandleAudioBecomingNoisy", "release" -> null
            "setRepeatMode" -> { repeatMode = args?.get(0) as Int; null }
            "setVolume" -> { volume = args?.get(0) as Float; null }
            "setPlayWhenReady" -> setPlayWhenReady(args?.get(0) as Boolean)
            "setMediaItem", "setMediaItems" -> setMediaItem(args?.get(0))
            "clearMediaItems" -> { mediaItem = null; null }
            "prepare" -> {
                prepareCount += 1
                emitState(Player.STATE_BUFFERING)
                null
            }
            "stop" -> {
                playWhenReady = false
                isPlaying = false
                listeners.toList().forEach {
                    it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                }
                emitState(Player.STATE_IDLE)
                null
            }
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === args?.firstOrNull()
            "toString" -> "RecordingExoPlayer"
            else -> defaultValue(method)
        }
    }

    private fun setPlayWhenReady(value: Boolean) {
        playWhenReady = value
        if (!value) isPlaying = false
        listeners.toList().forEach {
            it.onPlayWhenReadyChanged(value, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }
    }

    private fun setMediaItem(argument: Any?) {
        val item = when (argument) {
            is MediaItem -> argument
            is List<*> -> argument.firstOrNull() as? MediaItem
            else -> null
        }
        if (item?.mediaId == failNextMediaItemId) {
            failNextMediaItemId = null
            error("injected stream installation failure")
        }
        mediaItem = item
        listeners.toList().forEach {
            it.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        }
    }

    private fun defaultValue(method: Method): Any? {
        return when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Void.TYPE -> null
            else -> null
        }
    }
}
