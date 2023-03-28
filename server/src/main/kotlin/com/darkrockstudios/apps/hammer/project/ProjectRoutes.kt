package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.*
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
				getProjectLastSync()
				uploadScene()
				downloadScene()
				setSyncData()
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
		val syncId = call.request.headers[HEADER_SYNC_ID]

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

private fun Route.getProjectLastSync() {
	val projectRepository: ProjectRepository = get()

	get("/last_sync") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val syncId = call.request.headers[HEADER_SYNC_ID]

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
			val result = projectRepository.getProjectLastSync(principal.id, ProjectDefinition(projectName), syncId)
			if (result.isSuccess) {
				val syncData = result.getOrThrow()
				call.respond(HasProjectResponse(lastSync = syncData.lastSync, lastId = syncData.lastId))
			} else {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Failed to check if project exists",
						message = result.exceptionOrNull()?.message ?: "Unknown"
					)
				)
			}
		}
    }
}

private fun Route.uploadScene() {
    val projectRepository: ProjectRepository = get()

    post("/upload_scene/{entityId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val entityId = call.parameters["entityId"]?.toIntOrNull()

		val formParameters = call.receiveParameters()
		val sceneName = formParameters["sceneName"].toString()
		val sceneContent = formParameters["sceneContent"].toString()
		val sceneOrder = formParameters["sceneOrder"].toString().toIntOrNull()
		val scenePath = formParameters["scenePath"].toString()
		val sceneTypeName = formParameters["sceneType"].toString()
		val path = scenePath.trim().split("/").map { it.toInt() }
		val syncId = call.request.headers[HEADER_SYNC_ID]

		val sceneType = ApiSceneType.fromString(sceneTypeName)

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
		} else if (sceneContent.isBlank()) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Parameter", message = "sceneContent was missing")
			)
		} else if (sceneOrder == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Parameter", message = "sceneOrder was missing")
			)
		} else if (sceneType == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Parameter", message = "sceneType was missing")
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
			val entity = ApiProjectEntity.SceneEntity(
				id = entityId,
				order = sceneOrder,
				sceneType = sceneType,
				name = sceneName,
				path = path,
				content = sceneContent
			)

			val result = projectRepository.saveEntity(principal.id, projectDef, entity, syncId)
			if (result.isSuccess) {
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
		val syncId = call.request.headers[HEADER_SYNC_ID]

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
				projectRepository.loadEntity(principal.id, projectDef, entityId, ApiProjectEntity.Type.SCENE, syncId)
            if (result.isSuccess) {
				val entity = result.getOrThrow() as ApiProjectEntity.SceneEntity
				val response = LoadSceneResponse(
					id = entity.id,
					sceneType = entity.sceneType,
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

private fun Route.setSyncData() {
	val projectRepository: ProjectRepository = get()

	post("/set_sync_data") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val syncId = call.request.headers[HEADER_SYNC_ID]

		val formParameters = call.receiveParameters()
		val lastSync = formParameters["lastSync"].toString().toLongOrNull()
		val lastId = formParameters["lastId"].toString().toIntOrNull()

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Parameter", message = "projectName was missing")
			)
		} else if (syncId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Header", message = "syncId was missing")
			)
		} else if (lastSync == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Parameter", message = "lastSync was missing")
			)
		} else if (lastId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(error = "Missing Parameter", message = "lastId was missing")
			)
		} else {
			val projectDef = ProjectDefinition(projectName)
			val result = projectRepository.setProjectSyncData(principal.id, projectDef, syncId, lastId)
			if (result.isSuccess) {
				call.respond(HttpStatusCode.OK)
			} else {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Failed to set sync data",
						message = result.exceptionOrNull()?.message ?: "Unknown"
					)
				)
			}
		}
	}
}