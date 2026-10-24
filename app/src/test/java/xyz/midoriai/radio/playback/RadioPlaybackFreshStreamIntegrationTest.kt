package xyz.midoriai.radio.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackFreshStreamIntegrationTest {
    private val serviceSource: String by lazy {
        val userDir = System.getProperty("user.dir") ?: error("user.dir is not set")
        generateSequence(File(userDir).absoluteFile) { current -> current.parentFile }
            .map { root -> File(root, SERVICE_SOURCE_PATH) }
            .firstOrNull { candidate -> candidate.isFile }
            ?.readText()
            ?: error("Could not locate $SERVICE_SOURCE_PATH from user.dir=$userDir")
    }

    @Test
    fun sessionSelection_preservesCallbackResultWithoutStartingASwitchOpen() {
        val callback = serviceSource.substring(
            serviceSource.indexOf("override fun onSetMediaItems("),
            serviceSource.indexOf("private inner class PlayerEventListener"),
        )

        assertTrue(callback.contains("MediaSession.MediaItemsWithStartPosition("))
        assertTrue(callback.contains("selectChannelImmediately(normalizedChannel, openStream = false)"))
    }

    @Test
    fun freshOpenAndFadeTransactions_eachHaveAGuaranteedVolumeRestorationOwner() {
        val opener = serviceSource.substring(
            serviceSource.indexOf("private fun openFreshStream("),
            serviceSource.indexOf("private fun cancelPendingReconnectJob"),
        )
        val switch = serviceSource.substring(
            serviceSource.indexOf("private fun switchChannelWithFade("),
            serviceSource.indexOf("private fun openFreshStream("),
        )

        assertTrue(opener.contains("restoreVolumeAfterOpen: Boolean = true"))
        assertTrue(opener.contains("if (restoreVolumeAfterOpen)"))
        assertTrue(opener.contains("player.volume = playerVolumeNormal"))
        assertTrue(switch.contains("openFreshStream(restoreVolumeAfterOpen = false)"))
        assertTrue(switch.contains("finally"))
        assertTrue(switch.contains("isCurrentChannelSwitch(switchGeneration)"))
    }

    private companion object {
        const val SERVICE_SOURCE_PATH = "app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackService.kt"
    }
}
