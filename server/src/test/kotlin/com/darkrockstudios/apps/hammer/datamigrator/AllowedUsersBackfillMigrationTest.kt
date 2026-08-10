package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.datamigrator.migrations.AllowedUsersBackfillMigration
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class AllowedUsersBackfillMigrationTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var whiteListDao: WhiteListDao
	private lateinit var configRepository: ConfigRepository
	private lateinit var repository: WhiteListRepository
	private lateinit var migration: AllowedUsersBackfillMigration
	private lateinit var clock: TestClock

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		whiteListDao = WhiteListDao(db)
		configRepository = ConfigRepository(ServerConfigDao(db))
		clock = TestClock(Clock.System)
		repository = WhiteListRepository(whiteListDao, configRepository, clock)
		migration = AllowedUsersBackfillMigration(repository)

		setupKoin()
	}

	@Test
	fun `migrate - adds non-deleted accounts with default reason and no expiry`() = runTest {
		accountDao.createAccount("alice@example.com", "hash", "secret", isAdmin = false)
		accountDao.createAccount("admin@example.com", "hash", "secret", isAdmin = true)

		migration.migrate()

		val alice = whiteListDao.getByEmail("alice@example.com")
		assertNotNull(alice)
		assertEquals(WhiteListRepository.REASON_EXISTING_ACCOUNT, alice.reason)
		assertNull(alice.expires)
		assertNotNull(whiteListDao.getByEmail("admin@example.com"))
	}

	@Test
	fun `migrate - excludes soft-deleted accounts`() = runTest {
		val userId = accountDao.createAccount("gone@example.com", "hash", "secret", isAdmin = false)
		accountDao.markDeleted(userId, clock.now())

		migration.migrate()

		assertNull(whiteListDao.getByEmail("gone@example.com"))
	}

	@Test
	fun `migrate - keeps an existing entry's reason and expiry`() = runTest {
		val expiry = clock.now() + 30.days
		whiteListDao.addToWhiteList("patron@example.com", clock.now(), "Patreon", expiry)
		accountDao.createAccount("patron@example.com", "hash", "secret", isAdmin = false)

		migration.migrate()

		val entry = whiteListDao.getByEmail("patron@example.com")
		assertNotNull(entry)
		assertEquals("Patreon", entry.reason)
		assertEquals(expiry, entry.expires)
	}

	@Test
	fun `migrate - running twice adds nothing new`() = runTest {
		accountDao.createAccount("alice@example.com", "hash", "secret", isAdmin = false)

		migration.migrate()
		migration.migrate()

		assertEquals(1L, whiteListDao.getWhiteListCount())
	}

	@Test
	fun `via DataMigrator - a removed email is not re-added once complete`() = runTest {
		accountDao.createAccount("alice@example.com", "hash", "secret", isAdmin = false)
		val migrator = DataMigrator(configRepository).apply { addMigration(migration) }

		migrator.runMigrations()
		repository.removeFromWhiteList("alice@example.com")
		migrator.runMigrations()

		assertNull(whiteListDao.getByEmail("alice@example.com"))
	}
}
