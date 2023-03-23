package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.HasProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.SaveSceneResponse
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH_NAME
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Application.projectRoutes() {
    routing {
        authenticate(USER_AUTH_NAME) {
            route("/project/{userId}/{projectName}") {
                hasProject()
                uploadScene()
            }
        }
    }
}

private fun Route.hasProject() {
    val projectRepository: ProjectRepository = get()

    get("/has_project") {
        val principal = call.principal<ServerUserIdPrincipal>()!!
        val projectName = call.parameters["projectName"]

        if (projectName == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
            )
        } else {
            val exists = projectRepository.hasProject(principal.id, projectName)
            call.respond(HasProjectResponse(exists))
        }
    }
}

private fun Route.uploadScene() {
    val projectRepository: ProjectRepository = get()

    post("/upload_scene") {
        val principal = call.principal<ServerUserIdPrincipal>()!!
        val projectName = call.parameters["projectName"]

        val formParameters = call.receiveParameters()
        val entityId = formParameters["entityId"].toString().toLongOrNull()
        val sceneName = formParameters["sceneName"].toString()
        val sceneContent = formParameters["sceneContent"].toString()
        val sceneOrder = formParameters["sceneOrder"].toString().toIntOrNull()
        val scenePath = formParameters["scenePath"].toString()

        if (projectName == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
            )
        } else if(entityId == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "entityId was missing")
            )
        } else if(sceneContent.isBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "sceneContent was missing")
            )
        } else if(sceneOrder == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "sceneOrder was missing")
            )
        } else if(scenePath.isBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "scenePath was missing")
            )
        } else {
            val projectDef = ProjectDefinition(projectName)
            val entity = ProjectEntity.SceneEntity(
                id = entityId,
                order = sceneOrder,
                name = sceneName,
                path = scenePath.trim().split("/"),
                content = sceneContent
            )

            val result = projectRepository.saveEntity(principal.id, projectDef, entity)
            if(result.isSuccess) {
                call.respond(SaveSceneResponse(result.getOrThrow()))
            } else {
                val e = result.exceptionOrNull()
                call.respond(
                    status = HttpStatusCode.Conflict,
                    HttpResponseError(error = "Save Error", message = e?.message ?: "Unknown failure")
                )
            }
        }
    }
}
