package com.darkrockstudios.apps.hammer.e2e.util

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.net.URLEncoder
import kotlin.time.Clock

/**
 * Base for end-to-end tests that drive the web frontend rather than the sync API: seeds a
 * whitelisted account and signs it in through the real login form, leaving the session cookie
 * on the returned client.
 */
abstract class WebEndToEndTest : EndToEndTest() {

	/** Creates [email] as a whitelisted account, optionally with [penName] already claimed. */
	protected fun seedWhitelistedAccount(
		email: String,
		password: String,
		penName: String? = null,
	) = runBlocking {
		E2eTestData.createAccount(TestAccount(email, password), database())
		database().serverDatabase.whiteListQueries
			.addToWhiteList(email, Clock.System.now(), "Test author", null)

		if (penName != null) {
			val account = database().serverDatabase.accountQueries.findAccount(email).executeAsOne()
			database().serverDatabase.accountQueries.updatePenName(penName, account.id)
		}
	}

	/**
	 * Signs in over the login form and returns the client holding the session. [configure] adds
	 * to the client being built, for tests that need a particular transport behaviour.
	 */
	protected suspend fun login(
		email: String,
		password: String,
		configure: HttpClientConfig<*>.() -> Unit = {},
	): HttpClient {
		val authed = HttpClient {
			install(HttpCookies)
			configure()
		}

		val response = authed.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				"email=${URLEncoder.encode(email, "UTF-8")}" +
					"&password=${URLEncoder.encode(password, "UTF-8")}"
			)
		}
		check(response.status == HttpStatusCode.Found) {
			"Harness could not log $email in: got ${response.status}"
		}

		return authed
	}
}
