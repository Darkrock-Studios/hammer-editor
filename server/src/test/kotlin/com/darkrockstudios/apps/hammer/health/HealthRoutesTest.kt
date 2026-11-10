package com.darkrockstudios.apps.hammer.health

import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.HealthCheckDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.plugins.ApiProtocolEnforcerPlugin
import com.darkrockstudios.apps.hammer.plugins.HttpStatusException
import com.darkrockstudios.apps.hammer.plugins.configureSerialization
import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class HealthRoutesTest : BaseTest() {

	private val logger = LoggerFactory.getLogger("test")
	private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var registry: RecurringTaskRegistry
	private lateinit var healthService: HealthService

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		setupKoin()
		registry = RecurringTaskRegistry()
		healthService = HealthService(
			registry = registry,
			healthCheckDao = HealthCheckDao(db),
			configRepository = ConfigRepository(ServerConfigDao(db)),
			base64 = createTokenBase64(),
			clock = kotlin.time.Clock.System,
			ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
		)
	}

	@AfterEach
	override fun tearDown() {
		taskScope.cancel()
		super.tearDown()
	}

	private fun appModule(): Application.() -> Unit = {
		setupKtorTestKoin(this@HealthRoutesTest, module { single { healthService } })
		configureSerialization()
		install(StatusPages) {
			exception<HttpStatusException> { call, cause -> call.respond(cause.status) }
		}
		install(ApiProtocolEnforcerPlugin)
		routing {
			route(API_ROUTE_PREFIX) { healthRoutes() }
		}
	}

	/** Starts a job and blocks until its first tick has finished. */
	private fun startJob(name: String, tick: suspend () -> Unit = {}) {
		val task = object : RecurringTask(name, logger) {
			override suspend fun tick() = tick()
			override suspend fun nextDelay() = 1.hours
		}
		registry.register(task)
		task.start(taskScope)
		runBlocking {
			while (task.status().nextRun == null) kotlinx.coroutines.delay(10)
		}
	}

	private suspend fun HttpClient.health(path: String = "", token: String? = null): HttpResponse =
		get("$HEALTH_ROUTE$path") {
			token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
		}

	private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

	@Test
	fun `other api routes still require the protocol header`() = testApplication {
		application(appModule())

		val response = client.get("/$API_ROUTE_PREFIX/anything")

		assertEquals(HttpStatusCode.UpgradeRequired, response.status)
	}

	@Test
	fun `anonymous callers get only the summary, without a protocol header`() = testApplication {
		application(appModule())
		startJob("Token maintenance job")

		val response = client.health()

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
		assertEquals(setOf("status"), response.json().keys)
	}

	@Test
	fun `the anonymous summary is 503 when any job is failing`() = testApplication {
		application(appModule())
		startJob("Token maintenance job")
		startJob("Account deletion job") { error("boom") }

		val response = client.health()

		assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
		assertEquals("failing", response.json().getValue("status").jsonPrimitive.content)
	}

	@Test
	fun `a valid token gets the full report, 503 when anything is failing`() = testApplication {
		application(appModule())
		startJob("Account deletion job") { error("boom") }
		val token = runBlocking { healthService.generateToken() }

		val response = client.health(token = token)

		assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
		val body = response.json()
		assertEquals("failing", body.getValue("status").jsonPrimitive.content)
		val job = body.getValue("jobs").jsonObject.getValue("account-deletion").jsonObject
		assertEquals("failed", job.getValue("status").jsonPrimitive.content)
		assertEquals("boom", job.getValue("error").jsonPrimitive.content)
		assertEquals("ok", body.getValue("database").jsonObject.getValue("status").jsonPrimitive.content)
	}

	@Test
	fun `a wrong token is rejected on every path`() = testApplication {
		application(appModule())
		startJob("Token maintenance job")
		runBlocking { healthService.generateToken() }

		val statuses = listOf("", "/database", "/jobs/token-maintenance").map { client.health(it, "wrong").status }

		assertEquals(List(3) { HttpStatusCode.Unauthorized }, statuses)
	}

	@Test
	fun `a valid token on a healthy server gets the full report with 200`() = testApplication {
		application(appModule())
		startJob("Token maintenance job")
		val token = runBlocking { healthService.generateToken() }

		val response = client.health(token = token)

		assertEquals(HttpStatusCode.OK, response.status)
		assertTrue("jobs" in response.json())
	}

	@Test
	fun `a non-bearer authorization header is treated as anonymous`() = testApplication {
		application(appModule())

		val response = client.get(HEALTH_ROUTE) { header(HttpHeaders.Authorization, "Basic dXNlcjpwYXNz") }

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals(setOf("status"), response.json().keys)
	}

	@Test
	fun `an empty bearer token is rejected`() = testApplication {
		application(appModule())

		val response = client.get(HEALTH_ROUTE) { header(HttpHeaders.Authorization, "Bearer ") }

		assertEquals(HttpStatusCode.Unauthorized, response.status)
	}

	@Test
	fun `per-check paths require a token`() = testApplication {
		application(appModule())
		startJob("Token maintenance job")

		val database = client.health("/database")
		val job = client.health("/jobs/token-maintenance")

		assertEquals(HttpStatusCode.Unauthorized, database.status)
		assertEquals(HttpStatusCode.Unauthorized, job.status)
	}

	@Test
	fun `each job path reflects only its own job`() = testApplication {
		application(appModule())
		startJob("Token maintenance job")
		startJob("Account deletion job") { error("boom") }
		val token = runBlocking { healthService.generateToken() }

		val healthy = client.health("/jobs/token-maintenance", token)
		val failing = client.health("/jobs/account-deletion", token)
		val unknown = client.health("/jobs/nope", token)

		assertEquals(HttpStatusCode.OK, healthy.status)
		assertEquals("ok", healthy.json().getValue("status").jsonPrimitive.content)
		assertEquals(HttpStatusCode.ServiceUnavailable, failing.status)
		assertEquals("failed", failing.json().getValue("status").jsonPrimitive.content)
		assertEquals(HttpStatusCode.NotFound, unknown.status)
	}

	@Test
	fun `the database path reports the ping`() = testApplication {
		application(appModule())
		val token = runBlocking { healthService.generateToken() }

		val response = client.health("/database", token)

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.json()
		assertEquals("ok", body.getValue("status").jsonPrimitive.content)
		assertTrue("latencyMs" in body)
	}

	@Test
	fun `a revoked token no longer unlocks detail`() = testApplication {
		application(appModule())
		val token = runBlocking { healthService.generateToken() }
		runBlocking { healthService.revokeToken() }

		val response = client.health("/database", token)

		assertEquals(HttpStatusCode.Unauthorized, response.status)
		assertFalse(runBlocking { healthService.verifyToken(token) })
	}
}
