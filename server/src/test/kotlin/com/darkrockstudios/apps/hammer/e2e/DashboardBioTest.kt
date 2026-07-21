package com.darkrockstudios.apps.hammer.e2e

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import io.ktor.client.HttpClient
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.cookies.HttpCookies
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
import kotlin.time.Clock

/**
 * Saving a bio renders a fragment large enough for the Compression plugin to rewrite the response,
 * which makes the send pipeline suspend. Driven over a real engine so that path is actually taken.
 *
 * The failure this guards against surfaces only in the log: the response is already written by the
 * time the handler's coroutine resumes, so a crash there leaves the client with a healthy 200.
 */
class DashboardBioTest : EndToEndTest() {

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

	private fun seed() = runBlocking {
		E2eTestData.createAccount(TestAccount(email, password), database())
		database().serverDatabase.whiteListQueries
			.addToWhiteList(email, Clock.System.now(), "Test author", null)
		val account = database().serverDatabase.accountQueries.findAccount(email).executeAsOne()
		database().serverDatabase.accountQueries.updatePenName("Test Author", account.id)
	}

	private suspend fun login(): HttpClient {
		val authed = HttpClient {
			install(HttpCookies)
			install(ContentEncoding) { gzip() }
		}
		val response = authed.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				"email=${URLEncoder.encode(email, "UTF-8")}" +
					"&password=${URLEncoder.encode(password, "UTF-8")}"
			)
		}
		assertEquals(HttpStatusCode.Found, response.status)
		return authed
	}

	@Test
	fun `saving a bio renders the section and a toast without failing the handler`(): Unit = runBlocking {
		doStartServer()
		seed()
		val authed = login()
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
