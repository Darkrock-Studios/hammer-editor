package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import io.ktor.htmx.HxRequestHeaders
import io.ktor.htmx.HxResponseHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authenticate
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.koin.ktor.ext.get

val AdminOnlyPlugin = createRouteScopedPlugin(
	name = "AdminOnlyPlugin"
) {
	val accountsRepository = application.get<AccountsRepository>()
	onCall { call ->
		val session = call.sessions.get<UserSession>()

		if (session == null || !accountsRepository.isAdmin(session.userId)) {
			call.denyAccess(HttpStatusCode.Forbidden, "/unauthorized")
			return@onCall
		}
	}
}

fun Route.adminOnly(build: Route.() -> Unit): Route {
	return authenticate("auth-session") {
		install(AdminOnlyPlugin)
		apply(build)
	}
}

val AuthenticatedOnlyPlugin = createRouteScopedPlugin(
	name = "AuthenticatedOnlyPlugin"
) {
	onCall { call ->
		val session = call.sessions.get<UserSession>()

		if (session == null) {
			call.denyAccess(HttpStatusCode.Unauthorized, "/login")
			return@onCall
		}
	}
}

fun Route.authenticatedOnly(build: Route.() -> Unit): Route {
	return authenticate("auth-session") {
		install(AuthenticatedOnlyPlugin)
		apply(build)
	}
}

/**
 * Turns a caller away to [destination], keeping [status] honest for the access log.
 *
 * An htmx request is redirected by header rather than by body: htmx acts on HX-Redirect before it
 * decides what to do with the response body, so this still works where a body cannot be relied on.
 * A body would be the wrong tool anyway — htmx discards the body of a 4xx, and StatusPages answers
 * a 401 with the whole unauthorized page, which has no business being swapped into a fragment.
 */
private suspend fun ApplicationCall.denyAccess(status: HttpStatusCode, destination: String) {
	if (request.headers[HxRequestHeaders.Request] == "true") {
		response.header(HxResponseHeaders.Redirect, destination)
		respond(status)
	} else {
		respondRedirect(destination)
	}
}
