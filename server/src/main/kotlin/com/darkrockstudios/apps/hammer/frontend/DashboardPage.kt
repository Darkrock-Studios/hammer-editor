package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.PenNameResult
import com.darkrockstudios.apps.hammer.frontend.utils.*
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil

fun Route.dashboardPage(
	projectsRepository: ProjectsRepository,
	accountsRepository: AccountsRepository
) {
	authenticatedOnly {
		route("/dashboard") {
			get {
				val session = call.sessions.requireUser()
				val account = accountsRepository.getAccount(session.userId)
				val projectsModel = getProjectsModel(call, projectsRepository, session.userId)
				val projects = projectsModel["projects"] ?: error("Projects model not found")

				val model = call.withDefaults(
					mapOf(
						"page_stylesheet" to "/assets/css/dashboard.css",
						"page_script" to "/assets/js/dashboard.js",
						"username" to session.username,
						"email" to account.email,
						"penName" to (account.pen_name ?: ""),
						"accountCreated" to formatSyncDate(account.created),
						"isAdmin" to session.isAdmin,
						"projects" to projects,
					)
				)

				call.respond(MustacheContent("dashboard.mustache", model))
			}

			hx.get("/projects-fragment") {
				val session = call.sessions.requireUser()
				val model = getProjectsModel(call, projectsRepository, session.userId)
				call.respond(MustacheContent("partials/projects.mustache", model))
			}

			hx.post("/penname") {
				val session = call.sessions.requireUser()
				val formParameters = call.receiveParameters()
				val newPenName = formParameters["penName"]?.trim()?.takeIf { it.isNotEmpty() }

				when (val result = accountsRepository.updatePenName(session.userId, newPenName)) {
					PenNameResult.VALID -> {
						call.response.header(HxResponseHeaders.Trigger, "penNameUpdated")
						respondHtmlWithToast(
							content = newPenName ?: "",
							message = call.msg("penname_toast_saved"),
							toast = Toast.Success
						)
					}

					else -> {
						respondHtmlWithToast(
							content = "",
							message = penNameResultToMessage(call, result),
							toast = Toast.Error,
							status = HttpStatusCode.BadRequest
						)
					}
				}
			}

			hx.delete("/penname") {
				val session = call.sessions.requireUser()

				accountsRepository.updatePenName(session.userId, null)

				respondToast(call.msg("penname_toast_released"), Toast.Success)
			}

			hx.get("/penname/check") {
				val session = call.sessions.requireUser()
				val penName = call.request.queryParameters["penName"]?.trim() ?: ""

				if (penName.isEmpty()) {
					call.respondText(
						Json.encodeToString(
							PenNameCheckResponse(
								valid = false,
								available = false,
								message = ""
							)
						),
						ContentType.Application.Json
					)
					return@get
				}

				val validationResult = accountsRepository.validatePenName(penName)
				val isValid = validationResult == PenNameResult.VALID
				val isAvailable = if (isValid) {
					accountsRepository.isPenNameAvailable(penName, session.userId)
				} else {
					false
				}

				val message = when {
					!isValid -> penNameResultToMessage(call, validationResult)
					!isAvailable -> call.msg("penname_validation_taken")
					else -> call.msg("penname_validation_available")
				}

				call.respondText(
					Json.encodeToString(
						PenNameCheckResponse(
							valid = isValid,
							available = isAvailable,
							message = message
						)
					),
					ContentType.Application.Json
				)
			}
		}
	}
}

private suspend fun getProjectsModel(
	call: ApplicationCall,
	projectsRepository: ProjectsRepository,
	userId: Long,
	page: Int? = null
): MutableMap<String, Any> {
	val queryPage = call.request.queryParameters["page"]?.toIntOrNull()
	val actualPage = page ?: queryPage ?: 0

	val pageSize = 10
	val totalCount = projectsRepository.getProjectsCount(userId)
	val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
	val currentPage = if (totalPages > 0) actualPage.coerceIn(0, totalPages - 1) else 0

	val projects = projectsRepository.getProjectsWithSyncDate(userId, currentPage, pageSize)
	val projectsForTemplate = projects.map { project ->
		mapOf(
			"name" to project.name,
			"uuid" to project.uuid,
			"lastSync" to formatSyncDate(project.lastSync)
		)
	}

	val projectsModel = mutableMapOf<String, Any>()
	projectsModel["items"] = projectsForTemplate
	projectsModel["currentPage"] = currentPage
	projectsModel["currentPageDisplay"] = currentPage + 1
	projectsModel["totalPages"] = totalPages
	projectsModel["hasNextPage"] = currentPage < totalPages - 1
	projectsModel["hasPrevPage"] = currentPage > 0
	projectsModel["nextPage"] = currentPage + 1
	projectsModel["prevPage"] = currentPage - 1
	projectsModel["hasProjects"] = projectsForTemplate.isNotEmpty()
	projectsModel["isPaged"] = totalPages > 1

	val model = call.withDefaults()
	model["projects"] = projectsModel

	return model
}

private fun formatSyncDate(sqliteDateTime: String): String {
	return try {
		val instant = sqliteDateTimeStringToInstant(sqliteDateTime)
		instant.formatLocal("MMM dd, yyyy 'at' HH:mm")
	} catch (e: Exception) {
		sqliteDateTime
	}
}

private fun kotlin.time.Instant.formatLocal(format: String): String {
	val formatter = java.time.format.DateTimeFormatter.ofPattern(format)
	val zoned = java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault())
	return formatter.format(zoned)
}

private suspend fun penNameResultToMessage(call: ApplicationCall, result: PenNameResult): String {
	return when (result) {
		PenNameResult.VALID -> call.msg("penname_validation_valid")
		PenNameResult.TOO_SHORT -> call.msg("penname_validation_too_short", AccountsRepository.MIN_PEN_NAME_LENGTH)
		PenNameResult.TOO_LONG -> call.msg("penname_validation_too_long", AccountsRepository.MAX_PEN_NAME_LENGTH)
		PenNameResult.INVALID_CHARACTERS -> call.msg("penname_validation_invalid_chars")
		PenNameResult.NOT_AVAILABLE -> call.msg("penname_validation_taken")
	}
}

@Serializable
data class PenNameCheckResponse(
	val valid: Boolean,
	val available: Boolean,
	val message: String
)
