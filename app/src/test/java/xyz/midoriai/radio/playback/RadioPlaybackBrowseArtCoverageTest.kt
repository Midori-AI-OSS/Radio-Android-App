package xyz.midoriai.radio.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackBrowseArtCoverageTest {
    @Test
    fun browseRootChildren_triggerArtCoverageForEveryChannel() {
        val body = onGetChildrenBody()

        assertTrue(
            "onGetChildren() must schedule art coverage for every browse channel",
            body.contains("refreshBrowseArtCoverage("),
        )
        assertTrue(
            "onGetChildren() must still build a library item per browse channel",
            body.contains("buildLibraryChannelItem"),
        )
    }

    @Test
    fun browseArtCoverage_fetchesArtInBackgroundWithoutBlockingBrowse() {
        val body = refreshBrowseArtCoverageBody()

        assertTrue(
            "refreshBrowseArtCoverage() must run off the browse-call thread",
            body.contains("serviceScope.launch"),
        )
        assertTrue(
            "refreshBrowseArtCoverage() must reuse the existing art fetch path",
            body.contains("fetchArtForChannel("),
        )
        assertTrue(
            "refreshBrowseArtCoverage() must use the prefetch path so cooldown bounds network work",
            body.contains("isPrefetch = true"),
        )
    }

    @Test
    fun artArrival_notifiesBrowseTreeWhenEffectiveArtworkChanges() {
        val body = fetchArtForChannelBody()

        assertTrue(
            "fetchArtForChannel() must detect when the effective artwork changed",
            body.contains("hasArtworkChanged("),
        )
        assertTrue(
            "fetchArtForChannel() must refresh the browse tree on artwork change",
            body.contains("notifyBrowseRootChildrenChanged()"),
        )
    }

    private fun onGetChildrenBody(): String {
        return Regex("""override fun onGetChildren\([\s\S]*?\n        \}""")
            .find(serviceSource())
            ?.groupValues
            ?.get(0)
            ?: error("Could not locate onGetChildren() in RadioPlaybackService.kt")
    }

    private fun refreshBrowseArtCoverageBody(): String {
        return Regex("""private fun refreshBrowseArtCoverage\([\s\S]*?\n    \}""")
            .find(serviceSource())
            ?.groupValues
            ?.get(0)
            ?: error("Could not locate refreshBrowseArtCoverage() in RadioPlaybackService.kt")
    }

    private fun fetchArtForChannelBody(): String {
        return Regex("""private suspend fun fetchArtForChannel\([\s\S]*?\n    \}""")
            .find(serviceSource())
            ?.groupValues
            ?.get(0)
            ?: error("Could not locate fetchArtForChannel() in RadioPlaybackService.kt")
    }

    private fun serviceSource(): String {
        val userDir = System.getProperty("user.dir") ?: error("user.dir is not set")
        val serviceFile = generateSequence(File(userDir).absoluteFile) { current -> current.parentFile }
            .map { root -> File(root, SERVICE_SOURCE_PATH) }
            .firstOrNull { candidate -> candidate.isFile }
            ?: error(
                "Could not locate $SERVICE_SOURCE_PATH from user.dir=$userDir",
            )
        return serviceFile.readText()
    }

    private companion object {
        const val SERVICE_SOURCE_PATH = "app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackService.kt"
    }
}
