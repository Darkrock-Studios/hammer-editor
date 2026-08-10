package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class WhitelistExpiryJobTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var whiteListDao: WhiteListDao
	private lateinit var accountDao: AccountDao
	private lateinit var authTokenDao: AuthTokenDao
	private lateinit var whiteListRepository: WhiteListRepository
	private lateinit var accountsRepository: AccountsRepository
	private lateinit var clock: TestClock
	private val logger = LoggerFactory.getLogger(WhitelistExpiryJobTest::class.java)

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		whiteListDao = WhiteListDao(db)
		accountDao = AccountDao(db)
		authTokenDao = AuthTokenDao(db)
		clock = TestClock(Clock.System)
		whiteListRepository = WhiteListRepository(whiteListDao, clock)
		accountsRepository = AccountsRepository(
			accountDao = accountDao,
			authTokenDao = authTokenDao,
			clock = clock,
			// The force-logout path never hashes a token.
			tokenHasher = mockk<TokenHasher>(),
			base64 = createTokenBase64(),
		)

		setupKoin()
	}

	private fun createJob() = WhitelistExpiryJob(whiteListRepository, accountsRepository, logger)

	/** An account with one live session, so a force-logout has something to revoke. */
	private suspend fun seedAccountWithSession(email: String, installId: String): Long {
		val userId = accountDao.createAccount(email, "hash", "cipher-secret", isAdmin = false)
		authTokenDao.setToken(
			userId = userId,
			installId = installId,
			token = Token(userId = userId, auth = "auth-$installId", refresh = "refresh-$installId"),
			expires = clock.now() + 30.days,
		)
		return userId
	}

	@Test
	fun `tick - deletes expired entry and revokes its sessions`() = runTest {
		val installId = "install-lapsed"
		val userId = seedAccountWithSession("lapsed@example.com", installId)
		whiteListRepository.addToWhiteList("lapsed@example.com", "Beta tester", expires = clock.now() + 7.days)

		clock.advanceTime(8.days)
		createJob().tick()

		assertEquals(emptyList(), whiteListRepository.getWhiteList(), "Expired entry should be reaped")
		assertNull(
			authTokenDao.getTokenByInstallId(userId, installId),
			"The lapsed user's session should be revoked",
		)
	}

	@Test
	fun `tick - leaves unexpired and never-expiring entries alone`() = runTest {
		val installId = "install-current"
		val userId = seedAccountWithSession("current@example.com", installId)
		whiteListRepository.addToWhiteList("current@example.com", "Beta tester", expires = clock.now() + 30.days)
		whiteListRepository.addToWhiteList("forever@example.com", "Friend", expires = null)

		clock.advanceTime(1.days)
		createJob().tick()

		assertEquals(
			listOf("current@example.com", "forever@example.com"),
			whiteListRepository.getWhiteList(),
		)
		assertNotNull(
			authTokenDao.getTokenByInstallId(userId, installId),
			"A user whose entry has not expired must stay logged in",
		)
	}

	@Test
	fun `tick - reaps an expired entry that has no account`() = runTest {
		whiteListRepository.addToWhiteList("never-signed-up@example.com", "Beta tester", expires = clock.now() + 1.days)

		clock.advanceTime(2.days)
		createJob().tick()

		assertEquals(emptyList(), whiteListRepository.getWhiteList())
	}

	@Test
	fun `tick - expired entry stops authorizing even before it is reaped`() = runTest {
		whiteListRepository.addToWhiteList("lapsed@example.com", "Beta tester", expires = clock.now() + 1.days)

		clock.advanceTime(2.days)

		assertFalse(
			whiteListRepository.isOnWhiteList("lapsed@example.com"),
			"Enforcement must not depend on the job having run",
		)
	}
}
