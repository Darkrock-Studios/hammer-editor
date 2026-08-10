package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

/**
 * The monitoring toggles are enforced by the repository, so every login route
 * honors them without repeating the checks.
 */
class SecurityRepositoryTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private val clock = Clock.System

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		setupKoin()
	}

	private fun repository(config: MonitoringConfig): SecurityRepository {
		val state = MonitoringState().apply { update(config) }
		return SecurityRepository(LoginAttemptDao(db), state, LoggerFactory.getLogger("test"), clock)
	}

	private fun recorded() = db.serverDatabase.loginAttemptQueries.getRecentAttempts(10, 0).executeAsList()

	@Test
	fun `login tracking off records nothing`() = runTest {
		repository(MonitoringConfig(enabled = true, trackLoginAttempts = false))
			.recordLoginAttempt("author@test.com", "203.0.113.7", success = false)

		assertEquals(0, recorded().size)
	}

	@Test
	fun `storing IPs off keeps the attempt but drops the address`() = runTest {
		repository(MonitoringConfig(enabled = true, trackLoginAttempts = true, storeLoginIp = false))
			.recordLoginAttempt("author@test.com", "203.0.113.7", success = false)

		val attempt = recorded().single()
		assertEquals("author@test.com", attempt.email)
		assertNull(attempt.ip_address, "The operator disabled IP capture")
	}

	@Test
	fun `emails are lower-cased and blank ones stored as null`() = runTest {
		val repository = repository(MonitoringConfig(enabled = true, trackLoginAttempts = true))

		repository.recordLoginAttempt("  Author@Test.com ", null, success = true)
		repository.recordLoginAttempt("   ", null, success = false)

		val attempts = recorded()
		assertEquals(2, attempts.size)
		assertEquals("author@test.com", attempts.single { it.success }.email)
		assertNull(attempts.single { !it.success }.email, "Blank emails must not become an empty-string account")
	}
}
