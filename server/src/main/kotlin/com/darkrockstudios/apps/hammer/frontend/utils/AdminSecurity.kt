package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.frontend.data.UserSession
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
			if (call.request.headers["HX-Request"] == "true") {
				call.respondHtml(HttpStatusCode.Forbidden) {
					createHTML().div { +"Access denied" }
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
