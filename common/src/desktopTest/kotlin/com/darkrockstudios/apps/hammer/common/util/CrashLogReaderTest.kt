package com.darkrockstudios.apps.hammer.common.util

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrashLogReaderTest {

	private val fileSystem = FakeFileSystem()
	private val logDir = "/logs".toPath()

	private fun write(name: String, content: String) {
		fileSystem.createDirectories(logDir)
		fileSystem.write(logDir / name) { writeUtf8(content) }
	}

	@Test
	fun returnsNullWhenDirectoryMissing() {
		assertNull(readLatestCrash(fileSystem, logDir))
	}

	@Test
	fun returnsNullWhenNoCrashFiles() {
		write("2026-01-01T00.txt", "just a log")
		assertNull(readLatestCrash(fileSystem, logDir))
	}

	@Test
	fun readsTheOnlyCrash() {
		write("crash-1000.txt", "stack trace here")
		val crash = readLatestCrash(fileSystem, logDir)
		assertEquals("crash-1000.txt", crash?.fileName)
		assertEquals("stack trace here", crash?.content)
	}

	@Test
	fun picksNewestCrashByTimestamp() {
		// Filenames intentionally out of write order to prove ordering is by parsed millis, not mtime.
		write("crash-3000.txt", "newest")
		write("crash-1000.txt", "oldest")
		write("crash-2000.txt", "middle")
		val crash = readLatestCrash(fileSystem, logDir)
		assertEquals("crash-3000.txt", crash?.fileName)
		assertEquals("newest", crash?.content)
	}

	@Test
	fun ignoresNonCrashFilesWhenPicking() {
		write("crash-1000.txt", "the crash")
		write("2026-07-15T12.txt", "a much newer plain log")
		val crash = readLatestCrash(fileSystem, logDir)
		assertEquals("crash-1000.txt", crash?.fileName)
	}
}
