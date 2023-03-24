package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.HasProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.LoadSceneResponse
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
                beginProjectSync()
                endProjectSync()
                hasProject()
                uploadScene()
                downloadScene()
            }
        }
    }
}

private fun Route.beginProjectSync() {
    val projectRepository: ProjectRepository = get()

    get("/begin_sync") {
        val principal = call.principal<ServerUserIdPrincipal>()!!
        val projectName = call.parameters["projectName"]

        if (projectName == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
            )
        } else {
            val projectDef = ProjectDefinition(projectName)
            val result = projectRepository.beginProjectSync(principal.id, projectDef)
            if (result.isSuccess) {
                val syncId = result.getOrThrow()
                call.respond(syncId)
            } else {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    HttpResponseError(
                        error = "Failed to begin sync",
                        message = result.exceptionOrNull()?.message ?: "Unknown"
                    )
                )
            }
        }
    }
}

private fun Route.endProjectSync() {
    val projectRepository: ProjectRepository = get()

    get("/end_sync") {
        val principal = call.principal<ServerUserIdPrincipal>()!!
        val projectName = call.parameters["projectName"]
        val syncId = call.request.headers["x-sync-id"]

        if (projectName == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
            )
        } else if (syncId == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "syncId was missing")
            )
        } else {
            val projectDef = ProjectDefinition(projectName)
            val result = projectRepository.endProjectSync(principal.id, projectDef, syncId)
            if (result.isSuccess) {
                val syncId = result.getOrThrow()
                call.respond(syncId)
            } else {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    HttpResponseError(
                        error = "Failed to begin sync",
                        message = result.exceptionOrNull()?.message ?: "Unknown"
                    )
                )
            }
        }
    }
}

private fun Route.hasProject() {
    val projectRepository: ProjectRepository = get()

    get("/has_project") {
        val principal = call.principal<ServerUserIdPrincipal>()!!
        val projectName = call.parameters["projectName"]
        val syncId = call.request.headers["x-sync-id"]

        if (projectName == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
            )
        } else if (syncId == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Header", message = "x-sync-id was missing")
            )
        } else {
            val exists = projectRepository.hasProject(principal.id, ProjectDefinition(projectName), syncId)
            call.respond(HasProjectResponse(exists))
        }
    }
}

private fun Route.uploadScene() {
    val projectRepository: ProjectRepository = get()

    post("/upload_scene/entityId") {
        val principal = call.principal<ServerUserIdPrincipal>()!!
        val projectName = call.parameters["projectName"]
        val entityId = call.parameters["entityId"]?.toIntOrNull()

        val formParameters = call.receiveParameters()
        val sceneName = formParameters["sceneName"].toString()
        val sceneContent = formParameters["sceneContent"].toString()
        val sceneOrder = formParameters["sceneOrder"].toString().toIntOrNull()
        val scenePath = formParameters["scenePath"].toString()
        val syncId = call.request.headers["x-sync-id"]

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
        } else if (scenePath.isBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "scenePath was missing")
            )
        } else if (syncId.isNullOrBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "syncId was missing")
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

            val result = projectRepository.saveEntity(principal.id, projectDef, entity, syncId)
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

private fun Route.downloadScene() {
    val projectRepository: ProjectRepository = get()

    get("/download_scene/{entityId}") {
        val principal = call.principal<ServerUserIdPrincipal>()!!
        val projectName = call.parameters["projectName"]
        val entityId = call.parameters["entityId"]?.toIntOrNull()
        val syncId = call.request.headers["x-sync-id"]

        if (projectName == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
            )
        } else if (entityId == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Parameter", message = "entityId was missing")
            )
        } else if (syncId == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                HttpResponseError(error = "Missing Header", message = "syncId was missing")
            )
        } else {
            val projectDef = ProjectDefinition(projectName)

            val result =
                projectRepository.loadEntity(principal.id, projectDef, entityId, ProjectEntity.Type.SCENE, syncId)
            if (result.isSuccess) {
                val entity = result.getOrThrow() as ProjectEntity.SceneEntity
                val response = LoadSceneResponse(
                    id = entity.id,
                    order = entity.order,
                    name = entity.name,
                    path = entity.path,
                    content = entity.content
                )
                call.respond(response)
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