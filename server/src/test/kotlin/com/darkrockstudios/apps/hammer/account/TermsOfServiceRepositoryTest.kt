package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TermsOfServiceRepositoryTest {

	private val path = "/data/tos.txt".toPath()

	private fun repository(fs: FakeFileSystem, tosPath: String? = path.toString()) =
		TermsOfServiceRepository(ServerConfig(termsOfService = tosPath), fs)

	private fun FakeFileSystem.writeFile(path: Path, contents: String) {
		path.parent?.let { createDirectories(it) }
		write(path) { writeUtf8(contents) }
	}

	@Test
	fun `no configured path means no terms`() {
		val repo = repository(FakeFileSystem(), tosPath = null)
		assertNull(repo.challenge())
	}

	@Test
	fun `a missing file means no terms`() {
		val repo = repository(FakeFileSystem())
		assertNull(repo.challenge())
	}

	@Test
	fun `a blank file means no terms`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "   \n\t ")
		val repo = repository(fs)
		assertNull(repo.challenge())
	}

	@Test
	fun `a populated file is served as the challenge text with a hex version`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "Be excellent to each other")
		val repo = repository(fs)

		val challenge = assertNotNull(repo.challenge())
		assertEquals("Be excellent to each other", challenge.text)
		assertEquals(64, challenge.version.length)
		assertTrue(challenge.version.all { it in "0123456789abcdef" })
	}

	@Test
	fun `the version is stable for identical content`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "Be excellent to each other")
		val repo = repository(fs)

		assertEquals(repo.challenge()?.version, repo.challenge()?.version)
	}

	@Test
	fun `different content yields a different version`() {
		val fs = FakeFileSystem()

		fs.writeFile(path, "Terms version one")
		val first = repository(fs).challenge()?.version

		fs.delete(path)
		fs.writeFile(path, "Terms version two, which is longer")
		val second = repository(fs).challenge()?.version

		assertNotNull(first)
		assertNotNull(second)
		assertTrue(first != second)
	}
}
