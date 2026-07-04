package com.darkrockstudios.apps.hammer.storyideas

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeasSyncStateResponse
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.utilities.ERR_KEY_INVALID_SYNC_ID
import com.darkrockstudios.apps.hammer.utilities.ERR_KEY_UNKNOWN
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utilities.respondBadRequest
import com.darkrockstudios.apps.hammer.utilities.respondMissingParameter
import com.darkrockstudios.apps.hammer.utilities.requireSyncId
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
private const val ERR_KEY_IDEA_ID_MISSING = "api_ideas_error_ideaidmissing"
private const val ERR_KEY_IDEA_NOT_FOUND = "api_ideas_error_notfound"
private const val ERR_KEY_IDEA_DELETED = "api_ideas_error_deleted"
private const val ERR_KEY_IDEA_TOO_LARGE = "api_ideas_error_toolarge"

/**
 * Account-level story-idea sync. Every route requires the account (projects) sync session's
 * syncId header — the ideas phase runs inside that session; there is no separate ideas session.
 */
fun Route.ideasRoutes() {
	authenticate(USER_AUTH) {
		route("/ideas/{userId}") {
			ideasSyncState()
			downloadIdea()
			uploadIdea()
			deleteIdea()
		}
	}
}

private suspend fun ApplicationCall.requireIdeaIdFromPath(): IdeaId? {
	val raw = parameters["ideaId"]
	if (raw == null) {
		respondMissingParameter(ERR_KEY_IDEA_ID_MISSING)
		return null
	}
	return IdeaId(raw)
}

private suspend fun ApplicationCall.respondIdeasFailure(exception: Throwable?) {
	when (exception) {
		is InvalidSyncIdException -> respondBadRequest(ERROR_GENERIC, t(R(ERR_KEY_INVALID_SYNC_ID)))
		is IdeaNotFound -> respond(
			status = HttpStatusCode.NotFound,
			HttpResponseError(error = ERROR_GENERIC, displayMessage = t(R(ERR_KEY_IDEA_NOT_FOUND))),
		)

		is IdeaDeletedException -> respond(
			status = HttpStatusCode.Gone,
			HttpResponseError(error = ERROR_GENERIC, displayMessage = t(R(ERR_KEY_IDEA_DELETED))),
		)

		is IdeaTooLargeException -> respond(
			status = HttpStatusCode.PayloadTooLarge,
			HttpResponseError(error = ERROR_GENERIC, displayMessage = t(R(ERR_KEY_IDEA_TOO_LARGE))),
		)

		is IllegalArgumentException -> respondBadRequest(
			ERROR_GENERIC,
			exception.message ?: t(R(ERR_KEY_UNKNOWN)),
		)

		else -> respond(
			status = HttpStatusCode.InternalServerError,
			HttpResponseError(
				error = ERROR_GENERIC,
				displayMessage = exception?.message ?: t(R(ERR_KEY_UNKNOWN)),
			),
		)
	}
}

private fun Route.ideasSyncState() {
	val repository: ServerIdeasRepository = get()

	post("/state") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val syncId = call.requireSyncId() ?: return@post

		val result = repository.getSyncState(principal.id, syncId)
		if (isSuccess(result)) {
			val state = result.data
			call.respond(
				IdeasSyncStateResponse(
					ideas = state.ideas,
					deletedIdeas = state.deletedIdeas,
				)
			)
		} else {
			call.respondIdeasFailure(result.exception)
		}
	}
}

private fun Route.downloadIdea() {
	val repository: ServerIdeasRepository = get()

	get("/idea/{ideaId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val syncId = call.requireSyncId() ?: return@get
		val ideaId = call.requireIdeaIdFromPath() ?: return@get

		val result = repository.loadIdea(principal.id, syncId, ideaId)
		if (isSuccess(result)) {
			call.respond(result.data)
		} else {
			call.respondIdeasFailure(result.exception)
		}
	}
}

private fun Route.uploadIdea() {
	val repository: ServerIdeasRepository = get()

	post("/idea/{ideaId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val syncId = call.requireSyncId() ?: return@post
		val ideaId = call.requireIdeaIdFromPath() ?: return@post

		// A malformed body is a routine client condition, not a server error: answer 400 rather
		// than letting it fall through to the global handler as a 500.
		val request = try {
			call.receive<RawIdeaUploadRequest>()
		} catch (e: BadRequestException) {
			call.respondBadRequest(ERROR_GENERIC, e.message ?: "Malformed request body")
			return@post
		}

		val result = repository.saveIdea(
			userId = principal.id,
			syncId = syncId,
			ideaId = ideaId,
			idea = request.idea,
			originalHash = request.originalHash,
			clientHash = request.hash,
		)
		if (isSuccess(result)) {
			when (val outcome = result.data) {
				is ServerIdeasRepository.IdeaSaveResult.Saved ->
					call.respond(HttpStatusCode.OK, outcome.dto)

				is ServerIdeasRepository.IdeaSaveResult.Conflict ->
					call.respond(HttpStatusCode.Conflict, outcome.conflict)
			}
		} else {
			call.respondIdeasFailure(result.exception)
		}
	}
}

private fun Route.deleteIdea() {
	val repository: ServerIdeasRepository = get()

	post("/idea/{ideaId}/delete") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val syncId = call.requireSyncId() ?: return@post
		val ideaId = call.requireIdeaIdFromPath() ?: return@post

		val result = repository.deleteIdea(principal.id, syncId, ideaId)
		if (isSuccess(result)) {
			call.respond("Success")
		} else {
			call.respondIdeasFailure(result.exception)
		}
	}
}
