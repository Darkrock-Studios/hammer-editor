package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.Error_log
import com.darkrockstudios.apps.hammer.frontend.adminMonitoringPages
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class MonitoringExportRouteTest {

	private val fixedNow = Instant.parse("2026-01-15T12:00:00Z")
	private val clock = object : Clock { override fun now() = fixedNow }

	private fun fakeError(
		id: Long = 1L,
		exceptionType: String = "RuntimeException",
		route: String? = "/api/sync",
		userId: Long? = null,
		message: String? = "boom",
		stackTrace: String? = null,
		status: Int = 500,
		occurrences: Long = 1L,
	) = Error_log(
		id = id,
		fingerprint = "fp$id",
		exception_type = exceptionType,
		route = route,
		user_id = userId,
		message = message,
		stack_trace = stackTrace,
		status = status,
		occurrence_count = occurrences,
		first_seen = fixedNow,
		last_seen = fixedNow,
		notified_at = null,
	)

	private fun appModule(errorRepo: ErrorRepository): Application.() -> Unit = {
		routing {
			adminMonitoringPages(
				metricsRepository = mockk(relaxed = true),
				configRepository = mockk(relaxed = true),
				errorRepository = errorRepo,
				securityRepository = mockk(relaxed = true),
				userActivityRepository = mockk(relaxed = true),
				storyReaderRepository = mockk(relaxed = true),
				recurringTaskRegistry = RecurringTaskRegistry(),
				projectsSyncManager = mockk<SyncSessionManager<Long, ProjectsSynchronizationSession>>(relaxed = true),
				projectSyncManager = mockk<SyncSessionManager<Long, ProjectSynchronizationSession>>(relaxed = true),
				clock = clock,
				patreonFeatureEnabled = false,
				emailFeatureEnabled = false,
			)
		}
	}

	@Test
	fun `export returns 200 OK`() = testApplication {
		val errorRepo = mockk<ErrorRepository> {
			coEvery { getCount(null) } returns 0L
			coEvery { getRecent(0, 0, null) } returns emptyList()
		}
		application(appModule(errorRepo))
		assertEquals(HttpStatusCode.OK, client.get("/monitoring/errors/export").status)
	}

	@Test
	fun `export returns empty JSON array when no errors recorded`() = testApplication {
		val errorRepo = mockk<ErrorRepository> {
			coEvery { getCount(null) } returns 0L
			coEvery { getRecent(0, 0, null) } returns emptyList()
		}
		application(appModule(errorRepo))

		val body = client.get("/monitoring/errors/export").bodyAsText()
		// pretty-printed empty array may be "[]" or "[ ]"; strip all whitespace to compare
		assertEquals("[]", body.trim().replace("\\s+".toRegex(), ""))
	}

	@Test
	fun `export serialises all expected JSON fields`() = testApplication {
		val error = fakeError(
			exceptionType = "NullPointerException",
			route = "/api/project/{id}",
			userId = 42L,
			message = "null ref",
			stackTrace = "at Foo.bar(Foo.kt:10)",
			occurrences = 7L,
		)
		val errorRepo = mockk<ErrorRepository> {
			coEvery { getCount(null) } returns 1L
			coEvery { getRecent(0, 1, null) } returns listOf(error)
		}
		application(appModule(errorRepo))

		val body = client.get("/monitoring/errors/export").bodyAsText()
		assertTrue(body.contains("\"exceptionType\""))
		assertTrue(body.contains("NullPointerException"))
		assertTrue(body.contains("\"/api/project/{id}\""))
		assertTrue(body.contains("\"userId\""))
		assertTrue(body.contains("\"status\""))
		assertTrue(body.contains("\"occurrences\""))
		assertTrue(body.contains("\"message\""))
		assertTrue(body.contains("\"null ref\""))
		assertTrue(body.contains("\"stackTrace\""))
		assertTrue(body.contains("\"firstSeen\""))
		assertTrue(body.contains("\"lastSeen\""))
	}

	@Test
	fun `route filter restricts export to matching errors`() = testApplication {
		val syncError = fakeError(id = 1L, exceptionType = "RuntimeException", route = "/api/sync")
		val errorRepo = mockk<ErrorRepository> {
			coEvery { getCount("/api/sync") } returns 1L
			coEvery { getRecent(0, 1, "/api/sync") } returns listOf(syncError)
		}
		application(appModule(errorRepo))

		val body = client.get("/monitoring/errors/export?route=/api/sync").bodyAsText()
		assertTrue(body.contains("RuntimeException"))
		assertTrue(body.contains("\"/api/sync\""))
	}

	@Test
	fun `export sets attachment Content-Disposition header with filename`() = testApplication {
		val errorRepo = mockk<ErrorRepository> {
			coEvery { getCount(null) } returns 0L
			coEvery { getRecent(0, 0, null) } returns emptyList()
		}
		application(appModule(errorRepo))

		val resp = client.get("/monitoring/errors/export")
		val disposition = resp.headers["Content-Disposition"]
		assertNotNull(disposition)
		assertTrue(disposition.contains("attachment"))
		assertTrue(disposition.contains("hammer-errors.json"))
	}
}
