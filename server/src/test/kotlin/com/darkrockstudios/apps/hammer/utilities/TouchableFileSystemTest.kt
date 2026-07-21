package com.darkrockstudios.apps.hammer.utilities

import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * One contract, both implementations. The fake stands in for the real filesystem in every test above
 * [LruDiskCache], so if its modification-time overlay drifts from what a real filesystem does, those
 * tests would keep passing while eviction misbehaved in production.
 */
class TouchableFileSystemTest {

	@TempDir
	lateinit var tempDir: java.nio.file.Path

	private val aWhileAgo = Instant.fromEpochMilliseconds(1_500_000_000_000)

	@Test
	fun `the real filesystem satisfies the touch contract`() =
		assertTouchContract(SystemTouchableFileSystem(), tempDir.toOkioPath())

	@Test
	fun `the fake filesystem satisfies the touch contract`() =
		assertTouchContract(FakeTouchableFileSystem(), "/work".toPath())

	private fun assertTouchContract(fileSystem: TouchableFileSystem, directory: Path) {
		fileSystem.createDirectories(directory)
		val file = directory / "entry"
		fileSystem.write(file) { write(byteArrayOf(1, 2, 3)) }

		// A set time is what a later read reports.
		assertTrue(fileSystem.setLastModified(file, aWhileAgo))
		assertEquals(
			aWhileAgo.toEpochMilliseconds(),
			fileSystem.metadataOrNull(file)?.lastModifiedAtMillis,
			"a touched time should be what metadata reports",
		)

		// Rewriting supersedes it, so a fresh write always reads as recent.
		fileSystem.write(file) { write(byteArrayOf(4)) }
		val afterRewrite = fileSystem.metadataOrNull(file)?.lastModifiedAtMillis
		assertTrue(
			afterRewrite != null && afterRewrite > aWhileAgo.toEpochMilliseconds(),
			"a rewrite should supersede the touched time, was $afterRewrite",
		)

		// Touching something that isn't there fails rather than inventing an entry.
		assertFalse(fileSystem.setLastModified(directory / "absent", aWhileAgo))
	}
}
