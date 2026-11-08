package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.project.ProjectAccess
import com.darkrockstudios.apps.hammer.project.ProjectAccessResolver
import com.darkrockstudios.apps.hammer.project.ProjectAccessResult
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.principal
import io.ktor.server.response.*
import org.koin.ktor.ext.get
import kotlin.uuid.Uuid

internal const val ERROR_MISSING_PARAMETER = "Missing Parameter"
internal const val ERROR_MISSING_HEADER = "Missing Header"
internal const val ERROR_MISSING_ENTITY_ID = "Missing Entity Id"

internal const val ERR_KEY_PROJECT_NAME_MISSING = "api_project_sync_error_projectnamemissing"
internal const val ERR_KEY_PROJECT_ID_MISSING = "api_project_sync_error_projectidmissing"
internal const val ERR_KEY_SYNC_ID_MISSING = "api_project_sync_error_syncidmissing"
internal const val ERR_KEY_ENTITY_ID_MISSING = "api_project_error_entityidmissing"
internal const val ERR_KEY_INVALID_SYNC_ID = "api_project_sync_end_invalidid"
internal const val ERR_KEY_UNKNOWN = "api_error_unknown"

internal fun parseUuidOrNull(raw: String): Uuid? =
	try {
		Uuid.parse(raw)
	} catch (_: IllegalArgumentException) {
		null
	}

/** Responds 400 BadRequest with the given error name and a plain-text display message. */
suspend fun ApplicationCall.respondBadRequest(error: String, displayMessage: String) {
	respond(
		status = HttpStatusCode.BadRequest,
		HttpResponseError(error = error, displayMessage = displayMessage),
	)
}

/** Responds 400 BadRequest with a localized message from [messageKey]. */
suspend fun ApplicationCall.respondMissingParameter(
	messageKey: String,
	error: String = ERROR_MISSING_PARAMETER,
) = respondBadRequest(error, t(R(messageKey)))

/** Responds 400 BadRequest with `"Missing Header"` and a localized message from [messageKey]. */
suspend fun ApplicationCall.respondMissingHeader(messageKey: String) =
	respondBadRequest(ERROR_MISSING_HEADER, t(R(messageKey)))

/**
 * Responds 410 Gone for a project that does not exist for the user.
 *
 * Thar be dragons: this must NOT be 404. The `download_entity` client maps a 404 to
 * "entity deleted on the server" and, for an entity it doesn't already hold locally,
 * silently marks it deleted. A project-level 404 would therefore make a client abandon
 * undownloaded entities when a project vanishes mid-sync. A distinct status fails the
 * sync cleanly instead.
 */
suspend fun ApplicationCall.respondProjectGone(messageKey: String) =
	respond(
		status = HttpStatusCode.Gone,
		HttpResponseError(error = "Project Not Found", displayMessage = t(R(messageKey))),
	)

/**
 * Reads `projectId` from path params and resolves the caller's access to it, by id alone. Responds
 * 400 if the id is missing, 410 Gone if no project holds it, and 403 `not_member` if the caller
 * may not use it; the client treats 410 as free to create and 403 as someone else's. Returns `null`
 * after responding so the caller should `return@get` / `return@post` immediately.
 */
suspend fun ApplicationCall.requireProjectAccess(): ProjectAccess? {
	val projectIdRaw = parameters["projectId"]
	if (projectIdRaw == null) {
		respondMissingParameter(ERR_KEY_PROJECT_ID_MISSING)
		return null
	}

	val callerId = principal<ServerUserIdPrincipal>()!!.id
	return when (val result = application.get<ProjectAccessResolver>().resolve(callerId, ProjectId(projectIdRaw))) {
		is ProjectAccessResult.Granted -> result.access
		ProjectAccessResult.Unknown -> {
			respondProjectGone("api_project_getproject_error_notfound")
			null
		}

		ProjectAccessResult.NotMember -> {
			respond(
				status = HttpStatusCode.Forbidden,
				HttpResponseError(
					error = ApiErrorCode.NOT_MEMBER,
					displayMessage = t(R("api_project_error_notmember")),
					errorCode = ApiErrorCode.NOT_MEMBER,
				),
			)
			null
		}
	}
}

/**
 * Reads the sync id from the [HEADER_SYNC_ID] header, responding 400 BadRequest if
 * missing or blank. Returns `null` after responding so the caller should
 * `return@get` / `return@post` immediately.
 */
suspend fun ApplicationCall.requireSyncId(): String? {
	val syncId = request.headers[HEADER_SYNC_ID]
	if (syncId.isNullOrBlank()) {
		respondMissingParameter(ERR_KEY_SYNC_ID_MISSING)
		return null
	}
	return syncId
}

/**
 * Reads `entityId` from path params and parses as Int, responding 400 BadRequest if
 * missing or unparseable. Returns `null` after responding so the caller should
 * `return@get` / `return@post` immediately.
 */
suspend fun ApplicationCall.requireEntityId(
	error: String = ERROR_MISSING_PARAMETER,
): Int? {
	val entityId = parameters["entityId"]?.toIntOrNull()
	if (entityId == null) {
		respondMissingParameter(ERR_KEY_ENTITY_ID_MISSING, error)
		return null
	}
	return entityId
}
