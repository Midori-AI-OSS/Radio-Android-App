package xyz.midoriai.radio.playback

import java.io.File
import org.junit.Assert.assertFalse
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
    fun artArrival_reportsEffectiveArtworkChangeToCallers() {
        val body = fetchArtForChannelBody()

        assertTrue(
            "fetchArtForChannel() must detect when the effective artwork changed",
            body.contains("hasArtworkChanged(previous, payload)"),
        )
        assertTrue(
            "fetchArtForChannel() must only report changes for channels still in the catalog",
            body.contains("normalizedChannel in ensureAllChannel(_channels.value)"),
        )
        assertTrue(
            "fetchArtForChannel() must return the change result so callers decide notification policy",
            body.contains("): Boolean {"),
        )
        assertFalse(
            "fetchArtForChannel() must not notify the browse tree itself; callers own notifications",
            body.contains("notifyBrowseRootChildrenChanged()"),
        )
    }

    @Test
    fun artCoverage_emitsAtMostOneBrowseNotificationPerPass() {
        val body = refreshBrowseArtCoverageBody()
        val notifyCount = body.split("notifyBrowseRootChildrenChanged()").size - 1

        assertTrue(
            "refreshBrowseArtCoverage() must notify the browse tree at most once per coverage " +
                "pass, not once per changed channel (found $notifyCount)",
            notifyCount == 1,
        )
        assertTrue(
            "the single coverage notification must fire only when at least one channel's " +
                "artwork changed during the pass",
            body.contains("var artworkChanged = false") &&
                body.contains("if (artworkChanged)"),
        )
    }

    @Test
    fun selectedAndAdjacentRefresh_preservesPerChangeBrowseNotifications() {
        val body = refreshSelectedAndAdjacentArtBody()
        val notifyCount = body.split("notifyBrowseRootChildrenChanged()").size - 1
        val conditionalNotifyCount = body.split("if (").size - 1

        assertTrue(
            "refreshSelectedAndAdjacentArt() must keep one browse-tree notification per changed " +
                "artwork (selected + each adjacent), unlike the capped coverage pass " +
                "(found $notifyCount)",
            notifyCount == 2,
        )
        assertTrue(
            "each per-change notification must be emitted only when the fetch reported a change",
            conditionalNotifyCount == 2,
        )
    }

    @Test
    fun failedArtFetch_recordsAttemptInCooldownMap() {
        val body = fetchArtForChannelBody()
        val failureBranch = failureBranchBody()

        assertTrue(
            "a failed fetch must record its attempt time in the per-channel cooldown map",
            failureBranch.contains(
                "artFetchAtMsByChannel[normalizedChannel] = System.currentTimeMillis()",
            ),
        )
        assertTrue(
            "the prefetch cooldown must bound retries even when no payload was cached " +
                "(failed channels must not be re-requested for every browse query)",
            body.contains("(cached != null || lastFetchAt > 0L)"),
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

    private fun refreshSelectedAndAdjacentArtBody(): String {
        return Regex("""private suspend fun refreshSelectedAndAdjacentArt\([\s\S]*?\n    \}""")
            .find(serviceSource())
            ?.groupValues
            ?.get(0)
            ?: error("Could not locate refreshSelectedAndAdjacentArt() in RadioPlaybackService.kt")
    }

    private fun fetchArtForChannelBody(): String {
        return Regex("""private suspend fun fetchArtForChannel\([\s\S]*?\n    \}""")
            .find(serviceSource())
            ?.groupValues
            ?.get(0)
            ?: error("Could not locate fetchArtForChannel() in RadioPlaybackService.kt")
    }

    private fun failureBranchBody(): String {
        return Regex("""is RadioApiResult\.Failure -> \{[\s\S]*?\n\s*\}""")
            .find(fetchArtForChannelBody())
            ?.groupValues
            ?.get(0)
            ?: error("Could not locate the RadioApiResult.Failure branch in fetchArtForChannel()")
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
