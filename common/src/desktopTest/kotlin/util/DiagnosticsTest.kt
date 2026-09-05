package util

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.buildDiagnosticsReport
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private val LOG_DIR = "/logs".toPath()

/** The real clock stamps both writes into the same millisecond, which hides the newest-log pick. */
private class SteppingClock : Clock {
	private var millis = 0L

	override fun now(): Instant {
		millis += 1_000
		return Instant.fromEpochMilliseconds(millis)
	}
}

class DiagnosticsTest {

	private val fileSystem = FakeFileSystem(SteppingClock()).apply { createDirectories(LOG_DIR) }

	@AfterTest
	fun tearDown() = fileSystem.checkNoOpenFiles()

	private fun writeLog(name: String, contents: String) {
		fileSystem.write(LOG_DIR / name) { writeUtf8(contents) }
	}

	private suspend fun report() = buildDiagnosticsReport(LOG_DIR.toString(), fileSystem)

	@Test
	fun `the banner leads and the newest log follows`() = runTest {
		writeLog("2026-01-01T000000Z.txt", "older\n")
		writeLog("2026-01-02T000000Z.txt", "newer\n")

		val report = report()

		assertTrue(report.lines().first().startsWith("Hammer v${BuildMetadata.APP_VERSION}"))
		assertTrue(report.endsWith("newer"))
		assertFalse(report.contains("older"))
	}

	@Test
	fun `only the tail of a long log is taken`() = runTest {
		writeLog("current.txt", (1..500).joinToString("\n") { "line $it" })

		val logLines = report().substringAfter("\n\n").lines()

		assertEquals(200, logLines.size)
		assertEquals("line 301", logLines.first())
		assertEquals("line 500", logLines.last())
	}

	@Test
	fun `an empty log directory still yields the banner`() = runTest {
		val report = report()

		assertTrue(report.startsWith("Hammer v${BuildMetadata.APP_VERSION}"))
		assertTrue(report.endsWith("(no log file found)"))
	}

	@Test
	fun `a missing log directory still yields the banner`() = runTest {
		val report = buildDiagnosticsReport("/nope", fileSystem)

		assertTrue(report.startsWith("Hammer v${BuildMetadata.APP_VERSION}"))
		assertTrue(report.endsWith("(no log file found)"))
	}
}
