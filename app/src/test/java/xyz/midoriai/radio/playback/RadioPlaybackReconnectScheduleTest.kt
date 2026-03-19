package xyz.midoriai.radio.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioPlaybackReconnectScheduleTest {
    @Test
    fun reconnectDelaySchedule_matchesApprovedAggressiveValues() {
        assertEquals(
            listOf(50L, 100L, 200L, 400L, 800L, 1500L),
            reconnectDelaysFromServiceSource(),
        )
    }

    @Test
    fun reconnectDelaySchedule_appliesExpectedDelayPerAttemptSequence() {
        val delays = reconnectDelaysFromServiceSource()

        assertEquals(
            listOf(50L, 100L, 200L, 400L, 800L, 1500L, 1500L, 1500L),
            (1..8).map { attempt -> delayForAttempt(attempt, delays) },
        )
    }

    private fun reconnectDelaysFromServiceSource(): List<Long> {
        val userDir = System.getProperty("user.dir") ?: error("user.dir is not set")
        val serviceFile = generateSequence(File(userDir).absoluteFile) { current -> current.parentFile }
            .map { root -> File(root, SERVICE_SOURCE_PATH) }
            .firstOrNull { candidate -> candidate.isFile }
            ?: error(
                "Could not locate $SERVICE_SOURCE_PATH from user.dir=$userDir",
            )

        val declaration =
            Regex("""private\s+val\s+reconnectDelaysMs:\s+List<Long>\s*=\s*listOf\(([^)]*)\)""")
                .find(serviceFile.readText())
                ?.groupValues
                ?.get(1)
                ?: error("Could not locate reconnectDelaysMs declaration in ${serviceFile.absolutePath}")

        return Regex("""(\d+)L""")
            .findAll(declaration)
            .map { match -> match.groupValues[1].toLong() }
            .toList()
    }

    private fun delayForAttempt(attempt: Int, delays: List<Long>): Long {
        require(attempt > 0) { "attempt must be positive" }
        return delays[(attempt - 1).coerceAtMost(delays.lastIndex)]
    }

    private companion object {
        const val SERVICE_SOURCE_PATH = "app/src/main/java/xyz/midoriai/radio/playback/RadioPlaybackService.kt"
    }
}
