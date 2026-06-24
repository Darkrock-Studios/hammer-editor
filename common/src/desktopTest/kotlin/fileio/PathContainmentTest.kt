package fileio

import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathContainmentTest {

	private val root = "/projects/Test/.drafts/1".toPath()

	@Test
	fun `a genuine descendant is within the root`() {
		assertTrue(("/projects/Test/.drafts/1/1-5-draft-100.md".toPath()).isWithin(root))
	}

	@Test
	fun `the root itself is within the root`() {
		assertTrue(root.isWithin(root))
	}

	@Test
	fun `a parent-climbing traversal escapes the root`() {
		val escape = ("/projects/Test/.drafts/1" / "../../../../evil").toPath()
		assertFalse(escape.isWithin(root))
	}

	@Test
	fun `an embedded dot-dot segment that escapes the root is rejected`() {
		assertFalse(("/projects/Test/.drafts/1/../../evil.md".toPath()).isWithin(root))
	}

	@Test
	fun `a sibling directory is not within the root`() {
		assertFalse(("/projects/Test/.drafts/2/file.md".toPath()).isWithin(root))
	}

	@Test
	fun `a prefix-sharing sibling is not within the root`() {
		// "/projects/Test/.drafts/10" shares the textual prefix "/projects/Test/.drafts/1"
		// but is a different directory.
		assertFalse(("/projects/Test/.drafts/10/file.md".toPath()).isWithin(root))
	}

	@Test
	fun `an absolute reach-out is not within a relative root`() {
		val relativeRoot = ".drafts/1".toPath()
		assertFalse(("/etc/passwd".toPath()).isWithin(relativeRoot))
	}

	@Test
	fun `an unrelated absolute path is not within the root`() {
		assertFalse(("/etc/passwd".toPath()).isWithin(root))
	}

	private operator fun String.div(other: String) = "$this/$other"
}
