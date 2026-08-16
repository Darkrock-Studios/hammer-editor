package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.http.ProjectsSyncProbeRequest
import com.darkrockstudios.apps.hammer.base.http.ProjectsSyncProbeResponse
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.project.InvalidProjectName
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.monitoring.ActivityType
import com.darkrockstudios.apps.hammer.monitoring.UserActivityCollector
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

private const val ERROR_GENERIC = "Error"
private const val ERR_KEY_INVALID_PROJECT_NAME = "api_project_rename_error_invalidname"
private const val ERR_KEY_PROJECT_NAME_TAKEN = "api_project_rename_error_nametaken"
private const val ERR_KEY_PROJECT_NOT_FOUND = "api_project_getproject_error_notfound"

/** Reads syncId from header, responding 400 "Missing Header" with a localized message if absent. */
private suspend fun ApplicationCall.requireSyncIdFromHeader(): String? {
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

/** Handles common failure exceptions for sync-id-based operations. */
private suspend fun ApplicationCall.respondSyncFailure(exception: Throwable?) {
	when (exception) {
		is InvalidSyncIdException -> respondBadRequest(ERROR_GENERIC, t(R(ERR_KEY_INVALID_SYNC_ID)))
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
			syncProbe()
		}
	}
}

private fun Route.beginProjectsSync() {
	val projectsRepository: ProjectsRepository = get()
	val accountsRepository: AccountsRepository = get()
	val ideasRepository: ServerIdeasRepository = get()

	// POST is the preferred verb; GET remains for legacy clients.
	// TODO Remove the legacy GET route at the next protocol version bump.
	get("/begin_sync") { handleBeginProjectsSync(projectsRepository, accountsRepository, ideasRepository) }
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
				projects = syncData.projects.map { it.toApi() }.toSet(),
				deletedProjects = syncData.deletedProjects,
				ideasStateHash = ideasRepository.getIdeasStateHash(principal.id),
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
	val projectsRepository: ProjectsRepository = get()

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

		val unchanged = projectsRepository.probeProjectChanges(principal.id, request.projects)
		call.respond(ProjectsSyncProbeResponse(unchangedProjects = unchanged))
	}
}

private fun Route.endProjectSync() {
	val projectsRepository: ProjectsRepository = get()

	// POST is the preferred verb; GET remains for legacy clients.
	// TODO Remove the legacy GET route at the next protocol version bump.
	get("/end_sync") { handleEndProjectsSync(projectsRepository) }
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

	// POST is the preferred verb; GET remains for legacy clients.
	// TODO Remove the legacy GET route at the next protocol version bump.
	get("/delete") { handleDeleteProject(projectsRepository) }
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

	// POST is the preferred verb; GET remains for legacy clients.
	// TODO Remove the legacy GET route at the next protocol version bump.
	get("/rename") { handleRenameProject(projectsRepository) }
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
		is InvalidProjectName -> call.respond(
			status = HttpStatusCode.NotAcceptable,
			HttpResponseError(
				error = ERROR_GENERIC,
				displayMessage = call.tWithEnglishFallback(ERR_KEY_INVALID_PROJECT_NAME),
			),
		)

		is ProjectNotFound -> call.respond(
			status = HttpStatusCode.NotFound,
			HttpResponseError(
				error = ERROR_GENERIC,
				displayMessage = call.tWithEnglishFallback(ERR_KEY_PROJECT_NOT_FOUND),
			),
		)

		is ProjectNameTaken -> call.respond(
			status = HttpStatusCode.Conflict,
			HttpResponseError(
				error = ERROR_GENERIC,
				displayMessage = call.tWithEnglishFallback(ERR_KEY_PROJECT_NAME_TAKEN),
			),
		)

		else -> call.respondSyncFailure(exception)
	}
}

private fun Route.createProject() {
	val projectsRepository: ProjectsRepository = get()

	// POST is the preferred verb; GET remains for legacy clients.
	// TODO Remove the legacy GET route at the next protocol version bump.
	get("/create") { handleCreateProject(projectsRepository) }
	post("/create") { handleCreateProject(projectsRepository) }
}

private suspend fun RoutingContext.handleCreateProject(projectsRepository: ProjectsRepository) {
	val principal = call.principal<ServerUserIdPrincipal>()!!
	val projectName = call.request.queryParameters["projectName"]
	if (projectName == null) {
		call.respondMissingParameter(ERR_KEY_PROJECT_NAME_MISSING)
		return
	}
	val syncId = call.requireSyncIdFromHeader() ?: return

	val result = projectsRepository.createProject(principal.id, syncId, projectName)
	if (isSuccess(result)) {
		val data = result.data
		call.respond(CreateProjectResponse(data.project.uuid, data.alreadyExisted))
	} else {
		call.respondSyncFailure(result.exception)
	}
}
