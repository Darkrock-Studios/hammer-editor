package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

val AdminOnlyPlugin = createRouteScopedPlugin(
	name = "AdminOnlyPlugin"
) {
	onCall { call ->
		val session = call.sessions.get<UserSession>()

		if (session == null || !session.isAdmin) {
			if (call.request.headers[HxRequestHeaders.Request] == "true") {
				val message = call.msg("security_access_denied")
				call.respondHtml(HttpStatusCode.Forbidden) {
					createHTML().div { +message }
				}
			} else {
				call.respondRedirect("/unauthorized")
			}
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
			if (call.request.headers[HxRequestHeaders.Request] == "true") {
				val message = call.msg("security_please_login")
				call.respondHtml(HttpStatusCode.Unauthorized) {
					createHTML().div { +message }
				}
			} else {
				call.respondRedirect("/login")
			}
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
