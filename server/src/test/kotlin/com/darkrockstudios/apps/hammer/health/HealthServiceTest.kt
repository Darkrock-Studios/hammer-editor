package com.darkrockstudios.apps.hammer.health

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskStatus
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HealthServiceTest : BaseTest() {

	private val logger = LoggerFactory.getLogger("test")
	private val t0 = Instant.parse("2026-01-15T12:00:00Z")
	private val clock = MutableClock(t0)

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var configRepository: ConfigRepository
	private lateinit var tokenHasher: TokenHasher
	private lateinit var registry: RecurringTaskRegistry

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		setupKoin()
		configRepository = ConfigRepository(ServerConfigDao(db))
		tokenHasher = tokenHasher()
		registry = RecurringTaskRegistry()
	}

	private fun tokenHasher(): TokenHasher {
		val fileSystem = FakeFileSystem()
		val secureRandom = SecureRandom()
		val base64 = kotlin.io.encoding.Base64.Default
		val manager = KeyringManager(
			object : ServerSecretProvider {
				override fun loadKeyring(): String? = null
			},
			KeyringCodec(secureRandom, base64),
			fileSystem,
			"/none".toPath(),
		)
		return TokenHasher(manager, ServerSecretManager(fileSystem, secureRandom), base64)
	}

	private fun service(dao: HealthCheckDao = HealthCheckDao(db)) = HealthService(
		registry = registry,
		healthCheckDao = dao,
		configRepository = configRepository,
		tokenHasher = tokenHasher,
		base64 = createTokenBase64(),
		clock = clock,
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
	fun `a first tick that hangs becomes overdue`() = runTest {
		launch(task("Monitoring maintenance job") { awaitCancellation() })

		clock.now = t0 + HealthService.OVERDUE_GRACE + 1.seconds
		val job = service().report().jobs.getValue("monitoring-maintenance")

		assertNull(job.nextRun)
		assertEquals(JobState.OVERDUE, job.status)
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
		clock.now = t0 + 4.seconds
		service.report()
		val pingsWithinWindow = dao.pings
		clock.now = t0 + 6.seconds
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
	fun `colliding slugs get a numeric suffix`() {
		val statuses = listOf("Sync job", "Sync", "sync!").map { name ->
			RecurringTaskStatus(name, true, null, null, false, null)
		}

		val slugs = HealthService.jobsBySlug(statuses).keys.toList()

		assertEquals(listOf("sync", "sync-2", "sync-3"), slugs)
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
