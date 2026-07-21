package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.database.ErrorLogDao
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.monitoring.ErrorRepository
import com.darkrockstudios.apps.hammer.monitoring.MonitoringState
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ClosedByteChannelException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.EOFException
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class StatusPagesErrorRecordingTest {

	private val clock = object : Clock {
		override fun now(): Instant = Instant.parse("2026-01-15T12:00:00Z")
	}

	private lateinit var errorRepository: ErrorRepository

	@BeforeEach
	fun setup() {
		// ErrorLogDao pulls its IO dispatcher from Koin. Other test classes may
		// leave a global Koin running, so clear it first.
		stopKoin()
		startKoin {
			modules(module {
				single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.IO }
			})
		}
		val db = SharedPostgresTestDatabase()
		db.initialize()
		errorRepository = ErrorRepository(ErrorLogDao(db), clock)
	}

	@AfterEach
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun `client aborting an in-flight response is not recorded as a server error`() = testApplication {
		application {
			configureStatusPages(errorRepository, MonitoringState())
			routing {
				get("/assets/style.css") {
					throw ClosedByteChannelException(EOFException("Reset cancel_stream_error"))
				}
			}
		}

		client.get("/assets/style.css")

		runBlocking {
			assertEquals(0, errorRepository.getCount(), "a client abort must not be recorded in the error log")
		}
	}

	@Test
	fun `unhandled server exception is recorded as an error`() = testApplication {
		application {
			configureStatusPages(errorRepository, MonitoringState())
			routing {
				get("/api/boom") { throw RuntimeException("boom") }
			}
		}

		val response = client.get("/api/boom")

		assertEquals(HttpStatusCode.InternalServerError, response.status)
		runBlocking {
			assertEquals(1, errorRepository.getCount())
			val recorded = errorRepository.getRecent(0, 10).single()
			assertEquals("RuntimeException", recorded.exception_type)
		}
	}
}
