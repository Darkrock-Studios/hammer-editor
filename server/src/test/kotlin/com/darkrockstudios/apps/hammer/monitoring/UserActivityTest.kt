package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import com.darkrockstudios.apps.hammer.database.ErrorLogDao
import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Exercises the unique-user activity metric: the DAO's distinct-user window math
 * and retention purge against the test database, plus the maintenance job's
 * collector flush and master-switch gating.
 */
class UserActivityTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase

	private val now = Instant.parse("2026-01-15T12:00:00Z")
	private val clock = object : Clock { override fun now() = now }

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SqliteTestDatabase()
		db.initialize()
		setupKoin()
	}

	private suspend fun createAccount(email: String): Long =
		AccountDao(db).createAccount(email, "hash", "secret", isAdmin = false)

	@Test
	fun `distinct users dedupe per hour and isolate by activity type`() = runTest {
		val dao = UserActivityDao(db)
		val userA = createAccount("a@x.com")
		val userB = createAccount("b@x.com")
		val hour = Instant.parse("2026-01-15T11:00:00Z")

		// User A twice in the same hour collapses to one; user B is a second distinct user.
		dao.recordActivity(userA, ActivityType.SYNC.dbValue, hour)
		dao.recordActivity(userA, ActivityType.SYNC.dbValue, hour)
		dao.recordActivity(userB, ActivityType.SYNC.dbValue, hour)
		// A WEB row for A must not bleed into the SYNC count.
		dao.recordActivity(userA, ActivityType.WEB.dbValue, hour)

		assertEquals(2L, dao.countDistinctUsersSince(ActivityType.SYNC.dbValue, now - 24.hours))
		assertEquals(1L, dao.countDistinctUsersSince(ActivityType.WEB.dbValue, now - 24.hours))
	}

	@Test
	fun `window excludes rows older than the cutoff and purge deletes them`() = runTest {
		val dao = UserActivityDao(db)
		val user = createAccount("a@x.com")
		val recent = Instant.parse("2026-01-15T10:00:00Z")
		val old = Instant.parse("2026-01-01T10:00:00Z")

		dao.recordActivity(user, ActivityType.SYNC.dbValue, recent)
		dao.recordActivity(user, ActivityType.SYNC.dbValue, old)

		// 24h window sees only the recent row; 30d window sees both.
		assertEquals(1L, dao.countDistinctUsersSince(ActivityType.SYNC.dbValue, now - 24.hours))
		assertEquals(1L, dao.countDistinctUsersSince(ActivityType.SYNC.dbValue, now - 30.days))

		dao.deleteActivityBefore(now - 7.days)
		assertEquals(1L, dao.countDistinctUsersSince(ActivityType.SYNC.dbValue, now - 30.days))
	}

	@Test
	fun `overview derives window rollups and a complete leftmost trend day from one fetch`() =
		runTest {
			val repo = UserActivityRepository(UserActivityDao(db))
			val dao = UserActivityDao(db)
			val a = createAccount("a@x.com")
			val b = createAccount("b@x.com")
			val c = createAccount("c@x.com")
			val d = createAccount("d@x.com")

			dao.recordActivity(a, ActivityType.SYNC.dbValue, now - 2.hours)   // 24h / 7d / 30d
			dao.recordActivity(b, ActivityType.SYNC.dbValue, now - 3.days)    // 7d / 30d
			dao.recordActivity(c, ActivityType.SYNC.dbValue, now - 15.days)   // 30d
			dao.recordActivity(a, ActivityType.WEB.dbValue, now - 1.hours)    // web isolated

			// On the first calendar day of the window but BEFORE the precise now-30d
			// cutoff (window start is mid-day): shown on the trend, excluded from 30d.
			val firstDay = truncateToUtcDay(now - 30.days)
			dao.recordActivity(d, ActivityType.SYNC.dbValue, firstDay + 1.hours)

			val overview = repo.activeUsersOverview(now)

			assertEquals(1L, overview.sync.h24)
			assertEquals(2L, overview.sync.d7)
			assertEquals(3L, overview.sync.d30)   // d excluded — before the precise cutoff
			assertEquals(1L, overview.web.h24)

			// The leftmost trend day is the window's UTC-day start and includes d's row.
			assertEquals(firstDay, overview.daily.first().day)
			assertEquals(1L, overview.daily.first().sync)
			assertEquals(truncateToUtcDay(now), overview.daily.last().day)
		}

	private fun truncateToUtcDay(instant: Instant): Instant {
		val dayMillis = 24L * 60 * 60 * 1000
		return Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() / dayMillis * dayMillis)
	}

	@Test
	fun `maintenance tick flushes collected keys into the table`() = runTest {
		val collector = UserActivityCollector(clock)
		val repository = UserActivityRepository(UserActivityDao(db))
		val job = job(collector, repository, MonitoringConfig(enabled = true))
		val user = createAccount("a@x.com")

		job.tick()                                   // syncs collecting = true
		collector.record(user, ActivityType.SYNC)
		job.tick()                                   // flushes the key into the table

		assertEquals(1L, repository.uniqueUsers(ActivityType.SYNC, now - 24.hours))
	}

	@Test
	fun `master switch off prevents collection and flush`() = runTest {
		val collector = UserActivityCollector(clock)
		val repository = UserActivityRepository(UserActivityDao(db))
		val job = job(collector, repository, MonitoringConfig(enabled = false))
		val user = createAccount("a@x.com")

		job.tick()                                   // syncs collecting = false
		collector.record(user, ActivityType.SYNC)    // no-op while disabled
		job.tick()                                   // flush is skipped behind the master switch

		assertEquals(0L, repository.uniqueUsers(ActivityType.SYNC, now - 30.days))
	}

	private suspend fun job(
		collector: UserActivityCollector,
		repository: UserActivityRepository,
		config: MonitoringConfig,
	): MonitoringMaintenanceJob {
		val configRepository = ConfigRepository(ServerConfigDao(db))
		configRepository.set(AdminServerConfig.MONITORING_CONFIG, config)
		return MonitoringMaintenanceJob(
			configRepository = configRepository,
			metricsRepository = MetricsRepository(ApiMetricDao(db)),
			errorRepository = ErrorRepository(ErrorLogDao(db), clock),
			securityRepository = SecurityRepository(LoginAttemptDao(db), clock),
			collector = MetricsCollector(clock),
			userActivityCollector = collector,
			userActivityRepository = repository,
			monitoringState = MonitoringState(),
			emailService = NoopEmailService,
			clock = clock,
			logger = LoggerFactory.getLogger("test"),
		)
	}

	private object NoopEmailService : EmailService {
		override suspend fun sendEmail(to: String, subject: String, bodyHtml: String, bodyText: String?): EmailResult =
			EmailResult.Success

		override suspend fun isConfigured(): Boolean = false
	}
}
