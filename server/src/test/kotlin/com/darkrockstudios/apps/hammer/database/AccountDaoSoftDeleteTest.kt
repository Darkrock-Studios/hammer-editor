package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class AccountDaoSoftDeleteTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var authTokenDao: AuthTokenDao

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		authTokenDao = AuthTokenDao(db)

		setupKoin()
	}

	@Test
	fun `markDeleted - flags a regular account`() = runTest {
		val userId = accountDao.createAccount("writer@example.com", "hash", "secret", isAdmin = false)

		accountDao.markDeleted(userId, Clock.System.now())

		assertNotNull(accountDao.getAccount(userId)?.deleted_at)
	}

	@Test
	fun `markDeleted - refuses admin accounts at the data layer`() = runTest {
		val userId = accountDao.createAccount("admin@example.com", "hash", "secret", isAdmin = true)

		accountDao.markDeleted(userId, Clock.System.now())

		assertNull(
			accountDao.getAccount(userId)?.deleted_at,
			"The markDeleted query itself must refuse admin rows",
		)
	}

	@Test
	fun `markDeleted - retry keeps the original deletion time`() = runTest {
		val userId = accountDao.createAccount("retry@example.com", "hash", "secret", isAdmin = false)
		// Millisecond precision so the value round-trips Postgres' microsecond columns exactly.
		val now = Clock.System.now()
		val firstDeletion = kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilliseconds()) - 5.days

		accountDao.markDeleted(userId, firstDeletion)
		accountDao.markDeleted(userId, Clock.System.now())

		assertEquals(firstDeletion, accountDao.getAccount(userId)?.deleted_at)
	}

	@Test
	fun `auth tokens of a soft-deleted account are invisible to the bearer gate`() = runTest {
		val userId = accountDao.createAccount("locked-out@example.com", "hash", "secret", isAdmin = false)
		authTokenDao.setToken(
			userId = userId,
			installId = "install-1",
			token = Token(userId = userId, auth = "auth-locked", refresh = "refresh-locked"),
			expires = Clock.System.now() + 30.days,
		)
		assertNotNull(authTokenDao.getTokenByAuthToken("auth-locked"))

		accountDao.markDeleted(userId, Clock.System.now())
		assertNull(authTokenDao.getTokenByAuthToken("auth-locked"))

		accountDao.restoreDeleted(userId)
		assertNotNull(authTokenDao.getTokenByAuthToken("auth-locked"))
	}

	@Test
	fun `restoreDeleted - un-flags a soft-deleted account`() = runTest {
		val userId = accountDao.createAccount("writer2@example.com", "hash", "secret", isAdmin = false)
		accountDao.markDeleted(userId, Clock.System.now())

		accountDao.restoreDeleted(userId)

		assertNull(accountDao.getAccount(userId)?.deleted_at)
	}

	@Test
	fun `getSoftDeletedBefore - returns only accounts past the cutoff`() = runTest {
		val now = Clock.System.now()
		val oldId = accountDao.createAccount("old@example.com", "hash", "secret", isAdmin = false)
		val recentId = accountDao.createAccount("recent@example.com", "hash", "secret", isAdmin = false)
		accountDao.markDeleted(oldId, now - 40.days)
		accountDao.markDeleted(recentId, now - 2.days)

		val due = accountDao.getSoftDeletedBefore(now - 30.days)

		assertEquals(listOf(oldId), due.map { it.id })
	}
}
