package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.*
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.project.synchronizers.serverEntityHash
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.uncompress
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.ktor.ext.get
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

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
	val projectEntityRepository: ProjectEntityRepository = get()
	val json: Json = get()
	val ioDispatcher: CoroutineContext = get(named(DISPATCHER_IO))

	post("/begin_sync") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val projectIdRaw = call.request.queryParameters["projectId"]
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
					displayMessage = call.t(R("api_project_sync_error_projectnamemissing"))
				)
			)
		} else if (projectIdRaw == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_projectidmissing"))
				)
			)
		} else {
			val projectDef = ProjectDefinition(projectName, ProjectId(projectIdRaw))
			val result =
				projectEntityRepository.beginProjectSync(
					principal.id,
					projectDef,
					clientState,
					lite
				)
			if (isSuccess(result)) {
				val syncBegan = result.data
				call.respond(syncBegan)
			} else {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Failed to begin sync",
						displayMessage = result.displayMessageText(call, R("api_error_unknown"))
					)
				)
			}
		}
	}
}

private fun Route.endProjectSync() {
	val projectEntityRepository: ProjectEntityRepository = get()

	post("/end_sync") {
		val log = call.application.log
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val projectIdRaw = call.request.queryParameters["projectId"]
		val syncId = call.request.headers[HEADER_SYNC_ID]

		log.info("end_sync: userId=${principal.id}, project=$projectName, projectId=$projectIdRaw, syncId=$syncId")

		val formParameters = try {
			call.receiveParameters()
		} catch (e: Exception) {
			log.error("end_sync: Failed to read request body", e)
			throw e
		}

		val lastSync = try {
			Instant.parse(formParameters["lastSync"].toString())
		} catch (e: IllegalArgumentException) {
			null
		}
		val lastId = formParameters["lastId"].toString().toIntOrNull()

		log.info("end_sync: parsed lastSync=$lastSync, lastId=$lastId")

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_projectnamemissing"))
				)
			)
		} else if (projectIdRaw == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_projectidmissing"))
				)
			)
		} else if (syncId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_syncidmissing"))
				)
			)
		} else {
			val projectDef = ProjectDefinition(projectName, ProjectId(projectIdRaw))
			val result =
				projectEntityRepository.endProjectSync(
					principal.id,
					projectDef,
					syncId,
					lastSync,
					lastId
				)
			if (isSuccess(result)) {
				log.info("end_sync: success for project=$projectName")
				val success = result.data
				call.respond(success)
			} else {
				log.warn("end_sync: failed for project=$projectName - ${result.error}")
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Failed to end sync",
						displayMessage = result.displayMessageText(call, R("api_error_unknown"))
					)
				)
			}
		}
	}
}

private fun Route.uploadEntity() {
	val projectEntityRepository: ProjectEntityRepository = get()

	post("/upload_entity/{entityId}") {
		val log = call.application.log
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val projectIdRaw = call.request.queryParameters["projectId"]
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
					displayMessage = call.t(R("api_project_error_entitytypemissing"))
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
						displayMessage = call.t(R("api_project_sync_error_projectnamemissing"))
					)
				)
			} else if (projectIdRaw == null) {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Missing Parameter",
						displayMessage = call.t(R("api_project_sync_error_projectidmissing"))
					)
				)
			} else if (entityId == null) {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Missing Parameter",
						displayMessage = call.t(R("api_project_error_entityidmissing"))
					)
				)
			} else if (syncId.isNullOrBlank()) {
				call.respond(
					status = HttpStatusCode.BadRequest,
					HttpResponseError(
						error = "Missing Parameter",
						displayMessage = call.t(R("api_project_sync_error_syncidmissing"))
					)
				)
			} else {
				val projectDef = ProjectDefinition(projectName, ProjectId(projectIdRaw))
				val result =
					projectEntityRepository.saveEntity(
						principal.id,
						projectDef,
						entity,
						originalHash,
						syncId,
						force ?: false
					)
				if (isSuccess(result)) {
					call.respond(SaveEntityResponse(result.isSuccess))
				} else {
					val e = result.exception
					when (e) {
						is EntityConflictException -> {
							if (call.application.developmentMode) {
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
										R("api_error_unknown")
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
										R("api_error_unknown")
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
	val projectEntityRepository: ProjectEntityRepository = get()

	get("/download_entity/{entityId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val projectIdRaw = call.request.queryParameters["projectId"]
		val entityId = call.parameters["entityId"]?.toIntOrNull()
		val entityHash = call.request.headers[HEADER_ENTITY_HASH]
		val syncId = call.request.headers[HEADER_SYNC_ID]

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_projectnamemissing"))
				)
			)
		} else if (projectIdRaw == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_projectidmissing"))
				)
			)
		} else if (entityId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_error_entityidmissing"))
				)
			)
		} else if (syncId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_syncidmissing"))
				)
			)
		} else {
			val projectDef = ProjectDefinition(projectName, ProjectId(projectIdRaw))

			val result =
				projectEntityRepository.loadEntity(principal.id, projectDef, entityId, syncId)
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
									R("api_error_unknown")
								)
							)
						)
					}

					is EntityNotFound -> {
						call.respond(
							status = HttpStatusCode.NotFound,
							HttpResponseError(
								error = "Download Error",
								result.displayMessageText(call, R("api_error_unknown"))
							)
						)
					}

					else -> {
						log.error("Entity Download failed for ID $entityId: " + e?.message)
						call.respond(
							status = HttpStatusCode.InternalServerError,
							HttpResponseError(
								error = "Download Error",
								result.displayMessageText(call, R("api_error_unknown"))
							)
						)
					}
				}
			}
		}
	}
}

private fun Route.deleteEntity() {
	val projectEntityRepository: ProjectEntityRepository = get()

	get("/delete_entity/{entityId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectName = call.parameters["projectName"]
		val projectIdRaw = call.request.queryParameters["projectId"]
		val entityId = call.parameters["entityId"]?.toIntOrNull()
		val syncId = call.request.headers[HEADER_SYNC_ID]

		if (projectName == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_projectnamemissing"))
				)
			)
		} else if (projectIdRaw == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_projectidmissing"))
				)
			)
		} else if (entityId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Entity Id",
					displayMessage = call.t(R("api_project_error_entityidmissing"))
				)
			)
		} else if (syncId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_project_sync_error_syncidmissing"))
				)
			)
		} else {
			val projectDef = ProjectDefinition(projectName, ProjectId(projectIdRaw))
			val result =
				projectEntityRepository.deleteEntity(principal.id, projectDef, entityId, syncId)

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
							result.displayMessageText(call, R("api_error_unknown"))
						)
					)
				}
			}
		}
	}
}
