package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncryptionConvergenceTest : BaseTest() {

	private lateinit var testDatabase: SqliteTestDatabase
	private lateinit var base64: Base64
	private lateinit var secureRandom: SecureRandom
	private lateinit var keyProvider: SimpleFileBasedAesGcmKeyProvider

	private lateinit var aesV1: AesGcmContentEncryptor
	private lateinit var aesV2: AesGcmContentEncryptor
	private lateinit var plaintext: PlaintextContentEncryptor
	private lateinit var registry: ContentEncryptorRegistry
	private lateinit var convergence: EncryptionConvergence

	private val userId = 1L
	private val cipherSecret = Base64.encode(ByteArray(32) { (it * 7).toByte() })

	@BeforeEach
	override fun setup() {
		super.setup()
		base64 = Base64.Default
		secureRandom = SecureRandom()
		setupKoin()

		testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

		keyProvider = SimpleFileBasedAesGcmKeyProvider(base64)
		aesV1 = AesGcmContentEncryptor("content-key-1", "v1", keyProvider, secureRandom)
		aesV2 = AesGcmContentEncryptor("content-key-2", "v2", keyProvider, secureRandom)
		plaintext = PlaintextContentEncryptor()
		registry = ContentEncryptorRegistry(listOf(aesV1, aesV2, plaintext))
		convergence = EncryptionConvergence(testDatabase, AccountDao(testDatabase), registry)

		testDatabase.serverDatabase.accountQueries.createAccount(
			email = "t@t.com", password_hash = "h", cipher_secret = cipherSecret, is_admin = false,
		)
	}

	private fun db() = testDatabase.serverDatabase

	private suspend fun insertEntity(id: Long, plain: String, enc: ContentEncryptor, tag: String? = null) {
		val content = enc.encrypt(plain, cipherSecret)
		db().storyEntityQueries.insertNew(
			userId = userId, projectId = 1, id = id, type = "scene",
			content = content, hash = "hash-$id", cipher = tag ?: enc.cipherName(),
		)
	}

	private fun insertPlaintextNullCipher(id: Long, plain: String) {
		db().storyEntityQueries.insertNew(
			userId = userId, projectId = 1, id = id, type = "scene",
			content = plain, hash = "hash-$id", cipher = null,
		)
	}

	private fun entityRow(id: Long) = db().storyEntityQueries.getEntity(userId, 1, id).executeAsOne()

	private suspend fun decryptEntity(id: Long): String {
		val row = entityRow(id)
		return registry.resolve(row.cipher).decrypt(row.content, cipherSecret)
	}

	private suspend fun insertReviewScene(plain: String, enc: ContentEncryptor): Long {
		db().reviewRequestQueries.createRequest(
			userId = userId, projectId = 1, token = "tok-$plain", reviewerEmail = "r@r.com",
			label = "l", note = null, status = "sent", expires = null,
		)
		val reqId = db().reviewRequestQueries.getRequestByToken("tok-$plain").executeAsOne().id
		db().reviewSceneQueries.createScene(
			reviewRequestId = reqId, sceneId = 1, draftId = 1, sceneName = "s", sceneOrder = 0,
			snapshotContent = enc.encrypt(plain, cipherSecret), cipher = enc.cipherName(),
		)
		return db().reviewSceneQueries.getScenesForRequest(reqId).executeAsOne().id
	}

	@Test
	fun `enable encryption converts plaintext rows to the active key`() = runTest {
		insertPlaintextNullCipher(1, "alpha")
		insertEntity(2, "bravo", plaintext)

		assertEquals(2, convergence.remaining("aesgcm:v1"))
		val report = convergence.converge(aesV1)

		assertEquals(2, report.storyEntities)
		assertEquals(0, convergence.remaining("aesgcm:v1"))
		assertEquals("aesgcm:v1", entityRow(1).cipher)
		assertEquals("aesgcm:v1", entityRow(2).cipher)
		assertEquals("alpha", decryptEntity(1))
		assertEquals("bravo", decryptEntity(2))
	}

	@Test
	fun `disable encryption converts aes rows to plaintext`() = runTest {
		insertEntity(1, "secret-text", aesV1)

		val report = convergence.converge(plaintext)

		assertEquals(1, report.storyEntities)
		assertEquals("none", entityRow(1).cipher)
		assertEquals("secret-text", entityRow(1).content)
		assertEquals(0, convergence.remaining("none"))
	}

	@Test
	fun `rotate re-encrypts onto the new key`() = runTest {
		insertEntity(1, "rotate-me", aesV1)

		convergence.converge(aesV2)

		assertEquals("aesgcm:v2", entityRow(1).cipher)
		assertEquals("rotate-me", decryptEntity(1))
		assertEquals(0, convergence.remaining("aesgcm:v2"))
	}

	@Test
	fun `legacy tagged rows need no convergence to v1`() = runTest {
		insertEntity(1, "legacy", aesV1, tag = "AES/GCM/NoPadding")

		assertEquals(0, convergence.remaining("aesgcm:v1"))
		val report = convergence.converge(aesV1)

		assertEquals(0, report.storyEntities)
		assertEquals("AES/GCM/NoPadding", entityRow(1).cipher)
		assertEquals("legacy", decryptEntity(1))
	}

	@Test
	fun `converges review scene snapshots too`() = runTest {
		val sceneId = insertReviewScene("review-secret", aesV1)

		val report = convergence.converge(plaintext)

		assertEquals(1, report.reviewScenes)
		val scene = db().reviewSceneQueries.getScene(sceneId).executeAsOne()
		assertEquals("none", scene.cipher)
		assertEquals("review-secret", scene.snapshot_content)
	}

	@Test
	fun `a second run is a no-op`() = runTest {
		insertPlaintextNullCipher(1, "alpha")
		assertEquals(1, convergence.converge(aesV1).storyEntities)
		assertEquals(0, convergence.converge(aesV1).total)
	}

	@Test
	fun `an over-cap row fails the convergence`() = runTest {
		val tinyCap = EncryptionConvergence(testDatabase, AccountDao(testDatabase), registry, maxContentLength = 16)
		insertPlaintextNullCipher(1, "this plaintext is well over sixteen bytes once encrypted")

		assertFailsWith<EncryptionConvergenceException> { tinyCap.converge(aesV1) }

		// The failing row is left untouched, still readable as its original plaintext.
		assertEquals(null, entityRow(1).cipher)
		assertEquals("this plaintext is well over sixteen bytes once encrypted", entityRow(1).content)
	}

	@Test
	fun `a failure mid-convergence loses nothing and re-runs cleanly`() = runTest {
		insertPlaintextNullCipher(1, "one")
		insertPlaintextNullCipher(2, "two")
		insertPlaintextNullCipher(3, "three")

		val failing = FailOnNthEncrypt(aesV1, failOn = 2)
		assertFailsWith<RuntimeException> { convergence.converge(failing) }

		// Exactly one row committed before the failure; the rest keep their readable original.
		val converged = listOf(1L, 2L, 3L).count { entityRow(it).cipher == "aesgcm:v1" }
		assertEquals(1, converged)
		assertEquals(listOf("one", "two", "three"), listOf(1L, 2L, 3L).map { decryptEntity(it) })

		// Re-running with a working target finishes the job.
		convergence.converge(aesV1)
		assertEquals(0, convergence.remaining("aesgcm:v1"))
		assertEquals(listOf("one", "two", "three"), listOf(1L, 2L, 3L).map { decryptEntity(it) })
	}

	@Test
	fun `dry run reports off-target rows and writes nothing`() = runTest {
		insertPlaintextNullCipher(1, "a")
		insertEntity(2, "b", aesV1)

		val report = convergence.dryRun(aesV1)

		assertEquals(1, report.storyEntities)
		assertEquals(true, report.overCapEntities.isEmpty())
		// Untouched.
		assertEquals(null, entityRow(1).cipher)
	}

	@Test
	fun `dry run flags over-cap entities without writing`() = runTest {
		val tinyCap = EncryptionConvergence(testDatabase, AccountDao(testDatabase), registry, maxContentLength = 16)
		insertPlaintextNullCipher(1, "definitely longer than sixteen bytes once encrypted")

		val report = tinyCap.dryRun(aesV1)

		assertEquals(1, report.overCapEntities.size)
		assertEquals(null, entityRow(1).cipher)
	}

	@Test
	fun `remaining counts only rows not on target`() = runTest {
		insertEntity(1, "a", aesV1)
		insertEntity(2, "b", plaintext)
		insertPlaintextNullCipher(3, "c")

		// Target aesgcm:v1: the plaintext rows (2 & 3) need converging, the v1 row does not.
		assertEquals(2, convergence.remaining("aesgcm:v1"))
		// Target none: only the v1 row (1) needs converging.
		assertEquals(1, convergence.remaining("none"))
	}

	@Test
	fun `every convergence query folds the legacy tag to aesgcm v1 identically`() = runTest {
		insertEntity(1, "legacy", aesV1, tag = "AES/GCM/NoPadding")
		val story = db().storyEntityQueries

		// All four story queries that normalize the cipher must agree the legacy
		// row is aesgcm:v1 — if any one drifts, prune could drop a key in use.
		assertEquals(listOf("aesgcm:v1"), story.distinctCiphers().executeAsList().filterNotNull())
		assertEquals(0, story.countForConvergence("aesgcm:v1").executeAsOne())
		assertEquals(0, story.selectForConvergence("aesgcm:v1", 100).executeAsList().size)
		assertEquals(0, story.selectForConvergencePaged("aesgcm:v1", 100, 0).executeAsList().size)
		assertEquals(1, story.countForConvergence("none").executeAsOne())

		db().reviewRequestQueries.createRequest(
			userId = userId, projectId = 1, token = "tok-legacy", reviewerEmail = "r@r.com",
			label = "l", note = null, status = "sent", expires = null,
		)
		val reqId = db().reviewRequestQueries.getRequestByToken("tok-legacy").executeAsOne().id
		db().reviewSceneQueries.createScene(
			reviewRequestId = reqId, sceneId = 1, draftId = 1, sceneName = "s", sceneOrder = 0,
			snapshotContent = aesV1.encrypt("legacy", cipherSecret), cipher = "AES/GCM/NoPadding",
		)
		val review = db().reviewSceneQueries

		assertEquals(listOf("aesgcm:v1"), review.distinctCiphers().executeAsList().filterNotNull())
		assertEquals(0, review.countForConvergence("aesgcm:v1").executeAsOne())
		assertEquals(0, review.selectForConvergence("aesgcm:v1", 100).executeAsList().size)
		assertEquals(1, review.countForConvergence("none").executeAsOne())
	}

	@Test
	fun `distinctCiphers normalizes legacy tags and resolves the in-use content key ids`() = runTest {
		insertEntity(1, "a", aesV1)
		insertEntity(2, "b", aesV2)
		insertEntity(3, "c", aesV1, tag = "AES/GCM/NoPadding")
		insertPlaintextNullCipher(4, "d")
		insertReviewScene("e", aesV2)

		val tags = db().storyEntityQueries.distinctCiphers().executeAsList() +
			db().reviewSceneQueries.distinctCiphers().executeAsList()
		val inUseKeyIds = tags.filterNotNull().mapNotNull { AesGcmContentEncryptor.keyIdForTag(it) }.toSet()

		// Legacy 'AES/GCM/NoPadding' folds into v1; plaintext (none) maps to no key id.
		assertEquals(setOf("v1", "v2"), inUseKeyIds)
	}

	/** Delegates to a real encryptor but throws on the Nth encrypt, to simulate a mid-run crash. */
	private class FailOnNthEncrypt(
		private val delegate: ContentEncryptor,
		private val failOn: Int,
	) : ContentEncryptor {
		private var calls = 0
		override suspend fun encrypt(plainText: String, clientSecret: String): String {
			if (++calls == failOn) throw RuntimeException("injected convergence failure")
			return delegate.encrypt(plainText, clientSecret)
		}

		override suspend fun decrypt(encrypted: String, clientSecret: String): String =
			delegate.decrypt(encrypted, clientSecret)

		override fun cipherName(): String = delegate.cipherName()
	}
}
