package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.UpsertGroupResponse
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utilities.respondBadRequest
import com.darkrockstudios.apps.hammer.utilities.respondMissingParameter
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

internal fun Route.projectGroupRoutes() {
	upsertGroup()
	deleteGroup()
}

private fun Route.upsertGroup() {
	val projectsRepository: ProjectsRepository = get()

	post("/group/upsert") { handleUpsertGroup(projectsRepository) }
}

private suspend fun RoutingContext.handleUpsertGroup(projectsRepository: ProjectsRepository) {
	val principal = call.principal<ServerUserIdPrincipal>()!!

	// ContentNegotiation wraps any body-conversion failure in BadRequestException.
	val request = try {
		call.receive<ApiGroupDefinition>()
	} catch (e: BadRequestException) {
		call.respondBadRequest(ERROR_GENERIC, e.message ?: "Malformed request body")
		return
	}

	val uuid = canonicalGroupId(request.uuid.id)
	val parentId = request.parentId?.let { canonicalGroupId(it.id) }
	if (uuid == null || (request.parentId != null && parentId == null)) {
		call.respondMissingParameter(ERR_KEY_GROUP_ID_MISSING)
		return
	}
	val syncId = call.requireSyncIdFromHeader() ?: return

	val result = projectsRepository.upsertGroup(principal.id, syncId, request.copy(uuid = uuid, parentId = parentId))
	if (isSuccess(result)) {
		call.respond(UpsertGroupResponse(result.data.groupId, result.data.alreadyExisted))
	} else {
		call.respondSyncFailure(result.exception)
	}
}

private fun Route.deleteGroup() {
	val projectsRepository: ProjectsRepository = get()

	post("/group/delete") { handleDeleteGroup(projectsRepository) }
}

private suspend fun RoutingContext.handleDeleteGroup(projectsRepository: ProjectsRepository) {
	val principal = call.principal<ServerUserIdPrincipal>()!!
	val groupId = call.requireGroupIdFromQuery() ?: return
	val syncId = call.requireSyncIdFromHeader() ?: return

	val result = projectsRepository.deleteGroup(principal.id, syncId, groupId)
	if (isSuccess(result)) {
		call.respond("Success")
	} else {
		call.respondSyncFailure(result.exception)
	}
}
