package com.darkrockstudios.apps.hammer.e2e

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Saving a bio renders a fragment large enough for the Compression plugin to rewrite the response,
 * which makes the send pipeline suspend. Driven over a real engine so that path is actually taken.
 *
 * The failure this guards against surfaces only in the log: the response is already written by the
 * time the handler's coroutine resumes, so a crash there leaves the client with a healthy 200.
 */
class DashboardBioTest : WebEndToEndTest() {

	private val email = "author@test.com"
	private val password = "password123!@#"

	private val loggedErrors = CopyOnWriteArrayList<ILoggingEvent>()
	private val errorCollector = object : AppenderBase<ILoggingEvent>() {
		override fun append(event: ILoggingEvent) {
			if (event.level == Level.ERROR) loggedErrors.add(event)
		}
	}

	private fun collectServerErrors() {
		val root = LoggerFactory.getILoggerFactory().getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
		errorCollector.start()
		(root as ch.qos.logback.classic.Logger).addAppender(errorCollector)
	}

	@AfterEach
	fun detachErrorCollector() {
		val root = LoggerFactory.getILoggerFactory().getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
		(root as ch.qos.logback.classic.Logger).detachAppender(errorCollector)
		errorCollector.stop()
	}

	@Test
	fun `saving a bio renders the section and a toast without failing the handler`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password, penName = "Test Author")
		// Compression is what makes the send pipeline suspend, which is the point of this test.
		val authed = login(email, password) { install(ContentEncoding) { gzip() } }
		collectServerErrors()

		val bio = "I write stories about the sea. ".repeat(30)
		val response = authed.post(route("dashboard/bio")) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("bio=${URLEncoder.encode(bio, "UTF-8")}")
		}

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "toast-success")
		assertEquals(bio.trim(), database().serverDatabase.accountQueries.findAccount(email).executeAsOne().bio)
		assertTrue(loggedErrors.isEmpty(), "Server logged errors: ${loggedErrors.map { it.formattedMessage }}")
	}
}
