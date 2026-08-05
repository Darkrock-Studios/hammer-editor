package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class UserDataPurgeDaoTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var purgeDao: UserDataPurgeDao

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		purgeDao = UserDataPurgeDao(db)

		setupKoin()
	}

	@Test
	fun `purgeUserData - deletes a soft-deleted account`() = runTest {
		val userId = accountDao.createAccount("purge-me@example.com", "hash", "secret", isAdmin = false)
		accountDao.markDeleted(userId, Clock.System.now())

		assertTrue(purgeDao.purgeUserData(userId))
		assertNull(accountDao.getAccount(userId))
	}

	@Test
	fun `purgeUserData - refuses an account that is not soft-deleted`() = runTest {
		val userId = accountDao.createAccount("keep-me@example.com", "hash", "secret", isAdmin = false)

		assertFalse(purgeDao.purgeUserData(userId), "A restored account must never be purged")
		assertNotNull(accountDao.getAccount(userId))
	}

	@Test
	fun `purgeUserData - missing account returns false`() = runTest {
		assertFalse(purgeDao.purgeUserData(999_999L))
	}
}
