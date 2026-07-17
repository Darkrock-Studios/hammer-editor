package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WhiteListRepositoryTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase
	private lateinit var whiteListDao: WhiteListDao
	private lateinit var configDao: ServerConfigDao
	private lateinit var configRepository: ConfigRepository
	private lateinit var clock: TestClock

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SqliteTestDatabase()
		db.initialize()
		whiteListDao = WhiteListDao(db)
		configDao = ServerConfigDao(db)
		configRepository = ConfigRepository(configDao)

		clock = TestClock(Clock.System)

		setupKoin()
	}

	private fun createRepo() = WhiteListRepository(whiteListDao, configRepository, clock)

	@Test
	fun `useWhiteList - returns true by default`() = runTest {
		val repo = createRepo()
		assertTrue(repo.useWhiteList(), "Should be enabled by default")
	}

	@Test
	fun `setWhiteListEnabled - updates config`() = runTest {
		val repo = createRepo()

		repo.setWhiteListEnabled(false)
		assertFalse(repo.useWhiteList(), "Should be disabled after setting to false")

		repo.setWhiteListEnabled(true)
		assertTrue(repo.useWhiteList(), "Should be enabled after setting to true")
	}

	@Test
	fun `setWhiteListEnabled - persists`() = runTest {
		val repo1 = createRepo()
		repo1.setWhiteListEnabled(false)

		val repo2 = createRepo()
		assertFalse(repo2.useWhiteList(), "Should be disabled in new repo instance")
	}

	@Test
	fun `getWhiteList - all`() = runTest {
		val emails = listOf("a@b.com", "c@d.com")
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }

		val repo = createRepo()
		val result = repo.getWhiteList()

		assertEquals(emails.sorted(), result)
	}

	@Test
	fun `getWhiteList - paginated`() = runTest {
		val emails = (1..25).map { "user$it@example.com" }.sorted()
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }

		val repo = createRepo()
		val result = repo.getWhiteList(page = 1, pageSize = 10)

		assertEquals(emails.drop(10).take(10), result)
	}

	@Test
	fun `getWhiteListCount`() = runTest {
		val emails = listOf("a@b.com", "c@d.com")
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }

		val repo = createRepo()
		val result = repo.getWhiteListCount()

		assertEquals(2L, result)
	}

	@Test
	fun `isOnWhiteList - cleans email`() = runTest {
		whiteListDao.addToWhiteList("test@example.com", Instant.fromEpochSeconds(0), "Test")

		val repo = createRepo()
		val result = repo.isOnWhiteList("  TEST@Example.com  ")

		assertTrue(result)
	}

	@Test
	fun `addToWhiteList - cleans email`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("  TEST@Example.com  ")

		assertTrue(whiteListDao.isWhiteListed("test@example.com", clock.now()))
	}

	@Test
	fun `removeFromWhiteList - cleans email`() = runTest {
		whiteListDao.addToWhiteList("test@example.com", Instant.fromEpochSeconds(0), "Test")

		val repo = createRepo()
		repo.removeFromWhiteList("  TEST@Example.com  ")

		assertFalse(whiteListDao.isWhiteListed("test@example.com", clock.now()))
	}

	@Test
	fun `validateEmail - valid email returns true`() = runTest {
		val repo = createRepo()
		assertTrue(repo.validateEmail("test@example.com"))
	}

	@Test
	fun `validateEmail - invalid email returns false`() = runTest {
		val repo = createRepo()
		assertFalse(repo.validateEmail("not-an-email"))
		assertFalse(repo.validateEmail("missing@"))
		assertFalse(repo.validateEmail("@nodomain.com"))
	}

	@Test
	fun `validateReason - 32 chars or less returns true`() = runTest {
		val repo = createRepo()
		assertTrue(repo.validateReason("Short reason"))
		assertTrue(repo.validateReason("A".repeat(32)))
	}

	@Test
	fun `validateReason - over 32 chars returns false`() = runTest {
		val repo = createRepo()
		assertFalse(repo.validateReason("A".repeat(33)))
	}

	@Test
	fun `isOnWhiteList - entry with no expiry never expires`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("forever@example.com", "Test", expires = null)

		clock.advanceTime(3650.days)

		assertTrue(repo.isOnWhiteList("forever@example.com"))
	}

	@Test
	fun `isOnWhiteList - entry is whitelisted until its expiry passes`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("temp@example.com", "Beta tester", expires = clock.now() + 7.days)

		assertTrue(repo.isOnWhiteList("temp@example.com"), "Should be whitelisted before expiry")

		clock.advanceTime(7.days + 1.minutes)

		// No reaping job has run — enforcement must come from the query itself.
		assertFalse(repo.isOnWhiteList("temp@example.com"), "Should not be whitelisted after expiry")
	}

	@Test
	fun `isOnWhiteList - expired entry still exists until reaped`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("temp@example.com", "Beta tester", expires = clock.now() + 1.days)
		clock.advanceTime(2.days)

		assertEquals(1L, repo.getWhiteListCount(), "Row should survive until the job reaps it")
		assertFalse(repo.isOnWhiteList("temp@example.com"), "But it must not authorize")
	}

	@Test
	fun `addToWhiteList - re-adding an existing email renews its expiry`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("renew@example.com", "Beta tester", expires = clock.now() + 1.days)
		clock.advanceTime(2.days)
		assertFalse(repo.isOnWhiteList("renew@example.com"), "Lapsed before renewal")

		repo.addToWhiteList("renew@example.com", "Beta tester", expires = clock.now() + 30.days)

		assertTrue(repo.isOnWhiteList("renew@example.com"), "Re-adding should renew, not be ignored")
		assertEquals(1L, repo.getWhiteListCount(), "Renewal must not duplicate the row")
	}

	@Test
	fun `addToWhiteList - re-adding can clear an expiry`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("clear@example.com", "Beta tester", expires = clock.now() + 1.days)

		repo.addToWhiteList("clear@example.com", "Friend", expires = null)
		clock.advanceTime(365.days)

		assertTrue(repo.isOnWhiteList("clear@example.com"), "Expiry should have been cleared")
	}

	@Test
	fun `updateExpiry - sets and clears an expiry`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("edit@example.com", "Friend", expires = null)

		repo.updateExpiry("  EDIT@Example.com  ", clock.now() + 1.days)
		clock.advanceTime(2.days)
		assertFalse(repo.isOnWhiteList("edit@example.com"), "Should honour the new expiry")

		repo.updateExpiry("edit@example.com", null)
		assertTrue(repo.isOnWhiteList("edit@example.com"), "Clearing the expiry should restore access")
	}

	@Test
	fun `getExpiredEntries - returns only lapsed entries`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("lapsed@example.com", "Test", expires = clock.now() + 1.days)
		repo.addToWhiteList("current@example.com", "Test", expires = clock.now() + 30.days)
		repo.addToWhiteList("forever@example.com", "Test", expires = null)

		clock.advanceTime(2.days)

		assertEquals(listOf("lapsed@example.com"), repo.getExpiredEntries().map { it.email })
	}

	@Test
	fun `removeExpired - deletes only lapsed entries`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("lapsed@example.com", "Test", expires = clock.now() + 1.days)
		repo.addToWhiteList("current@example.com", "Test", expires = clock.now() + 30.days)
		repo.addToWhiteList("forever@example.com", "Test", expires = null)

		clock.advanceTime(2.days)
		repo.removeExpired()

		assertEquals(
			listOf("current@example.com", "forever@example.com"),
			repo.getWhiteList(),
		)
	}

	@Test
	fun `validateExpiry - null is valid and past is not`() = runTest {
		val repo = createRepo()

		assertTrue(repo.validateExpiry(null), "Never-expires is valid")
		assertTrue(repo.validateExpiry(clock.now() + 1.days))
		assertFalse(repo.validateExpiry(clock.now() - 1.days), "A past expiry is meaningless")
	}
}
