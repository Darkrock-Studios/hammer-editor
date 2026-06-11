package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.DeleteIdsResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_ENTITY_HASH
import com.darkrockstudios.apps.hammer.base.http.HEADER_ENTITY_TYPE
import com.darkrockstudios.apps.hammer.base.http.HEADER_ORIGINAL_HASH
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.SaveEntityResponse
import com.darkrockstudios.apps.hammer.base.http.StaleHashResponse
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataUploadRequest
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.utilities.ERROR_MISSING_ENTITY_ID
import com.darkrockstudios.apps.hammer.utilities.ERR_KEY_UNKNOWN
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utilities.requireEntityId
import com.darkrockstudios.apps.hammer.utilities.requireProjectDef
import com.darkrockstudios.apps.hammer.utilities.requireSyncId
import com.darkrockstudios.apps.hammer.utilities.respondMissingParameter
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.logging.Logger
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
			getWritingActivity()
			uploadWritingActivity()
			getProjectData()
			uploadProjectData()
		}
	}
}

private fun Route.beginProjectSync() {
	val projectEntityRepository: ProjectEntityRepository = get()
	val accountsRepository: AccountsRepository = get()
	val json: Json = get()
	val ioDispatcher: CoroutineContext = get(named(DISPATCHER_IO))

	post("/begin_sync") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectDef = call.requireProjectDef() ?: return@post
		val lite = call.parameters["lite"]?.toBoolean() ?: false

		// Derived from the authenticated token (not client-asserted)
		val installId = call.request.headers[HttpHeaders.Authorization]
			?.substringAfter("Bearer ", "")
			?.takeIf { it.isNotBlank() }
			?.let { accountsRepository.getInstallId(it) }

		val clientState: ClientEntityState? = withContext(ioDispatcher) {
			val compressed = call.receiveStream().readAllBytes()
			if (compressed.isNotEmpty()) {
				val jsonStr = String(compressed.uncompress(GZIP))
				json.decodeFromString<ClientEntityState>(jsonStr)
			} else {
				null
			}
		}

		val result = projectEntityRepository.beginProjectSync(
			principal.id,
			projectDef,
			clientState,
			lite,
			installId,
		)
		if (isSuccess(result)) {
			call.respond(result.data)
		} else {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Failed to begin sync",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private fun Route.endProjectSync() {
	val projectEntityRepository: ProjectEntityRepository = get()

	post("/end_sync") {
		val log = call.application.log
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectDef = call.requireProjectDef() ?: return@post
		val syncId = call.requireSyncId() ?: return@post

		log.info("end_sync: userId=${principal.id}, project=${projectDef.name}, projectId=${projectDef.uuid}, syncId=$syncId")

		val formParameters = try {
			call.receiveParameters()
			// Log the read failure, then let the route's error handling take over.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			log.error("end_sync: Failed to read request body", e)
			throw e
		}

		val lastSync = try {
			Instant.parse(formParameters["lastSync"].toString())
			// Unparseable timestamp is treated as absent.
		} catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
			null
		}
		val lastId = formParameters["lastId"].toString().toIntOrNull()

		log.info("end_sync: parsed lastSync=$lastSync, lastId=$lastId")

		val result = projectEntityRepository.endProjectSync(
			principal.id,
			projectDef,
			syncId,
			lastSync,
			lastId,
		)
		if (isSuccess(result)) {
			log.info("end_sync: success for project=${projectDef.name}")
			call.respond(result.data)
		} else {
			log.warn("end_sync: failed for project=${projectDef.name} - ${result.error}")
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Failed to end sync",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private fun Route.uploadEntity() {
	val projectEntityRepository: ProjectEntityRepository = get()

	post("/upload_entity/{entityId}") {
		val log = call.application.log
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val originalHash = call.request.headers[HEADER_ORIGINAL_HASH]
		val force = call.request.queryParameters["force"]?.toBooleanStrictOrNull()

		val entityTypeHeader = call.request.headers[HEADER_ENTITY_TYPE]
		val type = ApiProjectEntity.Type.fromString(entityTypeHeader ?: "")
		if (type == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Header",
					displayMessage = call.t(R("api_project_error_entitytypemissing")),
				),
			)
			return@post
		}

		val entity = when (type) {
			ApiProjectEntity.Type.SCENE -> call.receive<ApiProjectEntity.SceneEntity>()
			ApiProjectEntity.Type.NOTE -> call.receive<ApiProjectEntity.NoteEntity>()
			ApiProjectEntity.Type.TIMELINE_EVENT -> call.receive<ApiProjectEntity.TimelineEventEntity>()
			ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> call.receive<ApiProjectEntity.EncyclopediaEntryEntity>()
			ApiProjectEntity.Type.SCENE_DRAFT -> call.receive<ApiProjectEntity.SceneDraftEntity>()
		}

		val projectDef = call.requireProjectDef() ?: return@post
		val entityId = call.requireEntityId() ?: return@post
		val syncId = call.requireSyncId() ?: return@post

		val result = projectEntityRepository.saveEntity(
			principal.id,
			projectDef,
			entity,
			originalHash,
			syncId,
			force ?: false,
		)
		if (isSuccess(result)) {
			call.respond(SaveEntityResponse(result.isSuccess))
		} else {
			respondUploadEntityFailure(log, entityId, originalHash, result)
		}
	}
}

private suspend fun RoutingContext.respondUploadEntityFailure(
	log: Logger,
	entityId: Int,
	originalHash: String?,
	result: ServerResult.Failure<Unit>,
) {
	when (val e = result.exception) {
		is EntityConflictException -> {
			if (call.application.developmentMode) {
				val serverHash = e.entity.hash()
				log.info("Conflict for ID $entityId client provided original hash: $originalHash server hash: $serverHash")
			}
			respondConflictedEntity(e.entity)
		}

		is EntityTypeConflictException -> {
			call.respond(
				status = HttpStatusCode.Conflict,
				HttpResponseError(
					error = e.message ?: "Entity Type Conflict",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
			log.warn(e.message)
		}

		is EntityTooLargeException -> {
			call.respond(
				status = HttpStatusCode.PayloadTooLarge,
				HttpResponseError(
					error = e.message ?: "Entity too large",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
			log.warn(e.message)
		}

		else -> {
			call.respond(
				status = HttpStatusCode.ExpectationFailed,
				HttpResponseError(
					error = "Save Error",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private suspend fun RoutingContext.respondConflictedEntity(entity: ApiProjectEntity) {
	when (entity) {
		is ApiProjectEntity.SceneEntity -> call.respond(HttpStatusCode.Conflict, entity)
		is ApiProjectEntity.NoteEntity -> call.respond(HttpStatusCode.Conflict, entity)
		is ApiProjectEntity.TimelineEventEntity -> call.respond(HttpStatusCode.Conflict, entity)
		is ApiProjectEntity.EncyclopediaEntryEntity -> call.respond(HttpStatusCode.Conflict, entity)
		is ApiProjectEntity.SceneDraftEntity -> call.respond(HttpStatusCode.Conflict, entity)
	}
}

private fun Route.downloadEntity(log: Logger) {
	val projectEntityRepository: ProjectEntityRepository = get()

	get("/download_entity/{entityId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val entityHash = call.request.headers[HEADER_ENTITY_HASH]

		val projectDef = call.requireProjectDef() ?: return@get
		val entityId = call.requireEntityId() ?: return@get
		val syncId = call.requireSyncId() ?: return@get

		val cachedHash = projectEntityRepository.getCachedHash(principal.id, projectDef, entityId)
		val result = projectEntityRepository.loadEntity(principal.id, projectDef, entityId, syncId)

		if (isSuccess(result)) {
			respondDownloadedEntity(log, entityId, entityHash, cachedHash, result.data)
		} else {
			respondDownloadFailure(log, entityId, result)
		}
	}
}

private suspend fun RoutingContext.respondDownloadedEntity(
	log: Logger,
	entityId: Int,
	entityHash: String?,
	cachedHash: String?,
	serverEntity: ApiProjectEntity,
) {
	val serverEntityHash = serverEntity.hash()

	if (cachedHash != null && cachedHash != serverEntityHash) {
		log.warn("Stale hash detected for entity $entityId. Cached: $cachedHash, Computed: $serverEntityHash")
		call.respond(
			status = HttpStatusCode.PreconditionFailed,
			StaleHashResponse(
				entityId = entityId,
				message = "Server cached hash is stale",
				cachedHash = cachedHash,
				computedHash = serverEntityHash,
			),
		)
		return
	}
	if (entityHash != null && entityHash == serverEntityHash) {
		call.respond(HttpStatusCode.NotModified)
		return
	}

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

private suspend fun RoutingContext.respondDownloadFailure(
	log: Logger,
	entityId: Int,
	result: ServerResult.Failure<ApiProjectEntity>,
) {
	when (val e = result.exception) {
		is EntityConflictException -> call.respond(
			status = HttpStatusCode.Conflict,
			HttpResponseError(
				error = "Download Error",
				displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
			),
		)

		is EntityNotFound -> call.respond(
			status = HttpStatusCode.NotFound,
			HttpResponseError(
				error = "Download Error",
				result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
			),
		)

		else -> {
			log.error("Entity Download failed for ID $entityId: " + e?.message)
			call.respond(
				status = HttpStatusCode.InternalServerError,
				HttpResponseError(
					error = "Download Error",
					result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private fun Route.getWritingActivity() {
	val repository: ServerWritingActivityRepository = get()

	get("/writing_activity") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectDef = call.requireProjectDef() ?: return@get

		val result = repository.loadAll(principal.id, projectDef)
		if (isSuccess(result)) {
			call.respond(result.data)
		} else {
			call.respond(
				status = HttpStatusCode.NotFound,
				HttpResponseError(
					error = "Failed to load writing activity",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private fun Route.uploadWritingActivity() {
	val repository: ServerWritingActivityRepository = get()

	post("/writing_activity/{deviceId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectDef = call.requireProjectDef() ?: return@post
		val deviceId = call.parameters["deviceId"]
		if (deviceId.isNullOrBlank()) {
			call.respondMissingParameter("api_project_writingactivity_error_deviceidmissing")
			return@post
		}

		val log = call.receive<DeviceLog>()
		val result = repository.saveDeviceLog(principal.id, projectDef, deviceId, log)
		if (isSuccess(result)) {
			call.respond(HttpStatusCode.OK)
		} else {
			call.respond(
				status = HttpStatusCode.NotFound,
				HttpResponseError(
					error = "Failed to save writing activity",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private fun Route.getProjectData() {
	val repository: ServerProjectDataRepository = get()

	get("/project_data") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectDef = call.requireProjectDef() ?: return@get

		val result = repository.load(principal.id, projectDef)
		if (isSuccess(result)) {
			val dto = result.data
			if (dto == null) {
				call.respond(HttpStatusCode.NoContent)
			} else {
				call.respond(dto)
			}
		} else {
			call.respond(
				status = HttpStatusCode.NotFound,
				HttpResponseError(
					error = "Failed to load project data",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private fun Route.uploadProjectData() {
	val repository: ServerProjectDataRepository = get()

	post("/project_data") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectDef = call.requireProjectDef() ?: return@post

		val request = call.receive<ProjectDataUploadRequest>()
		val result = repository.save(
			userId = principal.id,
			projectDef = projectDef,
			data = request.data,
			originalHash = request.originalHash,
		)
		if (isSuccess(result)) {
			when (val outcome = result.data) {
				is ProjectDataSaveResult.Saved -> call.respond(HttpStatusCode.OK, outcome.dto)
				is ProjectDataSaveResult.Conflict -> call.respond(HttpStatusCode.Conflict, outcome.conflict)
			}
		} else {
			call.respond(
				status = HttpStatusCode.NotFound,
				HttpResponseError(
					error = "Failed to save project data",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private fun Route.deleteEntity() {
	val projectEntityRepository: ProjectEntityRepository = get()

	get("/delete_entity/{entityId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val projectDef = call.requireProjectDef() ?: return@get
		val entityId = call.requireEntityId(error = ERROR_MISSING_ENTITY_ID) ?: return@get
		val syncId = call.requireSyncId() ?: return@get

		val result = projectEntityRepository.deleteEntity(principal.id, projectDef, entityId, syncId)

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
						result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
					),
				)
			}
		}
	}
}
