package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import com.darkrockstudios.apps.hammer.database.ErrorLogDao
import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
import com.darkrockstudios.apps.hammer.database.PublishedStoryReaderDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.UserActivityDao
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The maintenance loop runs on a real dispatcher in production. Because every test
 * shares one embedded Postgres, a tick that outlives its application would mutate a
 * later test's data. These tests pin the shutdown contract that prevents that.
 */
class MonitoringMaintenanceJobLifecycleTest {

	private lateinit var db: SqliteTestDatabase

	@BeforeEach
	fun setup() {
		GlobalContext.stopKoin()
		GlobalContext.startKoin {
			modules(
				module {
					single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { Dispatchers.Default }
					single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.IO }
					single<CoroutineContext>(named(DISPATCHER_MAIN)) { Dispatchers.Default }
				}
			)
		}
		db = SqliteTestDatabase()
		db.initialize()
	}

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	@Test
	fun `stop does not return while a tick is in flight`() = runBlocking {
		val tickEntered = CountDownLatch(1)
		val release = CountDownLatch(1)

		// Blocks the first tick partway through, mimicking an in-flight maintenance pass.
		val gatingClock = object : Clock {
			private val firstCall = AtomicBoolean(true)
			override fun now(): Instant {
				if (firstCall.compareAndSet(true, false)) {
					tickEntered.countDown()
					release.await()
				}
				return Instant.parse("2026-01-15T12:00:00Z")
			}
		}

		val job = buildJob(gatingClock)
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		job.start(scope)

		assertTrue(tickEntered.await(10, TimeUnit.SECONDS), "the maintenance tick never started")

		val stopper = launch(Dispatchers.Default) { job.stop() }
		delay(200)
		assertTrue(stopper.isActive, "stop returned while a tick was still running")

		release.countDown()
		stopper.join()

		assertFalse(job.isRunning(), "the job is still running after stop")
		scope.cancel()
	}

	private suspend fun buildJob(clock: Clock): MonitoringMaintenanceJob {
		val configRepository = ConfigRepository(ServerConfigDao(db))
		configRepository.set(
			AdminServerConfig.MONITORING_CONFIG,
			MonitoringConfig(enabled = true, trackApiMetrics = true, alertEmailEnabled = false),
		)
		return MonitoringMaintenanceJob(
			configRepository = configRepository,
			metricsRepository = MetricsRepository(ApiMetricDao(db)),
			errorRepository = ErrorRepository(ErrorLogDao(db), clock),
			securityRepository = SecurityRepository(LoginAttemptDao(db), clock),
			collector = MetricsCollector(clock),
			userActivityCollector = UserActivityCollector(clock),
			userActivityRepository = UserActivityRepository(UserActivityDao(db)),
			storyReaderCollector = StoryReaderCollector(clock),
			storyReaderRepository = StoryReaderRepository(PublishedStoryReaderDao(db)),
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
