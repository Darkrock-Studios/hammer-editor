package com.darkrockstudios.apps.hammer.account.mfa

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AccountRecoveryCodeDao
import com.darkrockstudios.apps.hammer.database.AccountTotpDao
import com.darkrockstudios.apps.hammer.database.MfaLoginChallengeDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.encryption.PlaintextContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.secret.Keyring
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.RoleKeys
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MfaRepositoryTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var totpDao: AccountTotpDao
	private lateinit var challengeDao: MfaLoginChallengeDao
	private lateinit var repository: MfaRepository
	private lateinit var aes: AesGcmContentEncryptor
	private lateinit var tokenHasher: TokenHasher

	private var now: Instant = Clock.System.now()
	private val clock = object : Clock {
		override fun now() = now
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		db = SharedPostgresTestDatabase()
		db.initialize()

		val random = SecureRandom()
		val base64 = Base64.Default
		val codec = KeyringCodec(random, base64)
		val keyring = codec.serialize(
			Keyring(
				content = RoleKeys("v1", mapOf("v1" to "content-key")),
				tokenHmac = RoleKeys("v1", mapOf("v1" to "token-key")),
			)
		)
		val fileSystem = FakeFileSystem()
		val keyringManager = KeyringManager(
			object : ServerSecretProvider {
				override fun loadKeyring() = keyring
			},
			codec, fileSystem, "/none".toPath(),
		)
		tokenHasher = TokenHasher(keyringManager, ServerSecretManager(fileSystem, random), base64)
		aes = AesGcmContentEncryptor("content-key", "v1", SimpleFileBasedAesGcmKeyProvider(base64), random)

		accountDao = AccountDao(db)
		totpDao = AccountTotpDao(db)
		challengeDao = MfaLoginChallengeDao(db)
		repository = MfaRepository(
			accountDao = accountDao,
			totpDao = totpDao,
			recoveryCodeDao = AccountRecoveryCodeDao(db),
			challengeDao = challengeDao,
			encryptor = aes,
			encryptorRegistry = ContentEncryptorRegistry(listOf(aes, PlaintextContentEncryptor())),
			tokenHasher = tokenHasher,
			serverConfig = ServerConfig(host = "write.example.com"),
			random = random,
			base64 = base64,
			clock = clock,
		)
	}

	private suspend fun newAccount(email: String = "mfa-${Random.nextLong()}@example.com"): Long =
		accountDao.createAccount(email, "hash", Base64.encode(Random.nextBytes(32)), isAdmin = false)

	private fun codeAt(secret: String, at: Instant = now): String =
		Totp.code(Totp.base32Decode(secret), Totp.stepAt(at.epochSeconds))

	private suspend fun enrolled(): Pair<Long, Enrolled> {
		val userId = newAccount()
		val enrollment = assertNotNull(repository.beginTotp(userId))
		val codes = assertNotNull(repository.confirmTotp(userId, codeAt(enrollment.secret)))
		// Moves past the step the confirm code used, so the next code is fresh.
		now += Totp.STEP_SECONDS.seconds
		return userId to Enrolled(enrollment.secret, codes)
	}

	private data class Enrolled(val secret: String, val recoveryCodes: List<String>)

	@Test
	fun `Begin returns a Base32 secret and an otpauth URI`() = runTest {
		val email = "begin-${Random.nextLong()}@example.com"
		val userId = newAccount(email)

		val enrollment = assertNotNull(repository.beginTotp(userId))

		assertEquals(Totp.SECRET_BYTES, Totp.base32Decode(enrollment.secret).size)
		assertTrue(enrollment.otpauthUri.startsWith("otpauth://totp/"))
		assertTrue(enrollment.otpauthUri.contains("secret=${enrollment.secret}"))
		assertTrue(enrollment.otpauthUri.contains("write.example.com"))
		assertFalse(repository.hasMfa(userId))
	}

	@Test
	fun `Secret is encrypted at rest with its cipher tag`() = runTest {
		val userId = newAccount()
		val enrollment = assertNotNull(repository.beginTotp(userId))

		val row = assertNotNull(totpDao.get(userId))
		assertEquals(aes.cipherName(), row.cipher)
		assertNotEquals(enrollment.secret, row.secret)
	}

	@Test
	fun `Confirm with a wrong code leaves MFA off`() = runTest {
		val userId = newAccount()
		val enrollment = assertNotNull(repository.beginTotp(userId))
		val wrong = codeAt(enrollment.secret, now + 5.minutes)

		assertNull(repository.confirmTotp(userId, wrong))
		assertFalse(repository.hasMfa(userId))
	}

	@Test
	fun `Confirm turns MFA on and issues eight formatted recovery codes`() = runTest {
		val (userId, enrolled) = enrolled()

		assertTrue(repository.hasMfa(userId))
		assertEquals(listOf(MfaMethod.TOTP, MfaMethod.RECOVERY), repository.methods(userId))
		assertEquals(8, enrolled.recoveryCodes.size)
		assertEquals(8, enrolled.recoveryCodes.toSet().size)
		enrolled.recoveryCodes.forEach { assertTrue(Regex("[a-z0-9]{5}-[a-z0-9]{5}").matches(it)) }
	}

	@Test
	fun `Begin refuses an account that already has TOTP`() = runTest {
		val (userId, _) = enrolled()

		assertNull(repository.beginTotp(userId))
	}

	@Test
	fun `Codes verify once per step`() = runTest {
		val (userId, enrolled) = enrolled()
		val code = codeAt(enrolled.secret)

		assertTrue(repository.verifyCode(userId, code))
		assertFalse(repository.verifyCode(userId, code))
	}

	@Test
	fun `The confirm code cannot be replayed`() = runTest {
		val userId = newAccount()
		val enrollment = assertNotNull(repository.beginTotp(userId))
		val code = codeAt(enrollment.secret)
		assertNotNull(repository.confirmTotp(userId, code))

		assertFalse(repository.verifyCode(userId, code))
	}

	@Test
	fun `Recovery codes are single use and accept loose formatting`() = runTest {
		val (userId, enrolled) = enrolled()
		val code = enrolled.recoveryCodes.first()

		assertTrue(repository.verifyCode(userId, " ${code.uppercase().replace("-", "")} "))
		assertFalse(repository.verifyCode(userId, code))
		assertTrue(repository.verifyCode(userId, enrolled.recoveryCodes[1]))
	}

	@Test
	fun `Regenerating recovery codes invalidates the old set`() = runTest {
		val (userId, enrolled) = enrolled()

		val fresh = repository.regenerateRecoveryCodes(userId)

		assertFalse(repository.verifyCode(userId, enrolled.recoveryCodes.first()))
		assertTrue(repository.verifyCode(userId, fresh.first()))
	}

	@Test
	fun `Codes do not verify for an account without MFA`() = runTest {
		val userId = newAccount()

		assertFalse(repository.verifyCode(userId, "123456"))
	}

	@Test
	fun `Challenge verifies with a TOTP code and is consumed`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")
		assertEquals(listOf(MfaMethod.TOTP, MfaMethod.RECOVERY), challenge.methods)

		val result = repository.verifyChallenge(challenge.token, codeAt(enrolled.secret))

		assertEquals(ChallengeResult.Verified(userId, "install-a"), result)
		now += Totp.STEP_SECONDS.seconds
		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, codeAt(enrolled.secret)))
	}

	@Test
	fun `Challenge accepts a recovery code`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		val result = repository.verifyChallenge(challenge.token, enrolled.recoveryCodes.first())

		assertEquals(ChallengeResult.Verified(userId, "install-a"), result)
	}

	@Test
	fun `Wrong code keeps the challenge open`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		assertEquals(ChallengeResult.InvalidCode(userId), repository.verifyChallenge(challenge.token, codeAt(enrolled.secret, now + 5.minutes)))
		assertIs<ChallengeResult.Verified>(repository.verifyChallenge(challenge.token, codeAt(enrolled.secret)))
	}

	@Test
	fun `Challenge expires after five minutes`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		now += 5.minutes + 1.seconds

		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, codeAt(enrolled.secret)))
	}

	@Test
	fun `Challenge closes after five attempts`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		repeat(MfaRepository.MAX_CHALLENGE_ATTEMPTS) {
			assertEquals(ChallengeResult.InvalidCode(userId), repository.verifyChallenge(challenge.token, "bad"))
		}

		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, codeAt(enrolled.secret)))
	}

	@Test
	fun `Unknown challenge token is invalid`() = runTest {
		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge("nope-${Random.nextLong()}", "123456"))
	}

	@Test
	fun `Purge removes expired challenges only`() = runTest {
		val (userId, _) = enrolled()
		val old = repository.createChallenge(userId, "install-a")
		val createdAt = now
		now += 10.minutes
		val fresh = repository.createChallenge(userId, "install-b")

		repository.purgeExpiredChallenges()

		assertNull(challengeDao.claimAttempt(tokenHasher.hashToken(old.token), createdAt, maxAttempts = 5))
		assertNotNull(challengeDao.claimAttempt(tokenHasher.hashToken(fresh.token), now, maxAttempts = 5))
	}

	@Test
	fun `Disable removes TOTP and recovery codes`() = runTest {
		val (userId, enrolled) = enrolled()

		repository.disableTotp(userId)

		assertFalse(repository.hasMfa(userId))
		assertFalse(repository.verifyCode(userId, enrolled.recoveryCodes.first()))
		assertNotNull(repository.beginTotp(userId))
	}

	@Test
	fun `Reset clears every factor and open challenges`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		repository.resetAll(userId)

		assertFalse(repository.hasMfa(userId))
		assertFalse(repository.verifyCode(userId, enrolled.recoveryCodes.first()))
		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, codeAt(enrolled.secret)))
	}
}
