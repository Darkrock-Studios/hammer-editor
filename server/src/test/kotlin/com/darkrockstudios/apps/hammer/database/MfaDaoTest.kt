package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class MfaDaoTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var totpDao: AccountTotpDao
	private lateinit var recoveryDao: AccountRecoveryCodeDao
	private lateinit var challengeDao: MfaLoginChallengeDao
	private var userId = 0L

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		totpDao = AccountTotpDao(db)
		recoveryDao = AccountRecoveryCodeDao(db)
		challengeDao = MfaLoginChallengeDao(db)
		setupKoin()
	}

	private suspend fun newAccount(): Long =
		accountDao.createAccount("mfa-dao-${Random.nextLong()}@example.com", "hash", "secret", isAdmin = false)

	@Test
	fun `Pending TOTP confirms once and a confirmed secret is not replaced`() = runTest {
		userId = newAccount()
		totpDao.upsertPending(userId, "s1", "plain")
		assertTrue(totpDao.confirm(userId, 10))
		assertFalse(totpDao.confirm(userId, 11))

		totpDao.upsertPending(userId, "s2", "plain")

		val row = assertNotNull(totpDao.get(userId))
		assertEquals("s1", row.secret)
		assertTrue(row.confirmed)
		assertEquals(10L, row.last_step)
	}

	@Test
	fun `Last step only moves forward`() = runTest {
		userId = newAccount()
		totpDao.upsertPending(userId, "s", "plain")
		totpDao.confirm(userId, 10)

		assertFalse(totpDao.advanceLastStep(userId, 10))
		assertTrue(totpDao.advanceLastStep(userId, 11))
		assertFalse(totpDao.advanceLastStep(userId, 11))
	}

	@Test
	fun `Recovery codes are single use and replaced as a set`() = runTest {
		userId = newAccount()
		val now = Clock.System.now()
		recoveryDao.replaceAll(userId, listOf("a", "b"))

		assertTrue(recoveryDao.consume(userId, "a", now))
		assertFalse(recoveryDao.consume(userId, "a", now))
		assertEquals(1L, recoveryDao.countUnused(userId))

		recoveryDao.replaceAll(userId, listOf("c"))
		assertFalse(recoveryDao.consume(userId, "b", now))
		assertEquals(1L, recoveryDao.countUnused(userId))
	}

	@Test
	fun `Challenge attempts are capped and expiry is honoured`() = runTest {
		userId = newAccount()
		val now = Clock.System.now()
		val hash = "challenge-${Random.nextLong()}"
		challengeDao.create(userId, "install", hash, now + 5.minutes)

		assertEquals(1, challengeDao.claimAttempt(hash, now, maxAttempts = 2)?.attempts)
		assertEquals(2, challengeDao.claimAttempt(hash, now, maxAttempts = 2)?.attempts)
		assertNull(challengeDao.claimAttempt(hash, now, maxAttempts = 2))
		assertNull(challengeDao.claimAttempt("expired-$hash", now, maxAttempts = 5))

		val expired = "expired-${Random.nextLong()}"
		challengeDao.create(userId, "install", expired, now + 1.minutes)
		assertNull(challengeDao.claimAttempt(expired, now + 2.minutes, maxAttempts = 5))
	}
}
