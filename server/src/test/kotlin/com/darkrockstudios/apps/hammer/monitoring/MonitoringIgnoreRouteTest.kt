package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.frontend.adminMonitoringPages
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class MonitoringIgnoreRouteTest : BaseTest() {

	private val fixedNow = Instant.parse("2026-01-15T12:00:00Z")
	private val clock = object : Clock {
		override fun now() = fixedNow
	}

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var configRepository: ConfigRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		setupKoin()
		configRepository = ConfigRepository(ServerConfigDao(db))
	}

	private fun appModule(): Application.() -> Unit = {
		routing {
			adminMonitoringPages(
				metricsRepository = mockk(relaxed = true),
				configRepository = configRepository,
				errorRepository = mockk(relaxed = true),
				securityRepository = mockk(relaxed = true),
				userActivityRepository = mockk(relaxed = true),
				storyReaderRepository = mockk(relaxed = true),
				recurringTaskRegistry = RecurringTaskRegistry(),
				projectsSyncManager = mockk<SyncSessionManager<Long, ProjectsSynchronizationSession>>(
					relaxed = true
				),
				projectSyncManager = mockk<SyncSessionManager<Long, ProjectSynchronizationSession>>(
					relaxed = true
				),
				clock = clock,
			)
		}
	}

	private suspend fun io.ktor.client.HttpClient.postRule(
		path: String,
		type: String,
		glob: String? = null
	) =
		post(path) {
			header("HX-Request", "true")
			setBody(FormDataContent(Parameters.build {
				append("type", type)
				glob?.let { append("routeGlob", it) }
			}))
		}

	@Test
	fun `posting ignore adds a rule and requests a page refresh`() = testApplication {
		application(appModule())

		val resp = client.postRule(
			"/monitoring/errors/ignore",
			"UnsupportedProtocolVersionException",
			"/api/*"
		)

		assertEquals(HttpStatusCode.OK, resp.status)
		assertEquals("true", resp.headers["HX-Refresh"])
		assertEquals(
			listOf(IgnoredErrorRule("UnsupportedProtocolVersionException", "/api/*")),
			configRepository.get(AdminServerConfig.IGNORED_ERROR_RULES),
		)
	}

	@Test
	fun `posting the same rule twice does not duplicate it`() = testApplication {
		application(appModule())

		client.postRule("/monitoring/errors/ignore", "UnsupportedProtocolVersionException")
		client.postRule("/monitoring/errors/ignore", "UnsupportedProtocolVersionException")

		assertEquals(
			listOf(IgnoredErrorRule("UnsupportedProtocolVersionException")),
			configRepository.get(AdminServerConfig.IGNORED_ERROR_RULES),
		)
	}

	@Test
	fun `posting ignore with a blank type adds nothing`() = testApplication {
		application(appModule())

		client.postRule("/monitoring/errors/ignore", "  ")

		assertEquals(emptyList(), configRepository.get(AdminServerConfig.IGNORED_ERROR_RULES))
	}

	@Test
	fun `posting unignore removes only the matching rule`() = testApplication {
		runBlocking {
			configRepository.set(
				AdminServerConfig.IGNORED_ERROR_RULES,
				listOf(
					IgnoredErrorRule("UnsupportedProtocolVersionException"),
					IgnoredErrorRule("RuntimeException", "/api/*"),
				),
			)
		}
		application(appModule())

		val resp = client.postRule("/monitoring/errors/unignore", "RuntimeException", "/api/*")

		assertEquals(HttpStatusCode.OK, resp.status)
		assertEquals("true", resp.headers["HX-Refresh"])
		assertEquals(
			listOf(IgnoredErrorRule("UnsupportedProtocolVersionException")),
			configRepository.get(AdminServerConfig.IGNORED_ERROR_RULES),
		)
	}
}
