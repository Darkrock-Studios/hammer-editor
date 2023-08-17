package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.account.accountRoutes
import com.darkrockstudios.apps.hammer.admin.adminRoutes
import com.darkrockstudios.apps.hammer.project.projectRoutes
import com.darkrockstudios.apps.hammer.projects.projectsRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
	val logger = log
	routing {
		route("api") {
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
		call.respondText("I'm a little Tea Pot", status = HttpStatusCode.fromValue(418))
	}
}