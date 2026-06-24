package repositories.globalsettings

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokens
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.EncryptedFileAuthTokenStore
import kotlinx.serialization.json.Json
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedFileAuthTokenStoreTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var json: Json

	private val encPath: Path = "/config/auth_tokens.enc".toPath()
	private val legacyPath: Path = "/config/auth_tokens.json".toPath()

	private val url = "hammer.ink"
	private val userId = 1L
	private val tokens = AuthTokens(bearerToken = "zxc456", refreshToken = "bnm789")

	@BeforeEach
	override fun setup() {
		super.setup()
		fileSystem = FakeFileSystem()
		json = createJsonSerializer()
	}

	private fun createStore(
		userName: String = "alice",
		homeDir: String = "/home/alice",
		salt: ByteArray = "test-salt".encodeToByteArray(),
	) = EncryptedFileAuthTokenStore(
		fileSystem = fileSystem,
		json = json,
		filePath = encPath,
		legacyPlaintextPath = legacyPath,
		keyUserName = userName,
		keyHomeDir = homeDir,
		keySalt = salt,
	)

	@Test
	fun `Tokens round-trip through the encrypted store`() {
		val store = createStore()

		store.put(url, userId, tokens)

		val loaded = store.get(url, userId)
		assertEquals(tokens, loaded)
	}

	@Test
	fun `On-disk file contains no plaintext token substrings`() {
		val store = createStore()

		store.put(url, userId, tokens)

		val bytes = fileSystem.read(encPath) { readByteArray() }
		val asText = bytes.decodeToString()
		assertFalse(asText.contains("zxc456"))
		assertFalse(asText.contains("bnm789"))
		assertFalse(asText.contains("bearerToken"))
		assertFalse(asText.contains(url))
	}

	@Test
	fun `Different key inputs cannot decrypt and return null without crashing`() {
		createStore(userName = "alice", homeDir = "/home/alice").put(url, userId, tokens)

		val otherUser = createStore(userName = "mallory", homeDir = "/home/mallory")
		assertNull(otherUser.get(url, userId))

		val otherSalt = createStore(salt = "different-salt".encodeToByteArray())
		assertNull(otherSalt.get(url, userId))
	}

	@Test
	fun `Keying by url and userId isolates accounts`() {
		val store = createStore()
		store.put(url, userId, tokens)

		assertEquals(tokens, store.get(url, userId))
		assertNull(store.get("other.example.com", userId))
		assertNull(store.get(url, 999L))
	}

	@Test
	fun `Remove clears a single account`() {
		val store = createStore()
		store.put(url, userId, tokens)
		store.put("other.example.com", 2L, AuthTokens("a", "b"))

		store.remove(url, userId)

		assertNull(store.get(url, userId))
		assertEquals(AuthTokens("a", "b"), store.get("other.example.com", 2L))
	}

	@Test
	fun `Legacy plaintext file is imported then deleted`() {
		fileSystem.createDirectories(legacyPath.parent!!)
		val legacyMap = mapOf("$url|$userId" to tokens)
		fileSystem.writeJson(legacyPath, json, legacyMap)
		assertTrue(fileSystem.exists(legacyPath))

		val store = createStore()
		val loaded = store.get(url, userId)

		assertEquals(tokens, loaded)
		assertFalse(fileSystem.exists(legacyPath))
		assertTrue(fileSystem.exists(encPath))

		// Re-reading the encrypted file confirms the imported tokens persisted.
		assertFalse(fileSystem.read(encPath) { readByteArray() }.decodeToString().contains("zxc456"))
	}

	@Test
	fun `Existing encrypted tokens win over a stale legacy plaintext entry`() {
		val fresh = AuthTokens(bearerToken = "fresh-bearer", refreshToken = "fresh-refresh")
		createStore().put(url, userId, fresh)

		fileSystem.createDirectories(legacyPath.parent!!)
		val staleMap = mapOf("$url|$userId" to AuthTokens("stale-bearer", "stale-refresh"))
		fileSystem.writeJson(legacyPath, json, staleMap)

		// A fresh instance triggers migration on first access.
		val migrated = createStore().get(url, userId)

		assertEquals(fresh, migrated)
		assertFalse(fileSystem.exists(legacyPath))
	}

	@Test
	fun `Missing file yields no tokens`() {
		val store = createStore()
		assertNull(store.get(url, userId))
	}
}
