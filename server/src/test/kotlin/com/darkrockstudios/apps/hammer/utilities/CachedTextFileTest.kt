package com.darkrockstudios.apps.hammer.utilities

import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CachedTextFileTest {

	private val path = "/data/file.txt".toPath()

	private fun FakeFileSystem.writeFile(path: Path, contents: String) {
		path.parent?.let { createDirectories(it) }
		write(path) { writeUtf8(contents) }
	}

	@Test
	fun `a null path reads as no text`() {
		assertNull(CachedTextFile(null, FakeFileSystem()).read())
	}

	@Test
	fun `a missing file reads as no text`() {
		assertNull(CachedTextFile(path, FakeFileSystem()).read())
	}

	@Test
	fun `a blank file reads as no text`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "  \n\t ")
		assertNull(CachedTextFile(path, fs).read())
	}

	@Test
	fun `a populated file reads verbatim`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "hello world")
		assertEquals("hello world", CachedTextFile(path, fs).read())
	}

	@Test
	fun `edited content is re-read`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "first")
		val file = CachedTextFile(path, fs)
		assertEquals("first", file.read())

		fs.delete(path)
		fs.writeFile(path, "second, revised and longer")
		assertEquals("second, revised and longer", file.read())
	}

	@Test
	fun `a file that appears after an empty read is picked up`() {
		val fs = FakeFileSystem()
		val file = CachedTextFile(path, fs)
		assertNull(file.read())

		fs.writeFile(path, "now present")
		assertEquals("now present", file.read())
	}
}
