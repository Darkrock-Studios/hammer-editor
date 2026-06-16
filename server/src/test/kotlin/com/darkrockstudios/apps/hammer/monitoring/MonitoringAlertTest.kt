package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import com.darkrockstudios.apps.hammer.database.ErrorLogDao
import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
import com.darkrockstudios.apps.hammer.database.PublishedStoryReaderDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.UserActivityDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class MonitoringAlertTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase

	private val fixedNow = Instant.parse("2026-01-15T12:00:00Z")
	private val clock = object : Clock {
		override fun now(): Instant = fixedNow
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

	private fun job(email: FakeEmailService): Pair<MonitoringMaintenanceJob, ErrorRepository> {
		val errorRepository = ErrorRepository(ErrorLogDao(db), clock)
		val maintenance = MonitoringMaintenanceJob(
			configRepository = ConfigRepository(ServerConfigDao(db)),
			metricsRepository = MetricsRepository(ApiMetricDao(db)),
			errorRepository = errorRepository,
			securityRepository = SecurityRepository(LoginAttemptDao(db), clock),
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
		return maintenance to errorRepository
	}

	private val alertingConfig = MonitoringConfig(
		enabled = true,
		trackErrors = true,
		alertEmailEnabled = true,
		alertEmail = "admin@example.com",
		syncFailureThreshold = 3,
	)

	@Test
	fun `emails the admin once per noisy error group`() = runTest {
		val email = FakeEmailService()
		val (maintenance, errorRepository) = job(email)

		// Cross the threshold (3) for one fingerprint.
		repeat(3) {
			errorRepository.record(
				"RuntimeException",
				"/api/sync",
				7L,
				"boom",
				"stack",
				500
			)
		}

		maintenance.evaluateErrorAlerts(alertingConfig)
		assertEquals(1, email.sentSubjects.size)

		// Already notified: a second pass sends nothing more.
		maintenance.evaluateErrorAlerts(alertingConfig)
		assertEquals(1, email.sentSubjects.size)
	}

	@Test
	fun `does not email when the provider is not configured`() = runTest {
		val email = FakeEmailService(configured = false)
		val (maintenance, errorRepository) = job(email)
		repeat(5) {
			errorRepository.record(
				"RuntimeException",
				"/api/sync",
				null,
				"boom",
				"stack",
				500
			)
		}

		maintenance.evaluateErrorAlerts(alertingConfig)
		assertEquals(0, email.sentSubjects.size)
	}
}
