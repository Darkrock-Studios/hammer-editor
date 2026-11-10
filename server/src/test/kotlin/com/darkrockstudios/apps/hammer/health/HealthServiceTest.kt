package com.darkrockstudios.apps.hammer.health

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.HealthCheckDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskStatus
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

class HealthServiceTest : BaseTest() {

	private val logger = LoggerFactory.getLogger("test")
	private val t0 = Instant.parse("2026-01-15T12:00:00Z")
	private val clock = MutableClock(t0)
	private val timeSource = TestTimeSource()

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var configRepository: ConfigRepository
	private lateinit var registry: RecurringTaskRegistry

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		setupKoin()
		configRepository = ConfigRepository(ServerConfigDao(db))
		registry = RecurringTaskRegistry()
	}

	private fun service(
		dao: HealthCheckDao = HealthCheckDao(db),
		config: ConfigRepository = configRepository,
	) = HealthService(
		registry = registry,
		healthCheckDao = dao,
		configRepository = config,
		base64 = createTokenBase64(),
		clock = clock,
		ioDispatcher = ioTestDispatcher,
		timeSource = timeSource,
	)

	private fun task(
		name: String,
		interval: Duration = 1.hours,
		tick: suspend () -> Unit = {},
	) = object : RecurringTask(name, logger, clock) {
		override suspend fun tick() = tick()
		override suspend fun nextDelay() = interval
	}

	/** Registers and starts [task], letting its first tick run. */
	private fun TestScope.launch(task: RecurringTask, scope: CoroutineScope = backgroundScope) {
		registry.register(task)
		task.start(scope)
		runCurrent()
	}

	@Test
	fun `a healthy job and database report ok`() = runTest {
		launch(task("Token maintenance job"))

		val report = service().report()

		assertTrue(report.ok)
		assertEquals(OverallState.OK, report.status)
		assertEquals(DatabaseState.OK, report.database.status)
		assertEquals(JobState.OK, report.jobs.getValue("token-maintenance").status)
		assertEquals(t0.toString(), report.jobs.getValue("token-maintenance").lastRun)
		assertEquals((t0 + 1.hours).toString(), report.jobs.getValue("token-maintenance").nextRun)
	}

	@Test
	fun `a job whose last tick threw is failed and carries the error`() = runTest {
		launch(task("Account deletion job") { error("boom") })

		val report = service().report()

		val job = report.jobs.getValue("account-deletion")
		assertEquals(JobState.FAILED, job.status)
		assertEquals("boom", job.error)
		assertFalse(report.ok)
		assertEquals(OverallState.FAILING, report.status)
	}

	@Test
	fun `a registered job that is not running is stopped`() = runTest {
		registry.register(task("Whitelist expiry job"))

		val job = service().report().jobs.getValue("whitelist-expiry")

		assertEquals(JobState.STOPPED, job.status)
	}

	@Test
	fun `a job is overdue only once past its next run plus the grace period`() = runTest {
		launch(task("Disk cache prune job", interval = 1.hours))
		val dueAt = t0 + 1.hours

		clock.now = dueAt + HealthService.OVERDUE_GRACE - 1.seconds
		val withinGrace = service().report().jobs.getValue("disk-cache-prune").status
		clock.now = dueAt + HealthService.OVERDUE_GRACE + 1.seconds
		val pastGrace = service().report().jobs.getValue("disk-cache-prune").status

		assertEquals(JobState.OK, withinGrace)
		assertEquals(JobState.OVERDUE, pastGrace)
	}

	@Test
	fun `a first tick that hangs becomes overdue after the tick limit`() = runTest {
		launch(task("Monitoring maintenance job") { awaitCancellation() })

		clock.now = t0 + HealthService.MAX_TICK_DURATION - 1.seconds
		val stillRunning = service().report().jobs.getValue("monitoring-maintenance")
		clock.now = t0 + HealthService.MAX_TICK_DURATION + 1.seconds
		val hung = service().report().jobs.getValue("monitoring-maintenance")

		assertNull(hung.nextRun)
		assertEquals(JobState.OK, stillRunning.status)
		assertEquals(JobState.OVERDUE, hung.status)
	}

	@Test
	fun `a slow later tick is not overdue until the tick limit`() = runTest {
		var ticks = 0
		launch(task("Disk cache prune job", interval = 1.hours) {
			if (++ticks > 1) awaitCancellation()
		})
		val secondTickStart = t0 + 1.hours
		clock.now = secondTickStart
		advanceTimeBy(1.hours)
		runCurrent()

		clock.now = secondTickStart + HealthService.OVERDUE_GRACE + 1.minutes
		val slow = service().report().jobs.getValue("disk-cache-prune").status
		clock.now = secondTickStart + HealthService.MAX_TICK_DURATION + 1.seconds
		val hung = service().report().jobs.getValue("disk-cache-prune").status

		assertEquals(2, ticks)
		assertEquals(JobState.OK, slow)
		assertEquals(JobState.OVERDUE, hung)
	}

	@Test
	fun `a database that errors is down and fails the report`() = runTest {
		launch(task("Token maintenance job"))

		val report = service(FakeHealthCheckDao(db) { error("connection refused") }).report()

		assertEquals(DatabaseState.DOWN, report.database.status)
		assertNull(report.database.latencyMs)
		assertFalse(report.ok)
	}

	@Test
	fun `a database ping that times out is reported as timeout`() = runTest {
		val report = service(FakeHealthCheckDao(db) { false }).report()

		assertEquals(DatabaseState.TIMEOUT, report.database.status)
		assertFalse(report.ok)
	}

	@Test
	fun `reports are cached briefly so concurrent probes share one ping`() = runTest {
		val dao = FakeHealthCheckDao(db) { true }
		val service = service(dao)

		service.report()
		timeSource += 4.seconds
		service.report()
		val pingsWithinWindow = dao.pings
		timeSource += 2.seconds
		service.report()

		assertEquals(1, pingsWithinWindow)
		assertEquals(2, dao.pings)
	}

	@Test
	fun `slugs drop the job suffix and normalise punctuation`() {
		assertEquals("account-deletion", HealthService.slugOf("Account deletion job"))
		assertEquals("patreon-polling", HealthService.slugOf("Patreon: polling job"))
		assertEquals("sync", HealthService.slugOf("Sync"))
	}

	@Test
	fun `colliding slugs get a suffix that is stable as jobs come and go`() {
		fun status(name: String) = RecurringTaskStatus(name, true, null, null, false, null)

		val pair = HealthService.jobsBySlug(listOf(status("Sync job"), status("Sync")))
		val trio = HealthService.jobsBySlug(listOf(status("Sync job"), status("Sync"), status("sync!")))
		val alone = HealthService.jobsBySlug(listOf(status("Sync"), status("Backup job")))

		assertEquals(2, pair.size)
		assertTrue(pair.keys.all { it.matches(Regex("sync-[0-9a-f]{6}")) })
		pair.forEach { (slug, s) -> assertEquals(s.name, trio.getValue(slug).name) }
		assertEquals(setOf("sync", "backup"), alone.keys)
	}

	@Test
	fun `a generated token verifies and only its hash is stored`() = runTest {
		val service = service()

		val token = service.generateToken()

		assertTrue(service.verifyToken(token))
		assertFalse(service.verifyToken(token + "x"))
		val stored = configRepository.get(AdminServerConfig.HEALTH_TOKEN)!!
		assertNotEquals(token, stored.hash)
		assertEquals(t0, service.tokenCreatedAt())
	}

	@Test
	fun `a token issued by an earlier instance still verifies`() = runTest {
		val token = service().generateToken()

		val restarted = service()

		assertTrue(restarted.verifyToken(token))
		assertEquals(t0, restarted.tokenCreatedAt())
	}

	@Test
	fun `a token read that hangs gives up instead of blocking the probe`() = runTest {
		val hanging = ConfigRepository(object : ServerConfigDao(db) {
			override suspend fun getConfig(key: String): String? = awaitCancellation()
		})
		val service = service(config = hanging)

		assertFailsWith<HealthService.TokenUnavailableException> { service.verifyToken("anything") }
		service.close()
	}

	@Test
	fun `a token read that fails surfaces the error and is retried next time`() = runTest {
		var calls = 0
		val flaky = ConfigRepository(object : ServerConfigDao(db) {
			override suspend fun getConfig(key: String): String? {
				if (++calls == 1) error("database down")
				return null
			}
		})
		val service = service(config = flaky)

		assertFailsWith<IllegalStateException> { service.verifyToken("anything") }
		assertFalse(service.verifyToken("anything"))
		assertEquals(2, calls)
	}

	@Test
	fun `no token verifies before one is generated`() = runTest {
		val service = service()

		assertFalse(service.verifyToken("anything"))
		assertNull(service.tokenCreatedAt())
	}

	@Test
	fun `regenerating replaces the old token and revoking clears it`() = runTest {
		val service = service()
		val first = service.generateToken()

		val second = service.generateToken()
		val firstAfterRegenerate = service.verifyToken(first)
		val secondAfterRegenerate = service.verifyToken(second)
		service.revokeToken()

		assertFalse(firstAfterRegenerate)
		assertTrue(secondAfterRegenerate)
		assertFalse(service.verifyToken(second))
		assertNull(service.tokenCreatedAt())
	}

	@Test
	fun `the real ping succeeds against the database`() = runTest {
		assertTrue(HealthCheckDao(db).ping(5.seconds))
	}

	private class MutableClock(var now: Instant) : Clock {
		override fun now() = now
	}

	private class FakeHealthCheckDao(
		database: Database,
		private val result: () -> Boolean,
	) : HealthCheckDao(database) {
		var pings = 0
			private set

		override suspend fun ping(timeout: Duration): Boolean {
			pings++
			return result()
		}
	}
}
