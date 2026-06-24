package fileio

import com.darkrockstudios.apps.hammer.common.fileio.okio.ContainedFileSystem
import com.darkrockstudios.apps.hammer.common.fileio.okio.ContainmentViolationException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContainedFileSystemTest {

	private val cacheRoot = "/app/cache".toPath()
	private val configRoot = "/app/config".toPath()
	private val projectsRoot = "/home/user/Documents/Projects".toPath()

	private fun fs(roots: List<Path> = listOf(cacheRoot, configRoot, projectsRoot)): Pair<FakeFileSystem, ContainedFileSystem> {
		val delegate = FakeFileSystem()
		roots.forEach { delegate.createDirectories(it) }
		return delegate to ContainedFileSystem(delegate) { roots }
	}

	@Test
	fun `write within a root succeeds`() {
		val (delegate, guarded) = fs()
		val target = projectsRoot / "story.md"

		guarded.write(target) { writeUtf8("hello") }

		assertTrue(delegate.exists(target))
	}

	@Test
	fun `write within each configured root succeeds`() {
		val (_, guarded) = fs()
		guarded.write(cacheRoot / "a.toml") { writeUtf8("a") }
		guarded.write(configRoot / "b.toml") { writeUtf8("b") }
		guarded.write(projectsRoot / "c.md") { writeUtf8("c") }
	}

	@Test
	fun `write outside all roots throws`() {
		val (_, guarded) = fs()
		assertFailsWith<ContainmentViolationException> {
			guarded.write("/etc/passwd".toPath()) { writeUtf8("evil") }
		}
	}

	@Test
	fun `delete outside all roots throws`() {
		val (delegate, guarded) = fs()
		delegate.createDirectories("/etc".toPath())
		delegate.write("/etc/passwd".toPath()) { writeUtf8("x") }

		assertFailsWith<ContainmentViolationException> {
			guarded.delete("/etc/passwd".toPath())
		}
	}

	@Test
	fun `atomicMove with a target outside all roots throws`() {
		val (_, guarded) = fs()
		val source = projectsRoot / "a.md"
		guarded.write(source) { writeUtf8("x") }

		assertFailsWith<ContainmentViolationException> {
			guarded.atomicMove(source, "/tmp/escape.md".toPath())
		}
	}

	@Test
	fun `atomicMove within a root succeeds`() {
		val (delegate, guarded) = fs()
		val source = projectsRoot / "a.md"
		val target = projectsRoot / "b.md"
		guarded.write(source) { writeUtf8("x") }

		guarded.atomicMove(source, target)

		assertTrue(delegate.exists(target))
	}

	@Test
	fun `creating a root itself succeeds on a clean filesystem`() {
		val delegate = FakeFileSystem()
		val guarded = ContainedFileSystem(delegate) { listOf(cacheRoot) }

		// First run: nothing exists yet. createDirectories must scaffold the root
		// (and its ancestors) without being blocked.
		guarded.createDirectories(cacheRoot / "projects" / "MyProject")

		assertTrue(delegate.exists(cacheRoot / "projects" / "MyProject"))
	}

	@Test
	fun `creating an ancestor of a root is allowed`() {
		val delegate = FakeFileSystem()
		val guarded = ContainedFileSystem(delegate) { listOf(cacheRoot) }

		guarded.createDirectory("/app".toPath())

		assertTrue(delegate.exists("/app".toPath()))
	}

	@Test
	fun `creating a sibling-escape directory is blocked`() {
		val delegate = FakeFileSystem()
		delegate.createDirectories("/app".toPath())
		val guarded = ContainedFileSystem(delegate) { listOf(cacheRoot) }

		assertFailsWith<ContainmentViolationException> {
			guarded.createDirectory("/app/evil".toPath())
		}
	}

	@Test
	fun `openReadWrite outside all roots throws`() {
		val (_, guarded) = fs()
		assertFailsWith<ContainmentViolationException> {
			guarded.openReadWrite("/etc/passwd".toPath())
		}
	}

	@Test
	fun `openReadWrite within a root succeeds`() {
		val (delegate, guarded) = fs()
		val target = projectsRoot / "handle.bin"

		guarded.openReadWrite(target, mustCreate = true).use { it.write(0, ByteArray(4), 0, 4) }

		assertTrue(delegate.exists(target))
	}

	@Test
	fun `a traversal write that escapes a root throws`() {
		val (_, guarded) = fs()
		assertFailsWith<ContainmentViolationException> {
			guarded.write(projectsRoot / ".." / ".." / "evil.md") { writeUtf8("x") }
		}
	}

	@Test
	fun `allowed roots are re-evaluated per call`() {
		val delegate = FakeFileSystem()
		var roots = listOf(cacheRoot)
		delegate.createDirectories(cacheRoot)
		delegate.createDirectories(projectsRoot)
		val guarded = ContainedFileSystem(delegate) { roots }

		assertFailsWith<ContainmentViolationException> {
			guarded.write(projectsRoot / "a.md") { writeUtf8("x") }
		}

		roots = listOf(cacheRoot, projectsRoot)
		guarded.write(projectsRoot / "a.md") { writeUtf8("x") }
		assertTrue(delegate.exists(projectsRoot / "a.md"))
	}
}
