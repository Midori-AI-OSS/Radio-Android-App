package xyz.midoriai.radio.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.Futures
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class MediaSessionMutationIntegrationTest {
    private var session: MediaSession? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private lateinit var controller: MediaController
    private lateinit var streamOpening: MediaSessionStreamOpening
    private val canonicalInstalls = mutableListOf<List<String>>()
    private var delegatedMutations = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val underlyingPlayer = sessionCompatiblePlayer { delegatedMutations += 1 }
        streamOpening = MediaSessionStreamOpening(
            resolveRequestedChannel = { items ->
                items.firstOrNull()?.mediaId?.takeIf { it in setOf("all", "focus") } ?: "all"
            },
            effectiveQualityForNextOpen = { "high" },
            buildPlaybackItems = { channel, quality ->
                listOf(
                    MediaItem.Builder()
                        .setMediaId(channel)
                        .setUri("https://radio.example/$channel/$quality")
                        .build(),
                )
            },
            openSelectedStream = { channel ->
                canonicalInstalls += listOf(channel)
                FreshStreamOpenResult.Opened
            },
        )
        val sessionPlayer = streamOpening.wrapPlayer(
            player = underlyingPlayer,
            onPlay = {},
            onPause = {},
            onSelectAdjacentChannel = {},
        )
        session = MediaSession.Builder(context, sessionPlayer)
            .setId("mutation-test-${sessionIds.incrementAndGet()}")
            .setCallback(MutationCallback())
            .build()
        controllerFuture = MediaController.Builder(context, checkNotNull(session).token).buildAsync()
        drainMainLooper()
        controller = checkNotNull(controllerFuture).get(5, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        controllerFuture?.let(MediaController::releaseFuture)
        session?.release()
        drainMainLooper()
    }

    @Test
    fun controllerExposesOnlyCanonicalSetMutationCommand() {
        assertTrue(controller.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
        assertFalse(controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
    }

    @Test
    fun everyPermittedControllerSetEntryPointUsesCanonicalInstallation() {
        val item = MediaItem.Builder().setMediaId("focus").build()

        controller.setMediaItem(item)
        drainMainLooper()
        controller.setMediaItem(item, true)
        drainMainLooper()
        controller.setMediaItem(item, 120L)
        drainMainLooper()

        assertEquals(List(3) { listOf("focus") }, canonicalInstalls)
        assertEquals(0, delegatedMutations)
    }

    @Test
    fun permittedSetCanonicalizesArbitraryUriBeforeInstallation() {
        val arbitraryItem = MediaItem.Builder()
            .setMediaId("unrecognized")
            .setUri("https://example.invalid/stream")
            .build()

        controller.setMediaItem(arbitraryItem)
        drainMainLooper()

        assertEquals(listOf(listOf("all")), canonicalInstalls)
        assertEquals(0, delegatedMutations)
    }

    @Test
    fun unsupportedPlaylistMutationsCannotReachWrappedOrUnderlyingPlayer() {
        val item = MediaItem.Builder().setMediaId("arbitrary").setUri("https://example.invalid/stream").build()

        controller.setMediaItems(listOf(item))
        controller.setMediaItems(listOf(item), true)
        controller.setMediaItems(listOf(item), 0, C.TIME_UNSET)
        controller.addMediaItem(item)
        controller.addMediaItem(0, item)
        controller.addMediaItems(listOf(item))
        controller.addMediaItems(0, listOf(item))
        controller.replaceMediaItem(0, item)
        controller.replaceMediaItems(0, 1, listOf(item))
        controller.removeMediaItem(0)
        controller.removeMediaItems(0, 1)
        controller.clearMediaItems()
        drainMainLooper()

        assertTrue(canonicalInstalls.isEmpty())
        assertEquals(0, delegatedMutations)
    }

    private inner class MutationCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ConnectionResult {
            return ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(RADIO_SESSION_PLAYER_COMMANDS)
                .build()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val resolvedItems = streamOpening.resolvePlaybackItems(mediaItems)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(resolvedItems, startIndex, startPositionMs),
            )
        }
    }

    private fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sessionCompatiblePlayer(onMutation: () -> Unit): Player {
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getApplicationLooper" -> Looper.getMainLooper()
                "canAdvertiseSession" -> true
                "getAvailableCommands" -> Player.Commands.Builder().addAllCommands().build()
                "isCommandAvailable" -> true
                "getCurrentTimeline" -> Timeline.EMPTY
                "getCurrentMediaItemIndex", "getCurrentPeriodIndex", "getCurrentWindowIndex" -> 0
                "getCurrentAdGroupIndex", "getCurrentAdIndexInAdGroup" -> C.INDEX_UNSET
                "getMediaItemCount" -> 0
                "getPlaybackState" -> Player.STATE_IDLE
                "getPlayWhenReady", "isPlaying", "isLoading" -> false
                "getRepeatMode" -> Player.REPEAT_MODE_OFF
                "getPlaybackSuppressionReason" -> Player.PLAYBACK_SUPPRESSION_REASON_NONE
                "getMediaMetadata", "getPlaylistMetadata" -> MediaMetadata.EMPTY
                "getDeviceInfo" -> DeviceInfo.UNKNOWN
                "getAudioAttributes" -> AudioAttributes.DEFAULT
                "getPlaybackParameters" -> PlaybackParameters.DEFAULT
                "getCurrentTracks" -> Tracks.EMPTY
                "getTrackSelectionParameters" -> TrackSelectionParameters.Builder().build()
                "getVideoSize" -> VideoSize.UNKNOWN
                "getCurrentCues" -> CueGroup.EMPTY_TIME_ZERO
                "getPlayerError", "getCurrentMediaItem" -> null
                "addListener", "removeListener", "prepare", "release" -> null
                "setMediaItem", "setMediaItems", "addMediaItem", "addMediaItems",
                "replaceMediaItem", "replaceMediaItems", "removeMediaItem", "removeMediaItems",
                "clearMediaItems" -> {
                    onMutation()
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "SessionCompatiblePlayer"
                else -> defaultValue(method)
            }
        } as Player
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

    private companion object {
        val sessionIds = AtomicInteger()
    }
}
