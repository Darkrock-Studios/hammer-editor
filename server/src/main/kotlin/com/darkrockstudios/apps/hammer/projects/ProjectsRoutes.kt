package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.http.ProjectsSyncProbeRequest
import com.darkrockstudios.apps.hammer.base.http.ProjectsSyncProbeResponse
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.project.GroupCycleException
import com.darkrockstudios.apps.hammer.project.GroupIdTakenException
import com.darkrockstudios.apps.hammer.project.GroupNameTaken
import com.darkrockstudios.apps.hammer.project.GroupTooDeepException
import com.darkrockstudios.apps.hammer.project.InvalidGroupDescription
import com.darkrockstudios.apps.hammer.project.InvalidGroupName
import com.darkrockstudios.apps.hammer.project.InvalidProjectName
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.monitoring.ActivityType
import com.darkrockstudios.apps.hammer.monitoring.UserActivityCollector
import com.darkrockstudios.apps.hammer.project.ProjectDeletedException
import com.darkrockstudios.apps.hammer.project.ProjectIdTakenException
import com.darkrockstudios.apps.hammer.project.ProjectNameTaken
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.storyideas.ServerIdeasRepository
import com.darkrockstudios.apps.hammer.utilities.*
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

internal const val ERROR_GENERIC = "Error"
private const val ERR_KEY_INVALID_PROJECT_NAME = "api_project_rename_error_invalidname"
private const val ERR_KEY_PROJECT_NAME_TAKEN = "api_project_rename_error_nametaken"
private const val ERR_KEY_PROJECT_NOT_FOUND = "api_project_getproject_error_notfound"
private const val ERR_KEY_PROJECT_CREATE_DELETED = "api_projects_create_error_deleted"
private const val ERR_KEY_PROJECT_CREATE_ID_TAKEN = "api_projects_create_error_id_taken"
internal const val ERR_KEY_GROUP_ID_MISSING = "api_projects_group_error_groupidmissing"
private const val ERR_KEY_GROUP_INVALID_NAME = "api_projects_group_error_invalidname"
private const val ERR_KEY_GROUP_INVALID_DESCRIPTION = "api_projects_group_error_invaliddescription"
private const val ERR_KEY_GROUP_NAME_TAKEN = "api_projects_group_error_nametaken"
private const val ERR_KEY_GROUP_CYCLE = "api_projects_group_error_cycle"
private const val ERR_KEY_GROUP_TOO_DEEP = "api_projects_group_error_toodeep"
private const val ERR_KEY_GROUP_ID_TAKEN = "api_projects_group_error_id_taken"

/** Reads syncId from header, responding 400 "Missing Header" with a localized message if absent. */
internal suspend fun ApplicationCall.requireSyncIdFromHeader(): String? {
	val syncId = request.headers[HEADER_SYNC_ID]
	if (syncId == null) {
		respondMissingHeader(ERR_KEY_SYNC_ID_MISSING)
		return null
	}
	return syncId
}

/** Reads projectId from path params, responding 400 "Missing Parameter" with a localized message if absent. */
private suspend fun ApplicationCall.requireProjectIdFromPath(): ProjectId? {
	val projectIdRaw = parameters["projectId"]
	if (projectIdRaw == null) {
		respondMissingParameter(ERR_KEY_PROJECT_ID_MISSING)
		return null
	}
	return ProjectId(projectIdRaw)
}

/**
 * Reads projectId from the query, responding 400 with a localized message if absent or not a UUID.
 * The id is canonicalized so responses compare equal to what the database returns.
 */
private suspend fun ApplicationCall.requireProjectIdFromQuery(): ProjectId? {
	val uuid = request.queryParameters["projectId"]?.let(::parseUuidOrNull)
	if (uuid == null) {
		respondMissingParameter(ERR_KEY_PROJECT_ID_MISSING)
		return null
	}
	return ProjectId.fromUUID(uuid)
}

/** Canonical form of a client-sent group id, or null when it is not a UUID. */
internal fun canonicalGroupId(raw: String): GroupId? = parseUuidOrNull(raw)?.let { GroupId.fromUUID(it) }

/** Where a project goes: [groupId] is null at the top level. */
private class GroupPlacement(val groupId: GroupId?)

/**
 * Reads the optional `groupId` query parameter, absent meaning the top level. Responds 400 and
 * returns null when it is present but not a UUID.
 */
private suspend fun ApplicationCall.readGroupPlacementFromQuery(): GroupPlacement? {
	val raw = request.queryParameters["groupId"] ?: return GroupPlacement(null)
	val groupId = canonicalGroupId(raw)
	if (groupId == null) {
		respondMissingParameter(ERR_KEY_GROUP_ID_MISSING)
		return null
	}
	return GroupPlacement(groupId)
}

/** Reads the required `groupId` query parameter, responding 400 if absent or not a UUID. */
internal suspend fun ApplicationCall.requireGroupIdFromQuery(): GroupId? {
	val groupId = request.queryParameters["groupId"]?.let(::canonicalGroupId)
	if (groupId == null) {
		respondMissingParameter(ERR_KEY_GROUP_ID_MISSING)
		return null
	}
	return groupId
}

private suspend fun ApplicationCall.respondNotAcceptable(messageKey: String) = respond(
	status = HttpStatusCode.NotAcceptable,
	HttpResponseError(error = ERROR_GENERIC, displayMessage = t(R(messageKey))),
)

private suspend fun ApplicationCall.respondConflict(messageKey: String, errorCode: String) = respond(
	status = HttpStatusCode.Conflict,
	HttpResponseError(error = ERROR_GENERIC, displayMessage = t(R(messageKey)), errorCode = errorCode),
)

/** Handles common failure exceptions for sync-id-based operations. */
internal suspend fun ApplicationCall.respondSyncFailure(exception: Throwable?) {
	when (exception) {
		is InvalidSyncIdException -> respondBadRequest(ERROR_GENERIC, t(R(ERR_KEY_INVALID_SYNC_ID)))
		is InvalidProjectName -> respondNotAcceptable(ERR_KEY_INVALID_PROJECT_NAME)

		is ProjectDeletedException -> respondConflict(ERR_KEY_PROJECT_CREATE_DELETED, ApiErrorCode.PROJECT_DELETED)
		is ProjectIdTakenException -> respondConflict(ERR_KEY_PROJECT_CREATE_ID_TAKEN, ApiErrorCode.PROJECT_ID_TAKEN)

		is InvalidGroupName -> respondNotAcceptable(ERR_KEY_GROUP_INVALID_NAME)
		is InvalidGroupDescription -> respondNotAcceptable(ERR_KEY_GROUP_INVALID_DESCRIPTION)

		is GroupNameTaken -> respondConflict(ERR_KEY_GROUP_NAME_TAKEN, ApiErrorCode.GROUP_NAME_TAKEN)
		is GroupCycleException -> respondConflict(ERR_KEY_GROUP_CYCLE, ApiErrorCode.GROUP_CYCLE)
		is GroupTooDeepException -> respondConflict(ERR_KEY_GROUP_TOO_DEEP, ApiErrorCode.GROUP_TOO_DEEP)
		is GroupIdTakenException -> respondConflict(ERR_KEY_GROUP_ID_TAKEN, ApiErrorCode.GROUP_ID_TAKEN)

		else -> respond(
			status = HttpStatusCode.InternalServerError,
			HttpResponseError(
				error = ERROR_GENERIC,
				displayMessage = exception?.message ?: t(R(ERR_KEY_UNKNOWN)),
			),
		)
	}
}

fun Route.projectsRoutes() {
	authenticate(USER_AUTH) {
		route("/projects/{userId}") {
			beginProjectsSync()
			endProjectSync()
			deleteProject()
			createProject()
			renameProject()
			moveProject()
			syncProbe()
			projectGroupRoutes()
		}
	}
}

private fun Route.beginProjectsSync() {
	val projectsRepository: ProjectsRepository = get()
	val accountsRepository: AccountsRepository = get()
	val ideasRepository: ServerIdeasRepository = get()

	post("/begin_sync") { handleBeginProjectsSync(projectsRepository, accountsRepository, ideasRepository) }
}

private suspend fun RoutingContext.handleBeginProjectsSync(
	projectsRepository: ProjectsRepository,
	accountsRepository: AccountsRepository,
	ideasRepository: ServerIdeasRepository,
) {
	val principal = call.principal<ServerUserIdPrincipal>()
	if (principal == null) {
		call.respond(HttpStatusCode.Unauthorized)
		return
	}

	call.application.get<UserActivityCollector>().record(principal.id, ActivityType.SYNC)

	// Derived from the authenticated token (not client-asserted)
	val installId = call.request.headers[HttpHeaders.Authorization]
		?.substringAfter("Bearer ", "")
		?.takeIf { it.isNotBlank() }
		?.let { accountsRepository.getInstallId(it) }

	val result = projectsRepository.beginProjectsSync(principal.id, installId)
	if (isSuccess(result)) {
		val syncData = result.data
		call.respond(
			BeginProjectsSyncResponse(
				syncId = syncData.syncId,
				projects = syncData.projects.map { it.toApi(syncData.projectPlacements[it.uuid]) }.toSet(),
				deletedProjects = syncData.deletedProjects,
				ideasStateHash = ideasRepository.getIdeasStateHash(principal.id),
				groups = syncData.groups.map { it.toApi() }.toSet(),
			),
		)
	} else {
		call.respond(
			status = HttpStatusCode.BadRequest,
			HttpResponseError(
				error = "Begin Project Sync Failed",
				displayMessage = result.displayMessage?.text(call) ?: call.t(R(ERR_KEY_UNKNOWN)),
			),
		)
	}
}

private fun Route.syncProbe() {
	val probeProjectChanges: ProbeProjectChangesUseCase = get()

	post("/sync_probe") {
		val principal = call.principal<ServerUserIdPrincipal>()
		if (principal == null) {
			call.respond(HttpStatusCode.Unauthorized)
			return@post
		}

		// A malformed body is a routine client condition, not a server error: answer 400 rather than
		// letting it fall through to the global handler as a 500 (and a recorded monitored error).
		// ContentNegotiation wraps any body-conversion failure in BadRequestException.
		val request = try {
			call.receive<ProjectsSyncProbeRequest>()
		} catch (e: BadRequestException) {
			call.respondBadRequest(ERROR_GENERIC, e.message ?: "Malformed request body")
			return@post
		}

		val unchanged = probeProjectChanges(principal.id, request.projects)
		call.respond(ProjectsSyncProbeResponse(unchangedProjects = unchanged))
	}
}

private fun Route.endProjectSync() {
	val projectsRepository: ProjectsRepository = get()

	post("/end_sync") { handleEndProjectsSync(projectsRepository) }
}

private suspend fun RoutingContext.handleEndProjectsSync(projectsRepository: ProjectsRepository) {
	val principal = call.principal<ServerUserIdPrincipal>()!!
	val syncId = call.requireSyncIdFromHeader() ?: return

	val result = projectsRepository.endProjectsSync(principal.id, syncId)
	if (isSuccess(result)) {
		call.respond("Okay")
	} else {
		call.respondBadRequest(
			ERROR_GENERIC,
			result.displayMessage?.text(call) ?: call.t(R(ERR_KEY_INVALID_SYNC_ID)),
		)
	}
}

private fun Route.deleteProject() {
	val projectsRepository: ProjectsRepository = get()

	post("/delete") { handleDeleteProject(projectsRepository) }
}

private suspend fun RoutingContext.handleDeleteProject(projectsRepository: ProjectsRepository) {
	val principal = call.principal<ServerUserIdPrincipal>()!!
	val projectId = call.requireProjectIdFromPath() ?: return
	val syncId = call.requireSyncIdFromHeader() ?: return

	val result = projectsRepository.deleteProject(principal.id, syncId, projectId)
	if (isSuccess(result)) {
		call.respond("Success")
	} else {
		call.respondSyncFailure(result.exception)
	}
}

private fun Route.renameProject() {
	val projectsRepository: ProjectsRepository = get()

	post("/rename") { handleRenameProject(projectsRepository) }
}

private suspend fun RoutingContext.handleRenameProject(projectsRepository: ProjectsRepository) {
	val principal = call.principal<ServerUserIdPrincipal>()!!
	val projectId = call.requireProjectIdFromPath() ?: return
	val syncId = call.requireSyncIdFromHeader() ?: return
	val newProjectName = call.request.queryParameters["projectName"]

	val result = projectsRepository.renameProject(principal.id, syncId, projectId, newProjectName)
	if (isSuccess(result)) {
		call.respond("Success")
	} else {
		respondRenameFailure(result.exception)
	}
}

private suspend fun RoutingContext.respondRenameFailure(exception: Throwable?) {
	when (exception) {
		is ProjectNotFound -> call.respond(
			status = HttpStatusCode.NotFound,
			HttpResponseError(error = ERROR_GENERIC, displayMessage = call.t(R(ERR_KEY_PROJECT_NOT_FOUND))),
		)

		is ProjectNameTaken -> call.respond(
			status = HttpStatusCode.Conflict,
			HttpResponseError(error = ERROR_GENERIC, displayMessage = call.t(R(ERR_KEY_PROJECT_NAME_TAKEN))),
		)

		else -> call.respondSyncFailure(exception)
	}
}

private fun Route.createProject() {
	val projectsRepository: ProjectsRepository = get()

	post("/create") { handleCreateProject(projectsRepository) }
}

private suspend fun RoutingContext.handleCreateProject(projectsRepository: ProjectsRepository) {
	val principal = call.principal<ServerUserIdPrincipal>()!!
	val projectName = call.request.queryParameters["projectName"]
	if (projectName == null) {
		call.respondMissingParameter(ERR_KEY_PROJECT_NAME_MISSING)
		return
	}
	val projectId = call.requireProjectIdFromQuery() ?: return
	val placement = call.readGroupPlacementFromQuery() ?: return
	val syncId = call.requireSyncIdFromHeader() ?: return

	val result = projectsRepository.createProject(principal.id, syncId, projectName, projectId, placement.groupId)
	if (isSuccess(result)) {
		val data = result.data
		call.respond(CreateProjectResponse(data.project.uuid, data.alreadyExisted, data.groupId))
	} else {
		call.respondSyncFailure(result.exception)
	}
}

private fun Route.moveProject() {
	val projectsRepository: ProjectsRepository = get()

	post("/move") { handleMoveProject(projectsRepository) }
}

private suspend fun RoutingContext.handleMoveProject(projectsRepository: ProjectsRepository) {
	val principal = call.principal<ServerUserIdPrincipal>()!!
	val projectId = call.requireProjectIdFromQuery() ?: return
	val placement = call.readGroupPlacementFromQuery() ?: return
	val syncId = call.requireSyncIdFromHeader() ?: return

	val result = projectsRepository.moveProject(principal.id, syncId, projectId, placement.groupId)
	if (isSuccess(result)) {
		call.respond("Success")
	} else if (result.exception is ProjectNotFound) {
		call.respond(
			status = HttpStatusCode.NotFound,
			HttpResponseError(error = ERROR_GENERIC, displayMessage = call.t(R(ERR_KEY_PROJECT_NOT_FOUND))),
		)
	} else {
		call.respondSyncFailure(result.exception)
	}
}
