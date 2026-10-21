package repositories.secrets

import com.darkrockstudios.apps.hammer.common.data.secrets.EncryptedFileSecretStore
import com.darkrockstudios.apps.hammer.common.data.secrets.MachineBoundCipher
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EncryptedFileSecretStoreTest {

	private val fileSystem = FakeFileSystem()
	private val path = "/config/secrets.enc".toPath()

	private fun store(user: String = "alice") =
		EncryptedFileSecretStore(fileSystem, path, MachineBoundCipher(user, "/home/$user", "test-salt".encodeToByteArray()))

	@Test
	fun `secrets round trip, and the file holds none in plain text`() {
		store().put("plugin/lt/apiKey", "sup3r-s3cret")

		assertEquals("sup3r-s3cret", store().get("plugin/lt/apiKey"))
		val raw = fileSystem.read(path) { readByteArray() }.decodeToString(throwOnInvalidSequence = false)
		assertFalse("sup3r-s3cret" in raw)
	}

	@Test
	fun `an empty value removes the key, and removeAll takes a prefix`() {
		val store = store()
		store.put("plugin/a/one", "1")
		store.put("plugin/a/two", "2")
		store.put("plugin/b/one", "3")

		store.put("plugin/a/one", "")
		assertNull(store.get("plugin/a/one"))

		store.removeAll("plugin/a/")
		assertNull(store.get("plugin/a/two"))
		assertEquals("3", store.get("plugin/b/one"))
	}

	@Test
	fun `another user cannot read the file`() {
		store("alice").put("k", "v")

		assertNull(store("mallory").get("k"))
	}
}
