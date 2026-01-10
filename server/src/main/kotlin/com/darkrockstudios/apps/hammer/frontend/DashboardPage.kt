package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.BioService
import com.darkrockstudios.apps.hammer.account.PenNameService
import com.darkrockstudios.apps.hammer.account.PenNameService.PenNameResult
import com.darkrockstudios.apps.hammer.frontend.utils.*
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
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
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import kotlin.math.ceil

fun Route.dashboardPage(
	projectsRepository: ProjectsRepository,
	accountsRepository: AccountsRepository,
	penNameService: PenNameService,
	bioService: BioService,
	serverConfig: ServerConfig
) {
	authenticatedOnly {
		route("/dashboard") {
			get {
				val session = call.sessions.requireUser()
				val account = accountsRepository.getAccount(session.userId)
				val projectsModel = getProjectsModel(call, projectsRepository, session.userId)
				val projects = projectsModel["projects"] ?: error("Projects model not found")

				val penNameUrl = account.pen_name?.let { ProjectName.formatForUrl(it) }

				// Parse bio markdown to HTML
				val bio = account.bio
				val bioHtml = if (!bio.isNullOrBlank()) {
					val markdownFlavour = CommonMarkFlavourDescriptor()
					val markdownParser = MarkdownParser(markdownFlavour)
					val parsedTree = markdownParser.buildMarkdownTreeFromString(bio)
					HtmlGenerator(bio, parsedTree, markdownFlavour).generateHtml()
				} else {
					null
				}

				val model = call.withDefaults(
					mapOf(
						"page_stylesheet" to "/assets/css/dashboard.css",
						"page_script" to "/assets/js/dashboard.js",
						"username" to session.username,
						"email" to account.email,
						"penName" to (account.pen_name ?: ""),
						"penNameUrl" to (penNameUrl ?: ""),
						"bio" to (account.bio ?: ""),
						"bioHtml" to (bioHtml ?: ""),
						"accountCreated" to formatSyncDate(account.created),
						"isAdmin" to session.isAdmin,
						"projects" to projects,
						"communityEnabled" to serverConfig.communityEnabled,
						"communityMember" to account.community_member,
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

				if (newPenName == null) {
					respondHtmlWithToast(
						content = "",
						message = penNameResultToMessage(call, PenNameResult.TOO_SHORT),
						toast = Toast.Error,
						status = HttpStatusCode.BadRequest
					)
					return@post
				}

				when (val result = penNameService.setPenName(session.userId, newPenName)) {
					PenNameResult.VALID -> {
						call.response.header(HxResponseHeaders.Trigger, "penNameUpdated")
						respondHtmlWithToast(
							content = newPenName,
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

				penNameService.releasePenName(session.userId)

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

				val validationResult = penNameService.validatePenName(penName)
				val isValid = validationResult == PenNameResult.VALID
				val isAvailable = if (isValid) {
					penNameService.isPenNameAvailable(penName, session.userId)
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

			hx.post("/bio") {
				val session = call.sessions.requireUser()
				val formParameters = call.receiveParameters()
				val newBio = formParameters["bio"]?.trim()

				val account = accountsRepository.getAccount(session.userId)
				if (account.pen_name == null) {
					respondHtmlWithToast(
						content = "",
						message = call.msg("bio_error_no_penname"),
						toast = Toast.Error,
						status = HttpStatusCode.BadRequest
					)
					return@post
				}

				when (val result = bioService.setBio(session.userId, newBio)) {
					BioService.BioResult.VALID -> {
						// Re-render the bio section with updated data
						val updatedAccount = accountsRepository.getAccount(session.userId)
						val bioHtml = if (!updatedAccount.bio.isNullOrBlank()) {
							val markdownFlavour = CommonMarkFlavourDescriptor()
							val markdownParser = MarkdownParser(markdownFlavour)
							val parsedTree = markdownParser.buildMarkdownTreeFromString(updatedAccount.bio)
							HtmlGenerator(updatedAccount.bio, parsedTree, markdownFlavour).generateHtml()
						} else {
							null
						}

						val model = call.withDefaults(
							mapOf(
								"bio" to (updatedAccount.bio ?: ""),
								"bioHtml" to (bioHtml ?: "")
							)
						)

						respondTemplateWithToast(
							templatePath = "partials/bio-section.mustache",
							model = model,
							message = call.msg("bio_toast_saved"),
							toast = Toast.Success
						)
					}

					BioService.BioResult.TOO_LONG -> {
						respondHtmlWithToast(
							content = "",
							message = call.msg("bio_validation_too_long", BioService.MAX_BIO_LENGTH),
							toast = Toast.Error,
							status = HttpStatusCode.BadRequest
						)
					}

					BioService.BioResult.NO_PEN_NAME -> {
						respondHtmlWithToast(
							content = "",
							message = call.msg("bio_error_no_penname"),
							toast = Toast.Error,
							status = HttpStatusCode.BadRequest
						)
					}
				}
			}

			hx.delete("/bio") {
				val session = call.sessions.requireUser()

				bioService.setBio(session.userId, null)

				// Re-render empty bio section
				val model = call.withDefaults(
					mapOf(
						"bio" to "",
						"bioHtml" to ""
					)
				)

				respondTemplateWithToast(
					templatePath = "partials/bio-section.mustache",
					model = model,
					message = call.msg("bio_toast_cleared"),
					toast = Toast.Success
				)
			}

			hx.post("/community/join") {
				if (!serverConfig.communityEnabled) {
					respondHtmlWithToast(
						content = "",
						message = call.msg("community_error_disabled"),
						toast = Toast.Error,
						status = HttpStatusCode.BadRequest
					)
					return@post
				}

				val session = call.sessions.requireUser()
				val account = accountsRepository.getAccount(session.userId)

				// Require pen name to join community
				if (account.pen_name == null) {
					respondHtmlWithToast(
						content = "",
						message = call.msg("community_error_no_penname"),
						toast = Toast.Error,
						status = HttpStatusCode.BadRequest
					)
					return@post
				}

				accountsRepository.updateCommunityMember(session.userId, true)

				val model = call.withDefaults(
					mapOf(
						"communityEnabled" to true,
						"communityMember" to true,
						"penName" to (account.pen_name ?: "")
					)
				)

				respondTemplateWithToast(
					templatePath = "partials/community-section.mustache",
					model = model,
					message = call.msg("community_toast_joined"),
					toast = Toast.Success
				)
			}

			hx.post("/community/leave") {
				if (!serverConfig.communityEnabled) {
					respondHtmlWithToast(
						content = "",
						message = call.msg("community_error_disabled"),
						toast = Toast.Error,
						status = HttpStatusCode.BadRequest
					)
					return@post
				}

				val session = call.sessions.requireUser()
				val account = accountsRepository.getAccount(session.userId)

				accountsRepository.updateCommunityMember(session.userId, false)

				val model = call.withDefaults(
					mapOf(
						"communityEnabled" to true,
						"communityMember" to false,
						"penName" to (account.pen_name ?: "")
					)
				)

				respondTemplateWithToast(
					templatePath = "partials/community-section.mustache",
					model = model,
					message = call.msg("community_toast_left"),
					toast = Toast.Success
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
			"nameForUrl" to ProjectName.formatForUrl(project.name),
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

private suspend fun penNameResultToMessage(call: ApplicationCall, result: PenNameResult): String {
	return when (result) {
		PenNameResult.VALID -> call.msg("penname_validation_valid")
		PenNameResult.TOO_SHORT -> call.msg("penname_validation_too_short", PenNameService.MIN_PEN_NAME_LENGTH)
		PenNameResult.TOO_LONG -> call.msg("penname_validation_too_long", PenNameService.MAX_PEN_NAME_LENGTH)
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
