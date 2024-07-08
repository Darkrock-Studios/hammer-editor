package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.DeleteIdsResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_ENTITY_HASH
import com.darkrockstudios.apps.hammer.base.http.HEADER_ENTITY_TYPE
import com.darkrockstudios.apps.hammer.base.http.HEADER_ORIGINAL_HASH
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.SaveEntityResponse
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.project.synchronizers.serverEntityHash
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.logging.Logger
import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.uncompress
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.ktor.ext.get
import kotlin.IllegalArgumentException
import kotlin.coroutines.CoroutineContext
import kotlin.toString

fun Route.projectRoutes(logger: Logger) {
	authenticate(USER_AUTH) {
		route("/project/{userId}/{projectName}") {
			beginProjectSync()
			endProjectSync()
			uploadEntity()
			downloadEntity(logger)
			deleteEntity()
		}
	}
}

private fun Route.beginProjectSync() {
	val projectRepository: ProjectRepository = get()
	val json: Json = get()
	val ioDispatcher: CoroutineContext = get(named(DISPATCHER_IO))

	post("/begin_sync") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val lite = call.parameters["lite"]?.toBoolean() ?: false

		val clientState: ClientEntityState? = withContext(ioDispatcher) {
			val compressed = call.receiveStream().readAllBytes()
			if (compressed.isNotEmpty()) {
				val jsonStr = String(compressed.uncompress(GZIP))
				json.decodeFromString<ClientEntityState>(jsonStr)
			} else {
				null
			}
		}

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api.project.sync.error.projectnamemissing"))
				)
			)
		} else {
			val projectDef = ProjectDefinition(projectName)
			val result =
				projectRepository.beginProjectSync(principal.id, projectDef, clientState, lite)
			if (isSuccess(result)) {
				val syncBegan = result.data
				call.respond(syncBegan)
			} else {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Failed to begin sync",
						displayMessage = result.displayMessageText(call, R("api.error.unknown"))
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

		val formParameters = call.receiveParameters()
		val lastSync = try {
			Instant.parse(formParameters["lastSync"].toString())
		} catch (e: IllegalArgumentException) {
			null
		}
		val lastId = formParameters["lastId"].toString().toIntOrNull()

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api.project.sync.error.projectnamemissing"))
				)
			)
		} else if (syncId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api.project.sync.error.syncidmissing"))
				)
			)
		} else {
			val projectDef = ProjectDefinition(projectName)
			val result =
				projectRepository.endProjectSync(principal.id, projectDef, syncId, lastSync, lastId)
			if (isSuccess(result)) {
				val success = result.data
				call.respond(success)
			} else {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Failed to begin sync",
						displayMessage = result.displayMessageText(call, R("api.error.unknown"))
					)
				)
			}
		}
	}
}

private fun Route.uploadEntity() {
	val projectRepository: ProjectRepository = get()

	post("/upload_entity/{entityId}") {
		val log = call.application.log
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val entityId = call.parameters["entityId"]?.toIntOrNull()
		val syncId = call.request.headers[HEADER_SYNC_ID]
		val originalHash = call.request.headers[HEADER_ORIGINAL_HASH]
		val force = call.request.queryParameters["force"]?.toBooleanStrictOrNull()

		val entityTypeHeader = call.request.headers[HEADER_ENTITY_TYPE]
		val type = ApiProjectEntity.Type.fromString(entityTypeHeader ?: "")
		if (type == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Header",
					displayMessage = call.t(R("api.project.error.entitytypemissing"))
				)
			)
		} else {
			val entity = when (type) {
				ApiProjectEntity.Type.SCENE -> call.receive<ApiProjectEntity.SceneEntity>()
				ApiProjectEntity.Type.NOTE -> call.receive<ApiProjectEntity.NoteEntity>()
				ApiProjectEntity.Type.TIMELINE_EVENT -> call.receive<ApiProjectEntity.TimelineEventEntity>()
				ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> call.receive<ApiProjectEntity.EncyclopediaEntryEntity>()
				ApiProjectEntity.Type.SCENE_DRAFT -> call.receive<ApiProjectEntity.SceneDraftEntity>()
			}

			if (projectName == null) {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Missing Parameter",
						displayMessage = call.t(R("api.project.sync.error.projectnamemissing"))
					)
				)
			} else if (entityId == null) {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Missing Parameter",
						displayMessage = call.t(R("api.project.error.entityidmissing"))
					)
				)
			} else if (syncId.isNullOrBlank()) {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Missing Parameter",
						displayMessage = call.t(R("api.project.sync.error.syncidmissing"))
					)
				)
			} else {
				val projectDef = ProjectDefinition(projectName)
				val result =
					projectRepository.saveEntity(
						principal.id,
						projectDef,
						entity,
						originalHash,
						syncId,
						force ?: false
					)
				if (isSuccess(result)) {
					call.respond(SaveEntityResponse(result.data))
				} else {
					val e = result.exception
					when (e) {
						is EntityConflictException -> {
							if (call.application.environment.developmentMode) {
								val serverHash = serverEntityHash(e.entity)
								log.info("Conflict for ID $entityId client provided original hash: $originalHash server hash: $serverHash")
							}

							when (val conflictedEntity = e.entity) {
								is ApiProjectEntity.SceneEntity -> call.respond(
									status = HttpStatusCode.Conflict,
									conflictedEntity
								)

								is ApiProjectEntity.NoteEntity -> call.respond(
									status = HttpStatusCode.Conflict,
									conflictedEntity
								)

								is ApiProjectEntity.TimelineEventEntity -> call.respond(
									status = HttpStatusCode.Conflict,
									conflictedEntity
								)

								is ApiProjectEntity.EncyclopediaEntryEntity -> call.respond(
									status = HttpStatusCode.Conflict,
									conflictedEntity
								)

								is ApiProjectEntity.SceneDraftEntity -> call.respond(
									status = HttpStatusCode.Conflict,
									conflictedEntity
								)
							}
						}

						is EntityTypeConflictException -> {
							call.respond(
								status = HttpStatusCode.Conflict,
								HttpResponseError(
									error = e.message ?: "Entity Type Conflict",
									displayMessage = result.displayMessageText(
										call,
										R("api.error.unknown")
									)
								)
							)
							log.warn(e.message)
						}

						else -> {
							call.respond(
								status = HttpStatusCode.ExpectationFailed,
								HttpResponseError(
									error = "Save Error",
									displayMessage = result.displayMessageText(
										call,
										R("api.error.unknown")
									),
								)
							)
						}
					}
				}
			}
		}
	}
}

private fun Route.downloadEntity(log: Logger) {
	val projectRepository: ProjectRepository = get()

	get("/download_entity/{entityId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val entityId = call.parameters["entityId"]?.toIntOrNull()
		val entityHash = call.request.headers[HEADER_ENTITY_HASH]
		val syncId = call.request.headers[HEADER_SYNC_ID]

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api.project.sync.error.projectnamemissing"))
				)
			)
		} else if (entityId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api.project.error.entityidmissing"))
				)
			)
		} else if (syncId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api.project.sync.error.syncidmissing"))
				)
			)
		} else {
			val projectDef = ProjectDefinition(projectName)

			val result =
				projectRepository.loadEntity(principal.id, projectDef, entityId, syncId)
			if (isSuccess(result)) {
				val serverEntity = result.data
				val serverEntityHash = serverEntityHash(serverEntity)

				if (entityHash != null && entityHash == serverEntityHash) {
					call.respond(HttpStatusCode.NotModified)
				} else {
					log.info("Entity Download for ID $entityId because hash mismatched:\nClient: $entityHash\nServer: $serverEntityHash")
					call.response.headers.append(HEADER_ENTITY_TYPE, serverEntity.type.toString())
					when (serverEntity) {
						is ApiProjectEntity.SceneEntity -> call.respond(serverEntity)
						is ApiProjectEntity.NoteEntity -> call.respond(serverEntity)
						is ApiProjectEntity.TimelineEventEntity -> call.respond(serverEntity)
						is ApiProjectEntity.EncyclopediaEntryEntity -> call.respond(serverEntity)
						is ApiProjectEntity.SceneDraftEntity -> call.respond(serverEntity)
					}
				}
			} else {
				when (val e = result.exception) {
					is EntityConflictException -> {
						call.respond(
							status = HttpStatusCode.Conflict,
							HttpResponseError(
								error = "Download Error",
								displayMessage = result.displayMessageText(
									call,
									R("api.error.unknown")
								)
							)
						)
					}

					is EntityNotFound -> {
						call.respond(
							status = HttpStatusCode.NotFound,
							HttpResponseError(
								error = "Download Error",
								result.displayMessageText(call, R("api.error.unknown"))
							)
						)
					}

					else -> {
						log.error("Entity Download failed for ID $entityId: " + e?.message)
						call.respond(
							status = HttpStatusCode.InternalServerError,
							HttpResponseError(
								error = "Download Error",
								result.displayMessageText(call, R("api.error.unknown"))
							)
						)
					}
				}
			}
		}
	}
}

private fun Route.deleteEntity() {
	val projectRepository: ProjectRepository = get()

	get("/delete_entity/{entityId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val entityId = call.parameters["entityId"]?.toIntOrNull()
		val syncId = call.request.headers[HEADER_SYNC_ID]

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api.project.sync.error.projectnamemissing"))
				)
			)
		} else if (entityId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Entity Id",
					displayMessage = call.t(R("api.project.error.entityidmissing"))
				)
			)
		} else if (syncId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api.project.sync.error.syncidmissing"))
				)
			)
		} else {
			val projectDef = ProjectDefinition(projectName)
			val result = projectRepository.deleteEntity(principal.id, projectDef, entityId, syncId)

			if (isSuccess(result)) {
				call.respond(HttpStatusCode.OK, DeleteIdsResponse(true))
			} else {
				val e = result.exception
				if (e is NoEntityTypeFound) {
					call.respond(HttpStatusCode.OK, DeleteIdsResponse(false))
				} else {
					call.respond(
						status = HttpStatusCode.InternalServerError,
						HttpResponseError(
							error = "Failed to delete Entity",
							result.displayMessageText(call, R("api.error.unknown"))
						)
					)
				}
			}
		}
	}
}