package com.darkrockstudios.apps.hammer.account.mfa

import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.secret.RoleKeys
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MfaRepositoryTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var fixture: MfaTestFixture
	private val repository get() = fixture.repository
	private val totpDao get() = fixture.totpDao
	private val challengeDao get() = fixture.challengeDao
	private val tokenHasher get() = fixture.tokenHasher
	private val aes get() = fixture.encryptor
	private var now: Instant
		get() = fixture.now
		set(value) {
			fixture.now = value
		}

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		db = SharedPostgresTestDatabase()
		db.initialize()
		fixture = MfaTestFixture(db)
	}

	private suspend fun newAccount(email: String = "mfa-${Random.nextLong()}@example.com"): Long =
		fixture.newAccount(email)

	private fun codeAt(secret: String, at: Instant = now): String = fixture.codeAt(secret, at)

	private suspend fun enrolled(): Pair<Long, MfaTestFixture.Enrolled> {
		val userId = newAccount()
		return userId to fixture.enroll(userId)
	}

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

		assertEquals(CodeResult.Verified, repository.verifyCode(userId, code))
		assertEquals(CodeResult.Invalid, repository.verifyCode(userId, code))
	}

	@Test
	fun `The confirm code cannot be replayed`() = runTest {
		val userId = newAccount()
		val enrollment = assertNotNull(repository.beginTotp(userId))
		val code = codeAt(enrollment.secret)
		assertNotNull(repository.confirmTotp(userId, code))

		assertEquals(CodeResult.Invalid, repository.verifyCode(userId, code))
	}

	@Test
	fun `Recovery codes are single use and accept loose formatting`() = runTest {
		val (userId, enrolled) = enrolled()
		val code = enrolled.recoveryCodes.first()

		assertEquals(CodeResult.Verified, repository.verifyCode(userId, " ${code.uppercase().replace("-", "")} "))
		assertEquals(CodeResult.Invalid, repository.verifyCode(userId, code))
		assertEquals(CodeResult.Verified, repository.verifyCode(userId, enrolled.recoveryCodes[1]))
	}

	@Test
	fun `Recovery codes survive a token HMAC key rotation`() = runTest {
		val (userId, enrolled) = enrolled()
		val rotated = MfaTestFixture(
			db,
			now,
			RoleKeys("v2", mapOf("v1" to "token-key", "v2" to "token-key-2")),
		)

		assertEquals(CodeResult.Verified, rotated.repository.verifyCode(userId, enrolled.recoveryCodes.first()))
		assertEquals(CodeResult.Invalid, rotated.repository.verifyCode(userId, enrolled.recoveryCodes.first()))
	}

	@Test
	fun `Regenerating recovery codes invalidates the old set`() = runTest {
		val (userId, enrolled) = enrolled()

		val fresh = repository.regenerateRecoveryCodes(userId)

		assertEquals(CodeResult.Invalid, repository.verifyCode(userId, enrolled.recoveryCodes.first()))
		assertEquals(CodeResult.Verified, repository.verifyCode(userId, fresh.first()))
	}

	@Test
	fun `Codes do not verify for an account without MFA`() = runTest {
		val userId = newAccount()

		assertEquals(CodeResult.Invalid, repository.verifyCode(userId, "123456"))
	}

	@Test
	fun `Challenge verifies with a TOTP code and is consumed`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")
		assertEquals(listOf(MfaMethod.TOTP, MfaMethod.RECOVERY), challenge.methods)

		val result = repository.verifyChallenge(challenge.token, "install-a", codeAt(enrolled.secret))

		assertEquals(ChallengeResult.Verified(userId), result)
		now += Totp.STEP_SECONDS.seconds
		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, "install-a", codeAt(enrolled.secret)))
	}

	@Test
	fun `Challenge accepts a recovery code`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		val result = repository.verifyChallenge(challenge.token, "install-a", enrolled.recoveryCodes.first())

		assertEquals(ChallengeResult.Verified(userId), result)
	}

	@Test
	fun `Wrong code keeps the challenge open`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		assertEquals(ChallengeResult.InvalidCode(userId), repository.verifyChallenge(challenge.token, "install-a", codeAt(enrolled.secret, now + 5.minutes)))
		assertIs<ChallengeResult.Verified>(repository.verifyChallenge(challenge.token, "install-a", codeAt(enrolled.secret)))
	}

	@Test
	fun `Challenge expires after five minutes`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		now += 5.minutes + 1.seconds

		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, "install-a", codeAt(enrolled.secret)))
	}

	@Test
	fun `Challenge closes after five attempts`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		repeat(MfaRepository.MAX_CHALLENGE_ATTEMPTS) {
			assertEquals(ChallengeResult.InvalidCode(userId), repository.verifyChallenge(challenge.token, "install-a", "bad"))
		}

		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, "install-a", codeAt(enrolled.secret)))
	}

	@Test
	fun `Challenge only answers for the install it was issued to`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, "install-b", codeAt(enrolled.secret)))
		assertIs<ChallengeResult.Verified>(repository.verifyChallenge(challenge.token, "install-a", codeAt(enrolled.secret)))
	}

	@Test
	fun `Unknown challenge token is invalid`() = runTest {
		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge("nope-${Random.nextLong()}", "install-a", "123456"))
	}

	@Test
	fun `Purge removes expired challenges only`() = runTest {
		val (userId, _) = enrolled()
		val old = repository.createChallenge(userId, "install-a")
		val createdAt = now
		now += 10.minutes
		val fresh = repository.createChallenge(userId, "install-b")

		repository.purgeExpiredChallenges()

		assertNull(challengeDao.claimAttempt(tokenHasher.hashToken(old.token), "install-a", createdAt, maxAttempts = 5))
		assertNotNull(challengeDao.claimAttempt(tokenHasher.hashToken(fresh.token), "install-b", now, maxAttempts = 5))
	}

	@Test
	fun `Disable removes TOTP and recovery codes`() = runTest {
		val (userId, enrolled) = enrolled()

		repository.disableTotp(userId)

		assertFalse(repository.hasMfa(userId))
		assertEquals(CodeResult.Invalid, repository.verifyCode(userId, enrolled.recoveryCodes.first()))
		assertNotNull(repository.beginTotp(userId))
	}

	@Test
	fun `Reset clears every factor and open challenges`() = runTest {
		val (userId, enrolled) = enrolled()
		val challenge = repository.createChallenge(userId, "install-a")

		repository.resetAll(userId)

		assertFalse(repository.hasMfa(userId))
		assertEquals(CodeResult.Invalid, repository.verifyCode(userId, enrolled.recoveryCodes.first()))
		assertEquals(ChallengeResult.Invalid, repository.verifyChallenge(challenge.token, "install-a", codeAt(enrolled.secret)))
	}

	private suspend fun failCodes(userId: Long, secret: String, times: Int) {
		val wrong = codeAt(secret, now + 5.minutes)
		repeat(times) { assertEquals(CodeResult.Invalid, repository.verifyCode(userId, wrong)) }
	}

	private suspend fun lockOut(userId: Long, secret: String) {
		failCodes(userId, secret, MfaRepository.MAX_FAILED_CODES - 1)
		assertEquals(CodeResult.Locked(now + MfaRepository.LOCKOUT), repository.verifyCode(userId, "000000"))
	}

	@Test
	fun `The tenth wrong code in a window locks the account for fifteen minutes`() = runTest {
		val (userId, enrolled) = enrolled()
		failCodes(userId, enrolled.secret, MfaRepository.MAX_FAILED_CODES - 1)

		val locked = repository.verifyCode(userId, codeAt(enrolled.secret, now + 5.minutes))

		assertEquals(CodeResult.Locked(now + MfaRepository.LOCKOUT), locked)
		assertEquals(CodeResult.Locked(now + MfaRepository.LOCKOUT), repository.verifyCode(userId, codeAt(enrolled.secret)))
		assertEquals(CodeResult.Locked(now + MfaRepository.LOCKOUT), repository.verifyCode(userId, enrolled.recoveryCodes.first()))
	}

	@Test
	fun `The lock lifts after fifteen minutes`() = runTest {
		val (userId, enrolled) = enrolled()
		lockOut(userId, enrolled.secret)

		now += MfaRepository.LOCKOUT + 1.seconds

		assertEquals(CodeResult.Verified, repository.verifyCode(userId, codeAt(enrolled.secret)))
	}

	@Test
	fun `Failures outside the window do not count toward the lock`() = runTest {
		val (userId, enrolled) = enrolled()
		failCodes(userId, enrolled.secret, MfaRepository.MAX_FAILED_CODES - 1)

		now += MfaRepository.FAILURE_WINDOW + 1.seconds
		failCodes(userId, enrolled.secret, MfaRepository.MAX_FAILED_CODES - 1)

		assertEquals(CodeResult.Verified, repository.verifyCode(userId, codeAt(enrolled.secret)))
	}

	@Test
	fun `A correct code clears the failure count`() = runTest {
		val (userId, enrolled) = enrolled()
		failCodes(userId, enrolled.secret, MfaRepository.MAX_FAILED_CODES - 1)
		assertEquals(CodeResult.Verified, repository.verifyCode(userId, codeAt(enrolled.secret)))
		now += Totp.STEP_SECONDS.seconds

		failCodes(userId, enrolled.secret, MfaRepository.MAX_FAILED_CODES - 1)

		assertEquals(CodeResult.Verified, repository.verifyCode(userId, codeAt(enrolled.secret)))
	}

	@Test
	fun `Wrong recovery codes count toward the lock`() = runTest {
		val (userId, enrolled) = enrolled()

		repeat(MfaRepository.MAX_FAILED_CODES - 1) {
			assertEquals(CodeResult.Invalid, repository.verifyCode(userId, "aaaaa-aaaaa"))
		}

		assertEquals(CodeResult.Locked(now + MfaRepository.LOCKOUT), repository.verifyCode(userId, "aaaaa-aaaaa"))
		assertEquals(CodeResult.Locked(now + MfaRepository.LOCKOUT), repository.verifyCode(userId, enrolled.recoveryCodes.first()))
	}

	@Test
	fun `Challenges report the lock across every challenge for the account`() = runTest {
		val (userId, enrolled) = enrolled()
		failCodes(userId, enrolled.secret, MfaRepository.MAX_FAILED_CODES - 1)
		val first = repository.createChallenge(userId, "install-a")

		val locked = repository.verifyChallenge(first.token, "install-a", "bad")

		assertEquals(ChallengeResult.Locked(userId, now + MfaRepository.LOCKOUT), locked)
		val second = repository.createChallenge(userId, "install-b")
		assertEquals(
			ChallengeResult.Locked(userId, now + MfaRepository.LOCKOUT),
			repository.verifyChallenge(second.token, "install-b", codeAt(enrolled.secret)),
		)
	}

	@Test
	fun `An account can be locked again after the lock lapses`() = runTest {
		val (userId, enrolled) = enrolled()
		lockOut(userId, enrolled.secret)
		now += MfaRepository.LOCKOUT + 1.seconds

		lockOut(userId, enrolled.secret)
	}

	@Test
	fun `A replayed code is refused but does not count toward the lock`() = runTest {
		val (userId, enrolled) = enrolled()
		val code = codeAt(enrolled.secret)
		assertEquals(CodeResult.Verified, repository.verifyCode(userId, code))
		assertEquals(CodeResult.Invalid, repository.verifyCode(userId, code))

		failCodes(userId, enrolled.secret, MfaRepository.MAX_FAILED_CODES - 1)

		assertNull(repository.lockedUntil(userId))
		assertEquals(CodeResult.Locked(now + MfaRepository.LOCKOUT), repository.verifyCode(userId, "000000"))
	}

	@Test
	fun `Locked until reports an active lock only`() = runTest {
		val (userId, enrolled) = enrolled()
		assertNull(repository.lockedUntil(userId))
		lockOut(userId, enrolled.secret)

		assertEquals(now + MfaRepository.LOCKOUT, repository.lockedUntil(userId))
		now += MfaRepository.LOCKOUT + 1.seconds
		assertNull(repository.lockedUntil(userId))
	}

	@Test
	fun `Reset lifts an active lock`() = runTest {
		val (userId, enrolled) = enrolled()
		lockOut(userId, enrolled.secret)

		repository.resetAll(userId)
		val again = fixture.enroll(userId)

		assertNull(repository.lockedUntil(userId))
		assertEquals(CodeResult.Verified, repository.verifyCode(userId, codeAt(again.secret)))
	}
}
