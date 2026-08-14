package xyz.midoriai.radio.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackPauseStopTest {
    @Test
    fun pausePlayback_fullyStopsTheUnderlyingPlayer() {
        val body = pausePlaybackBody()

        assertTrue(
            "pausePlayback() must fully stop the player via stop() or clearMediaItems()",
            body.contains(".stop(") || body.contains(".clearMediaItems("),
        )
    }

    @Test
    fun pausePlayback_neverLeavesThePlayerPausedButPrepared() {
        val body = pausePlaybackBody()

        assertFalse(
            "pausePlayback() must not pause the player without a full stop",
            body.contains(".pause(") && !body.contains(".stop(") && !body.contains(".clearMediaItems("),
        )
    }

    private fun pausePlaybackBody(): String {
        val userDir = System.getProperty("user.dir") ?: error("user.dir is not set")
        val serviceFile = generateSequence(File(userDir).absoluteFile) { current -> current.parentFile }
            .map { root -> File(root, SERVICE_SOURCE_PATH) }
            .firstOrNull { candidate -> candidate.isFile }
            ?: error(
                "Could not locate $SERVICE_SOURCE_PATH from user.dir=$userDir",
            )

        return Regex("""private fun pausePlayback\(\) \{([\s\S]*?)\n    \}""")
            .find(serviceFile.readText())
            ?.groupValues
            ?.get(1)
            ?: error("Could not locate pausePlayback() in ${serviceFile.absolutePath}")
    }

    private companion object {
        const val SERVICE_SOURCE_PATH = "app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackService.kt"
    }
}
