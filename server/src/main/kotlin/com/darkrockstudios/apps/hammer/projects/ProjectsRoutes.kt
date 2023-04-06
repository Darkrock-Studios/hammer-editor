package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.http.GetProjectsResponse
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH_NAME
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Application.projectsRoutes() {
	routing {
		authenticate(USER_AUTH_NAME) {
			getProjects()
		}
	}
}

private fun Route.getProjects() {
	val projectsRepository: ProjectsRepository = get()

	get("/projects/{userId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!

		val projects = projectsRepository.getProjects(principal.id)
		val responseData = GetProjectsResponse(projects.map { it.name })
		call.respond(responseData)
	}
}