package xyz.midoriai.radio.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioSessionPlayerTest {
    @Test
    @Suppress("DEPRECATION")
    fun liveStreamSessionPlayer_reportsUnknownDurationAndIsNotSeekable() {
        val artworkData = byteArrayOf(1, 2, 3, 4)
        val metadata = MediaMetadata.Builder()
            .setTitle("Dream Circuit")
            .setArtworkData(artworkData)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId("all")
            .setMediaMetadata(metadata)
            .build()
        val sessionPlayer = RadioSessionPlayer(
            player = fakePlayer(
                duration = 245_000L,
                contentDuration = 245_000L,
                currentMediaItem = mediaItem,
            ),
            onPlay = {},
            onPause = {},
            onSelectAdjacentChannel = {},
        )

        assertEquals(C.TIME_UNSET, sessionPlayer.duration)
        assertEquals(C.TIME_UNSET, sessionPlayer.contentDuration)
        assertTrue(sessionPlayer.isCurrentMediaItemLive)
        assertFalse(sessionPlayer.isCurrentMediaItemSeekable)
        assertEquals("Dream Circuit", sessionPlayer.currentMediaItem?.mediaMetadata?.title)
        assertEquals(artworkData.toList(), sessionPlayer.currentMediaItem?.mediaMetadata?.artworkData?.toList())
    }

    @Test
    fun liveStreamSessionPlayer_preservesTransportCallbacks() {
        val callbacks = mutableListOf<String>()
        val sessionPlayer = RadioSessionPlayer(
            player = fakePlayer(currentMediaItem = null),
            onPlay = { callbacks += "play" },
            onPause = { callbacks += "pause" },
            onSelectAdjacentChannel = { direction -> callbacks += "adjacent:$direction" },
        )

        sessionPlayer.play()
        sessionPlayer.pause()
        sessionPlayer.setPlayWhenReady(true)
        sessionPlayer.setPlayWhenReady(false)
        sessionPlayer.stop()
        sessionPlayer.seekToNextMediaItem()
        sessionPlayer.seekToPreviousMediaItem()
        sessionPlayer.seekToNext()
        sessionPlayer.seekToPrevious()

        assertEquals(
            listOf(
                "play",
                "pause",
                "play",
                "pause",
                "pause",
                "adjacent:1",
                "adjacent:-1",
                "adjacent:1",
                "adjacent:-1",
            ),
            callbacks,
        )
        assertFalse(sessionPlayer.isCurrentMediaItemLive)
        assertNull(sessionPlayer.currentMediaItem)
    }

    private fun fakePlayer(
        duration: Long = 0L,
        contentDuration: Long = 0L,
        currentMediaItem: MediaItem? = null,
    ): Player {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getDuration" -> duration
                "getContentDuration" -> contentDuration
                "getCurrentMediaItem" -> currentMediaItem
                "isCurrentMediaItemLive" -> false
                "isCurrentMediaItemSeekable" -> true
                "addListener", "removeListener", "release" -> null
                "getAvailableCommands" -> Player.Commands.EMPTY
                "isCommandAvailable" -> false
                "getPlaybackState" -> Player.STATE_IDLE
                "getRepeatMode" -> Player.REPEAT_MODE_OFF
                "getCurrentTimeline" -> androidx.media3.common.Timeline.EMPTY
                "hashCode" -> System.identityHashCode(method)
                "equals" -> args?.firstOrNull() === this
                "toString" -> "FakePlayer"
                else -> defaultValue(method)
            }
        }

        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            handler,
        ) as Player
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
