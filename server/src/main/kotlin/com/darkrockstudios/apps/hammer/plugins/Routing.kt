package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.account.accountRoutes
import com.darkrockstudios.apps.hammer.admin.adminRoutes
import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import com.darkrockstudios.apps.hammer.project.projectRoutes
import com.darkrockstudios.apps.hammer.projects.projectsRoutes
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
	val logger = log
	routing {
		route(API_ROUTE_PREFIX) {
			accountRoutes()
			projectsRoutes()
			projectRoutes(logger)
			adminRoutes()
			teapot()
		}
	}
}

private fun Route.teapot() {
	get("/teapot") {
		call.respondText(
			"I'm a little Tea Pot [${call.t(R("language"))}]",
			status = HttpStatusCode.fromValue(418)
		)
	}
}