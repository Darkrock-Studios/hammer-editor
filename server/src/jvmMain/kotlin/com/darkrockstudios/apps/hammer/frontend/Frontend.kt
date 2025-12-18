package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import com.darkrockstudios.apps.hammer.frontend.utils.setLocaleAndRedirect
import com.darkrockstudios.apps.hammer.frontend.utils.withMessages
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import org.koin.ktor.ext.inject

fun Route.frontend() {
	val accountsRepository: AccountsRepository by inject()

	staticResources("/assets", "/assets")

	get("/") {
		val model = mapOf(
			"title" to "Hammer",
			"greeting" to "Hello, World!",
			"message" to "Welcome to Hammer Server"
		)
		call.respond(MustacheContent("index.mustache", call.withMessages(model)))
	}

	get("/clicked") {
		// This endpoint is intended to be called via HTMX and returns a small HTML fragment
		val ts = System.currentTimeMillis()
		val html = createHTML().p {
			+"You clicked at $ts"
			div {

			}
		}

		// Optional: trigger a client-side event consumers can listen to via HTMX
		// Using the standard HX-Trigger header (no extra dependencies required)
		call.response.headers.append("HX-Trigger", "{\"clicked\":{\"ts\":$ts}}")

		// Respond with the fragment for HTMX to swap into the page
		call.respondText(html, ContentType.Text.Html)
	}

	get("/login") {
		call.respond(MustacheContent("login.mustache", call.withMessages()))
	}

	// Change language preference (cookie) then redirect back
	post("/locale") {
		val params = call.receiveParameters()
		val newLocale = params["locale"] ?: ""
		val redirectTo = params["redirectTo"]
		call.setLocaleAndRedirect(newLocale, redirectTo)
	}

	post("/login") {
		val params = call.receiveParameters()
		val email = params["email"] ?: ""
		val password = params["password"] ?: ""

		val result = accountsRepository.login(
			email = email,
			password = password,
			installId = "web"
		)
		if (isSuccess(result)) {
			val token = result.data
			val isAdmin = accountsRepository.isAdmin(token.userId)
			call.sessions.set(
				UserSession(
					userId = token.userId.toString(),
					username = email,
					isAdmin = isAdmin
				)
			)
			call.respondRedirect("/admin")
		} else {
			val message = result.displayMessageText(call) ?: "Login failed"
			val model = mapOf(
				"message" to message
			)
			call.respond(MustacheContent("login.mustache", call.withMessages(model)))
		}
	}

	// Simple logout: clear the session and redirect to home
	get("/logout") {
		call.sessions.clear<UserSession>()
		call.respondRedirect("/")
	}

	adminOnly {
		get("/admin") {
			val session = call.sessions.get<UserSession>()
			val model = mapOf(
				"isAdmin" to (session?.isAdmin?.toString() ?: "null"),
			)
			call.respond(MustacheContent("admin.mustache", call.withMessages(model)))
		}
	}
}

fun Application.configureFrontEnd() {
	configureTemplating()

	install(plugin = Sessions) {
		cookie<UserSession>("user_session") {
			cookie.path = "/"
			cookie.maxAgeInSeconds = 3600 * 24 * 7 // 7 days
			cookie.extensions["SameSite"] = "lax"
		}
	}
}

fun AuthenticationConfig.frontendAuthentication(accountRepo: AccountsRepository, whitelistRepo: WhiteListRepository) {
	session<UserSession>("auth-session") {
		validate { session ->
			// Check if it's valid, eventually...
			session
		}
		challenge {
			call.respondRedirect("/login")
		}
	}
}