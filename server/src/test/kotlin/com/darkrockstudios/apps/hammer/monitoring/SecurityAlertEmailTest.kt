package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import com.darkrockstudios.apps.hammer.database.ErrorLogDao
import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
import com.darkrockstudios.apps.hammer.database.PublishedStoryReaderDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.UserActivityDao
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityAlertEmailTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase

	private var nowValue = Instant.parse("2026-01-15T12:00:00Z")
	private val clock = object : Clock {
		override fun now(): Instant = nowValue
	}

	private class FakeEmailService(var configured: Boolean = true) : EmailService {
		val sentSubjects = mutableListOf<String>()
		override suspend fun sendEmail(to: String, subject: String, bodyHtml: String, bodyText: String?): EmailResult {
			sentSubjects.add(subject)
			return EmailResult.Success
		}

		override suspend fun isConfigured(): Boolean = configured
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SqliteTestDatabase()
		db.initialize()
		setupKoin()
	}

	private fun job(email: FakeEmailService): Pair<MonitoringMaintenanceJob, SecurityRepository> {
		val securityRepository = SecurityRepository(LoginAttemptDao(db), clock)
		val maintenance = MonitoringMaintenanceJob(
			configRepository = ConfigRepository(ServerConfigDao(db)),
			metricsRepository = MetricsRepository(ApiMetricDao(db)),
			errorRepository = ErrorRepository(ErrorLogDao(db), clock),
			securityRepository = securityRepository,
			collector = MetricsCollector(clock),
			userActivityCollector = UserActivityCollector(clock),
			userActivityRepository = UserActivityRepository(UserActivityDao(db)),
			storyReaderCollector = StoryReaderCollector(clock),
			storyReaderRepository = StoryReaderRepository(PublishedStoryReaderDao(db)),
			monitoringState = MonitoringState(),
			emailService = email,
			clock = clock,
			logger = LoggerFactory.getLogger("test"),
		)
		return maintenance to securityRepository
	}

	private val config = MonitoringConfig(
		enabled = true,
		trackLoginAttempts = true,
		alertEmailEnabled = true,
		alertEmail = "admin@example.com",
	)

	private suspend fun SecurityRepository.recordFailures(count: Int, email: String?, ip: String?) {
		repeat(count) { recordLoginAttempt(email = email, ipAddress = ip, success = false) }
	}

	@Test
	fun `emails once when an account crosses the failure threshold, then suppresses within cooldown`() = runTest {
		val email = FakeEmailService()
		val (maintenance, security) = job(email)

		security.recordFailures(SecurityAlerts.ACCOUNT_FAILURES.toInt(), email = "victim@example.com", ip = null)

		maintenance.evaluateSecurityAlerts(config)
		assertEquals(1, email.sentSubjects.size)

		// Same subject, still inside the cooldown window: no second email.
		maintenance.evaluateSecurityAlerts(config)
		assertEquals(1, email.sentSubjects.size)
	}

	@Test
	fun `re-alerts after the cooldown elapses`() = runTest {
		val email = FakeEmailService()
		val (maintenance, security) = job(email)

		security.recordFailures(SecurityAlerts.ACCOUNT_FAILURES.toInt(), email = "victim@example.com", ip = null)
		maintenance.evaluateSecurityAlerts(config)
		assertEquals(1, email.sentSubjects.size)

		// Jump past the cooldown; a continuing attack (fresh failures) alerts again.
		nowValue += 7.hours
		security.recordFailures(SecurityAlerts.ACCOUNT_FAILURES.toInt(), email = "victim@example.com", ip = null)
		maintenance.evaluateSecurityAlerts(config)
		assertEquals(2, email.sentSubjects.size)
	}

	@Test
	fun `account and IP signals are independent alerts`() = runTest {
		val email = FakeEmailService()
		val (maintenance, security) = job(email)

		// One IP spraying enough distinct accounts to trip the IP lens, none of which
		// individually crosses the per-account threshold.
		repeat(SecurityAlerts.IP_ACCOUNTS.toInt()) { i ->
			security.recordFailures(2, email = "spray$i@example.com", ip = "203.0.113.88")
		}
		// And a separate single account hammered from a different IP.
		security.recordFailures(SecurityAlerts.ACCOUNT_FAILURES.toInt(), email = "victim@example.com", ip = "198.51.100.7")

		maintenance.evaluateSecurityAlerts(config)
		assertEquals(2, email.sentSubjects.size)
		assertTrue(email.sentSubjects.any { it.contains("victim@example.com") }, "account alert names the hammered account")
		assertTrue(email.sentSubjects.any { it.contains("203.0.113.88") }, "IP alert names the spraying IP")
	}

	@Test
	fun `does not email when the provider is not configured`() = runTest {
		val email = FakeEmailService(configured = false)
		val (maintenance, security) = job(email)
		security.recordFailures(SecurityAlerts.ACCOUNT_FAILURES.toInt(), email = "victim@example.com", ip = null)

		maintenance.evaluateSecurityAlerts(config)
		assertEquals(0, email.sentSubjects.size)
	}
}
