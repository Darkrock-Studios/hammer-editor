package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncryptionBootstrapTest : BaseTest() {

	private lateinit var testDatabase: SqliteTestDatabase
	private lateinit var configDao: ServerConfigDao
	private lateinit var convergence: EncryptionConvergence
	private lateinit var aesV1: AesGcmContentEncryptor
	private lateinit var plaintext: PlaintextContentEncryptor

	private val userId = 1L
	private val cipherSecret = Base64.encode(ByteArray(32) { it.toByte() })

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

		val keyProvider = SimpleFileBasedAesGcmKeyProvider(Base64.Default)
		aesV1 = AesGcmContentEncryptor("content-key-1", "v1", keyProvider, SecureRandom())
		plaintext = PlaintextContentEncryptor()
		val registry = ContentEncryptorRegistry(listOf(aesV1, plaintext))
		configDao = ServerConfigDao(testDatabase)
		convergence = EncryptionConvergence(testDatabase, AccountDao(testDatabase), registry)

		testDatabase.serverDatabase.accountQueries.createAccount(
			email = "t@t.com", password_hash = "h", cipher_secret = cipherSecret, is_admin = false,
		)
	}

	private fun db() = testDatabase.serverDatabase

	private fun bootstrap(active: ContentEncryptor) =
		EncryptionBootstrap(active, convergence, configDao, testDatabase)

	private suspend fun insertEntity(id: Long, plain: String, enc: ContentEncryptor) {
		db().storyEntityQueries.insertNew(
			userId = userId, projectId = 1, id = id, type = "scene",
			content = enc.encrypt(plain, cipherSecret), hash = "h$id", cipher = enc.cipherName(),
		)
	}

	private fun entityRow(id: Long) = db().storyEntityQueries.getEntity(userId, 1, id).executeAsOne()

	private suspend fun marker() = configDao.getConfig(EncryptionBootstrap.LAST_APPLIED_KEY)

	@Test
	fun `unspecified mode with encrypted data hard-stops`() = runTest {
		insertEntity(1, "secret", aesV1)
		assertFailsWith<UnspecifiedEncryptionModeException> {
			bootstrap(plaintext).run(mode = null)
		}
	}

	@Test
	fun `unspecified mode on a fresh database boots and records plaintext`() = runTest {
		bootstrap(plaintext).run(mode = null)
		assertEquals("none", marker())
	}

	@Test
	fun `explicit none converges aes data to plaintext`() = runTest {
		insertEntity(1, "secret", aesV1)

		bootstrap(plaintext).run(mode = EncryptionMode.NONE)

		assertEquals("none", entityRow(1).cipher)
		assertEquals("secret", entityRow(1).content)
		assertEquals("none", marker())
	}

	@Test
	fun `explicit aes converges plaintext data to the active key`() = runTest {
		insertEntity(1, "open", plaintext)

		bootstrap(aesV1).run(mode = EncryptionMode.AES)

		assertEquals("aesgcm:v1", entityRow(1).cipher)
		assertEquals("aesgcm:v1", marker())
	}

	@Test
	fun `a matching last-applied marker skips the scan`() = runTest {
		// Marker already at the target; a stray non-target row must be left untouched.
		configDao.upsertConfig(EncryptionBootstrap.LAST_APPLIED_KEY, "none")
		insertEntity(1, "still-encrypted", aesV1)

		bootstrap(plaintext).run(mode = EncryptionMode.NONE)

		assertEquals("aesgcm:v1", entityRow(1).cipher)
	}
}
