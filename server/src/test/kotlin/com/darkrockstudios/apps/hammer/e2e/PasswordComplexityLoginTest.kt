package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Issue #835: an account created with a complex password could not log in afterwards.
 */
class PasswordComplexityLoginTest : EndToEndTest() {

	@Test
	fun `Complex passwords survive the create then login round trip`(): Unit = roundTrip(
		"asciiSpecials" to "Tr0ub4dor&3xample!",
		"plusEqualsAmp" to "p@ss+word=123&x",
		"percentAndAccent" to "100%%Sécur|té2024#",
		"heavyNonAscii" to "ĄŻ§±¶•ªº¿Œ‰›ﬁ‡°",
		"surroundingSpaces" to "  spaced out pass  ",
	)

	@Test
	fun `Exotic passwords survive the create then login round trip`(): Unit = roundTrip(
		"quotesAndSlashes" to "pass\"word'with\\slash",
		"maxLength" to "A1!".repeat(21) + "b",
		"emoji" to "🔐Secret🔑Pass1!",
		"newline" to "line1\nline2!A1",
	)

	// The login rate limiter allows 10 requests per window, so at most five pairs per server.
	private fun roundTrip(vararg passwords: Pair<String, String>): Unit = runBlocking {
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database())

		// Allowed Users is always enforced, and only the first account (the bootstrap
		// admin) is exempt, so every other address has to be on the list to get in.
		passwords.indices.forEach { index ->
			database().executeAsync(
				"INSERT INTO white_list(email, date_added) VALUES ('user$index@example.com', NOW())"
			)
		}

		doStartServer()

		val failures = mutableListOf<String>()

		passwords.forEachIndexed { index, (name, password) ->
			val email = "user$index@example.com"

			val created = client().post(api("account/create")) {
				headers { append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString()) }
				setBody(
					FormDataContent(
						Parameters.build {
							append("email", email)
							append("password", password)
							append("installId", "fake-install-id")
						}
					)
				)
			}
			if (created.status != HttpStatusCode.Created) {
				failures += "$name: create -> ${created.status} ${created.bodyAsText()}"
				return@forEachIndexed
			}

			val loggedIn = client().post(api("account/login")) {
				headers { append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString()) }
				setBody(
					FormDataContent(
						Parameters.build {
							append("email", email)
							append("password", password)
							append("installId", "fake-install-id")
						}
					)
				)
			}
			if (loggedIn.status != HttpStatusCode.OK) {
				failures += "$name (len=${password.length}): login -> ${loggedIn.status} ${loggedIn.bodyAsText()}"
			}
		}

		assertEquals(emptyList(), failures.toList(), "Round trip failures")
	}
}
