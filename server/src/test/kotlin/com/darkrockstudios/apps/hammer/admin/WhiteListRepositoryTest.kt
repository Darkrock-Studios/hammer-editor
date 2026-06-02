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

		assertTrue(whiteListDao.isWhiteListed("test@example.com"))
	}

	@Test
	fun `removeFromWhiteList - cleans email`() = runTest {
		whiteListDao.addToWhiteList("test@example.com", Instant.fromEpochSeconds(0), "Test")

		val repo = createRepo()
		repo.removeFromWhiteList("  TEST@Example.com  ")

		assertFalse(whiteListDao.isWhiteListed("test@example.com"))
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
}
