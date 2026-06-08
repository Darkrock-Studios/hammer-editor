package fileio

import com.darkrockstudios.apps.hammer.common.fileio.okio.moveDirectory
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoveDirectoryTest {

	private fun FakeFileSystem.writeText(path: Path, text: String) {
		path.parent?.let { createDirectories(it) }
		write(path) { writeUtf8(text) }
	}

	private fun FakeFileSystem.readText(path: Path): String =
		read(path) { readUtf8() }

	@Test
	fun `moves nested contents and removes the source`() {
		val fs = FakeFileSystem()
		val src = "/data/HammerProjects".toPath()
		val dst = "/sdcard/Documents/HammerProjects".toPath()
		fs.writeText(src / "Alice" / "scenes" / "01-Chapter", "one")
		fs.writeText(src / "Alice" / "scenes" / "02-Chapter", "two")
		fs.writeText(src / "Alice" / "project.toml", "meta")

		fs.moveDirectory(source = src, destination = dst)

		assertFalse(fs.exists(src), "source should be removed")
		assertEquals("one", fs.readText(dst / "Alice" / "scenes" / "01-Chapter"))
		assertEquals("two", fs.readText(dst / "Alice" / "scenes" / "02-Chapter"))
		assertEquals("meta", fs.readText(dst / "Alice" / "project.toml"))
	}

	@Test
	fun `same source and destination is a no-op and preserves data`() {
		val fs = FakeFileSystem()
		val dir = "/sdcard/Documents/HammerProjects".toPath()
		fs.writeText(dir / "Alice" / "scenes" / "02-Chapter", "two")

		// Regression: previously this copied every file onto itself and deleted it,
		// destroying data and crashing on the stale listing.
		fs.moveDirectory(source = dir, destination = dir)

		assertTrue(fs.exists(dir / "Alice" / "scenes" / "02-Chapter"))
		assertEquals("two", fs.readText(dir / "Alice" / "scenes" / "02-Chapter"))
	}

	@Test
	fun `missing source is a no-op`() {
		val fs = FakeFileSystem()
		val src = "/data/HammerProjects".toPath()
		val dst = "/sdcard/Documents/HammerProjects".toPath()

		fs.moveDirectory(source = src, destination = dst)

		assertFalse(fs.exists(src))
		assertFalse(fs.exists(dst))
	}

	@Test
	fun `merges into an existing destination`() {
		val fs = FakeFileSystem()
		val src = "/data/HammerProjects".toPath()
		val dst = "/sdcard/Documents/HammerProjects".toPath()
		fs.writeText(src / "Alice" / "a.txt", "a")
		fs.writeText(dst / "Bob" / "b.txt", "b")

		fs.moveDirectory(source = src, destination = dst)

		assertFalse(fs.exists(src))
		assertEquals("a", fs.readText(dst / "Alice" / "a.txt"))
		assertEquals("b", fs.readText(dst / "Bob" / "b.txt"))
	}
}
