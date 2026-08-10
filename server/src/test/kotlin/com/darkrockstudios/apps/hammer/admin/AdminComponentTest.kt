package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class AdminComponentTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var whiteListDao: WhiteListDao
	private lateinit var whiteListRepository: WhiteListRepository
	private lateinit var clock: TestClock

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		whiteListDao = WhiteListDao(db)
		clock = TestClock(Clock.System)
		whiteListRepository = WhiteListRepository(whiteListDao, clock)

		setupKoin()
	}

	private fun createComponent() = AdminComponent(whiteListRepository)

	// Read straight from the table so we assert what was persisted.
	private fun storedExpiry(email: String) =
		db.serverDatabase.whiteListQueries.getAll().executeAsList().single { it.email == email }.expires

	@Test
	fun `addToWhiteList - no expiry keeps the entry forever`() = runTest {
		val component = createComponent()

		val result = component.addToWhiteList("forever@example.com")

		assertTrue(isSuccess(result))
		assertNull(storedExpiry("forever@example.com"), "Omitting expiry must store null")

		clock.advanceTime(3650.days)
		assertTrue(whiteListRepository.isOnWhiteList("forever@example.com"))
	}

	@Test
	fun `addToWhiteList - a future expiry is honoured`() = runTest {
		val component = createComponent()

		val result = component.addToWhiteList("temp@example.com", expires = clock.now() + 7.days)

		assertTrue(isSuccess(result))
		assertNotNull(storedExpiry("temp@example.com"))

		clock.advanceTime(8.days)
		assertFalse(whiteListRepository.isOnWhiteList("temp@example.com"))
	}

	@Test
	fun `addToWhiteList - a past expiry is rejected and nothing is added`() = runTest {
		val component = createComponent()

		val result = component.addToWhiteList("past@example.com", expires = clock.now() - 1.days)

		assertTrue(isFailure(result))
		assertFalse(whiteListRepository.isOnWhiteList("past@example.com"))
		assertTrue(whiteListRepository.getWhiteList().isEmpty(), "A rejected add must not persist a row")
	}

	@Test
	fun `addToWhiteList - an invalid email is rejected even with a valid expiry`() = runTest {
		val component = createComponent()

		val result = component.addToWhiteList("not-an-email", expires = clock.now() + 7.days)

		assertTrue(isFailure(result))
		assertTrue(whiteListRepository.getWhiteList().isEmpty())
	}
}
