package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrivacyPolicyRepositoryTest {

	private val path = "/data/privacy.txt".toPath()

	private fun repository(fs: FakeFileSystem, privacyPath: String? = path.toString()) =
		PrivacyPolicyRepository(ServerConfig(privacyPolicy = privacyPath), fs)

	private fun FakeFileSystem.writeFile(path: Path, contents: String) {
		path.parent?.let { createDirectories(it) }
		write(path) { writeUtf8(contents) }
	}

	@Test
	fun `no configured path means no policy`() {
		val repo = repository(FakeFileSystem(), privacyPath = null)
		assertNull(repo.text())
	}

	@Test
	fun `a missing file means no policy`() {
		val repo = repository(FakeFileSystem())
		assertNull(repo.text())
	}

	@Test
	fun `a blank file means no policy`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "   \n\t ")
		val repo = repository(fs)
		assertNull(repo.text())
	}

	@Test
	fun `a populated file is served verbatim`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "We respect your privacy.")
		val repo = repository(fs)

		assertEquals("We respect your privacy.", repo.text())
	}

	@Test
	fun `edited content is re-read`() {
		val fs = FakeFileSystem()
		fs.writeFile(path, "First policy")
		val repo = repository(fs)
		assertEquals("First policy", repo.text())

		fs.delete(path)
		fs.writeFile(path, "Second policy, revised and longer")
		assertEquals("Second policy, revised and longer", repo.text())
	}
}
