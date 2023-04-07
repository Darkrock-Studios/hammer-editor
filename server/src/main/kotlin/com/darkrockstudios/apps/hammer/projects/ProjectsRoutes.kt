package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.http.GetProjectsResponse
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH_NAME
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Application.projectsRoutes() {
	routing {
		authenticate(USER_AUTH_NAME) {
			getProjects()
			deleteProject()
			createProject()
		}
	}
}

private fun Route.getProjects() {
	val projectsRepository: ProjectsRepository = get()

	get("/projects/{userId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!

		val projects = projectsRepository.getProjects(principal.id)
		val deletedProjects = projectsRepository.getDeletedProjects(principal.id)
		val responseData = GetProjectsResponse(
			projects = projects.map { it.name }.toSet(),
			deletedProjects = deletedProjects
		)
		call.respond(responseData)
	}
}

private fun Route.deleteProject() {
	val projectsRepository: ProjectsRepository = get()

	get("/projects/{userId}/{projectName}/delete") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
			)
		} else {
			projectsRepository.deleteProject(principal.id, projectName)
			call.respond("Success")
		}
	}
}

private fun Route.createProject() {
	val projectsRepository: ProjectsRepository = get()

	get("/projects/{userId}/{projectName}/create") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
			)
		} else {
			projectsRepository.createProject(principal.id, projectName)
			call.respond("Success")
		}
	}
}