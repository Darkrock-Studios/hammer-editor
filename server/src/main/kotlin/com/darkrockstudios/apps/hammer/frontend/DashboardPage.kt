package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountDeletionUseCase
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.BioService
import com.darkrockstudios.apps.hammer.account.PenNameUseCase
import com.darkrockstudios.apps.hammer.account.ReleasePenNameUseCase
import com.darkrockstudios.apps.hammer.account.PenNameUseCase.PenNameResult
import com.darkrockstudios.apps.hammer.account.isActive
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.Toast
import com.darkrockstudios.apps.hammer.frontend.utils.authenticatedOnly
import com.darkrockstudios.apps.hammer.frontend.utils.formatSyncDate
import com.darkrockstudios.apps.hammer.frontend.utils.getLocale
import com.darkrockstudios.apps.hammer.frontend.utils.localizedMsg
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.renderTemplate
import com.darkrockstudios.apps.hammer.frontend.utils.requireUser
import com.darkrockstudios.apps.hammer.frontend.utils.respondHtmlWithToast
import com.darkrockstudios.apps.hammer.frontend.utils.respondInlineError
import com.darkrockstudios.apps.hammer.frontend.utils.respondTemplateWithToast
import com.darkrockstudios.apps.hammer.frontend.utils.respondToast
import com.darkrockstudios.apps.hammer.project.ProjectAccessResolver
import com.darkrockstudios.apps.hammer.project.ProjectMemberRepository
import com.darkrockstudios.apps.hammer.project.ReceivedInvite
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.MarkdownRenderer
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.htmx.HxResponseHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.htmx.hx
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil

fun Route.dashboardPage(
	projectsRepository: ProjectsRepository,
	accountsRepository: AccountsRepository,
	penNameUseCase: PenNameUseCase,
	releasePenName: ReleasePenNameUseCase,
	bioService: BioService,
	accountDeletionUseCase: AccountDeletionUseCase,
	serverConfig: ServerConfig,
	markdownRenderer: MarkdownRenderer,
	projectMemberRepository: ProjectMemberRepository,
	projectAccessResolver: ProjectAccessResolver,
	whiteListRepository: WhiteListRepository,
) {
	/** An inactive owner's membership grants nothing, so their invites are not offered. */
	suspend fun visibleInvites(userId: Long): List<ReceivedInvite> {
		val invites = projectMemberRepository.receivedInvites(userId)
		val activeOwners = invites.map { it.ownerId }.distinct()
			.filter { accountsRepository.getAccountOrNull(it)?.isActive(whiteListRepository) == true }
			.toSet()
		return invites.filter { it.ownerId in activeOwners }
	}

	authenticatedOnly {
		route("/dashboard") {
			get {
				val session = call.sessions.requireUser()
				val account = accountsRepository.getAccount(session.userId)
				val projectsModel = getProjectsModel(call, projectsRepository, session.userId)
				val projects = projectsModel["projects"] ?: error("Projects model not found")
				val hasPenName = !account.pen_name.isNullOrBlank()
				val invitesModel = call.invitesModel(visibleInvites(session.userId), hasPenName)
				val sharedModel = call.sharedProjectsModel(projectAccessResolver, session.userId)

				val penNameUrl = account.pen_name?.let { ProjectName.penNameForUrl(it) }

				// Parse bio markdown to sanitized HTML
				val bioHtml = account.bio?.let { markdownRenderer.markdownToSafeHtml(it) }

				val model = call.withDefaults(
					mapOf(
						"page_stylesheet" to "/assets/css/dashboard.css",
						"page_script" to "/assets/js/dashboard.js",
						"page_pre_script" to "/assets/js/pen-name-logic.js",
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
				model.putAll(invitesModel)
				model.putAll(sharedModel)

				call.respond(MustacheContent("dashboard.mustache", model))
			}

			hx.get("/projects-fragment") {
				val session = call.sessions.requireUser()
				val model = getProjectsModel(call, projectsRepository, session.userId)
				call.respond(MustacheContent("partials/projects.mustache", model))
			}

			hx.post("/invites/{inviteId}/accept") {
				val session = call.sessions.requireUser()
				val inviteId = call.parameters["inviteId"]?.toLongOrNull()
				val invites = visibleInvites(session.userId)
				val invite = invites.find { it.id == inviteId }
				if (invite == null) {
					respondTemplateWithToast(
						templatePath = INVITES_FRAGMENT,
						model = call.withDefaults(call.invitesModel(invites, hasPenName(accountsRepository, session.userId))),
						message = call.msg("dashboard_invite_gone"),
						toast = Toast.Error,
						status = HttpStatusCode.NotFound,
					)
					return@post
				}

				// Accepting needs a pen name, so an account without one sets it here first.
				val setPenName = !hasPenName(accountsRepository, session.userId)
				if (setPenName) {
					val penName = call.receiveParameters()["penName"]?.trim().orEmpty()
					val result = if (penName.isEmpty()) {
						PenNameResult.TOO_SHORT
					} else {
						penNameUseCase.setPenName(session.userId, penName)
					}
					if (result != PenNameResult.VALID) {
						val model = call.invitesModel(
							invites,
							hasPenName = false,
							errorInviteId = invite.id,
							error = penNameResultToMessage(call, result),
							penNameDraft = penName,
						)
						respondInlineError(renderTemplate(INVITES_FRAGMENT, call.withDefaults(model)))
						return@post
					}
				}

				val accepted = projectMemberRepository.acceptInvite(session.userId, invite.id)
				// A new pen name shows across the page (account section, bio, community), so reload it.
				if (setPenName) {
					call.response.header(HxResponseHeaders.Refresh, "true")
					call.respond(HttpStatusCode.NoContent)
					return@post
				}
				if (accepted !is ServerResult.Success) {
					respondTemplateWithToast(
						templatePath = INVITES_FRAGMENT,
						model = call.withDefaults(
							call.invitesModel(visibleInvites(session.userId), hasPenName(accountsRepository, session.userId))
						),
						message = call.msg("dashboard_invite_gone"),
						toast = Toast.Error,
						status = HttpStatusCode.NotFound,
					)
					return@post
				}

				val model = call.withDefaults(call.invitesModel(visibleInvites(session.userId), hasPenName = true))
				model.putAll(call.sharedProjectsModel(projectAccessResolver, session.userId))
				model["sharedProjectsOob"] = true
				respondHtmlWithToast(
					content = renderTemplate(INVITES_FRAGMENT, model) + renderTemplate(SHARED_PROJECTS_FRAGMENT, model),
					message = call.msg("dashboard_invite_accepted", invite.projectName),
				)
			}

			hx.post("/invites/{inviteId}/decline") {
				val session = call.sessions.requireUser()
				call.parameters["inviteId"]?.toLongOrNull()?.let {
					projectMemberRepository.declineInvite(session.userId, it)
				}
				respondTemplateWithToast(
					templatePath = INVITES_FRAGMENT,
					model = call.withDefaults(
						call.invitesModel(visibleInvites(session.userId), hasPenName(accountsRepository, session.userId))
					),
					message = call.msg("dashboard_invite_declined"),
					toast = Toast.Info,
				)
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

				when (val result = penNameUseCase.setPenName(session.userId, newPenName)) {
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

				releasePenName(session.userId)

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

				val validationResult = penNameUseCase.validatePenName(penName)
				val isValid = validationResult == PenNameResult.VALID
				val isAvailable = if (isValid) {
					penNameUseCase.isPenNameAvailable(penName, session.userId)
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

				val outcome = when (bioService.setBio(session.userId, newBio)) {
					BioService.BioResult.VALID -> {
						// Re-render the bio section with updated data
						val updatedAccount = accountsRepository.getAccount(session.userId)
						val bioHtml = updatedAccount.bio?.let { markdownRenderer.markdownToSafeHtml(it) }

						val model = call.withDefaults(
							mapOf(
								"bio" to (updatedAccount.bio ?: ""),
								"bioHtml" to (bioHtml ?: "")
							)
						)

						BioOutcome(
							content = renderTemplate("partials/bio-section.mustache", model),
							message = call.msg("bio_toast_saved"),
							toast = Toast.Success,
							status = HttpStatusCode.OK
						)
					}

					BioService.BioResult.TOO_LONG -> BioOutcome(
						message = call.msg("bio_validation_too_long", BioService.MAX_BIO_LENGTH)
					)

					BioService.BioResult.NO_PEN_NAME -> BioOutcome(
						message = call.msg("bio_error_no_penname")
					)
				}

				respondHtmlWithToast(
					content = outcome.content,
					message = outcome.message,
					toast = outcome.toast,
					status = outcome.status
				)
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

			hx.get("/delete-account-dialog") {
				val session = call.sessions.requireUser()
				val account = accountsRepository.getAccount(session.userId)

				val model = call.withDefaults(
					mapOf(
						"email" to account.email,
						"projectCount" to projectsRepository.getProjectsCount(session.userId),
						"retentionNote" to call.msg(
							"account_delete_dialog_retention_note",
							serverConfig.accountDeletion.retentionDays
						),
					)
				)

				call.respond(MustacheContent("partials/delete-account-dialog.mustache", model))
			}

			hx.post("/delete-account") {
				val session = call.sessions.requireUser()
				val account = accountsRepository.getAccount(session.userId)
				val typedEmail = call.receiveParameters()["confirmEmail"]?.trim() ?: ""

				// The JS arming of the button is UX only; the typed email is the
				// real confirmation and must be verified here.
				if (!typedEmail.equals(account.email, ignoreCase = true)) {
					respondToast(
						call.msg("account_delete_error_email_mismatch"),
						Toast.Error,
						HttpStatusCode.BadRequest
					)
					return@post
				}

				val result = accountDeletionUseCase.softDelete(session.userId)
				if (isSuccess(result)) {
					call.sessions.clear<UserSession>()
					call.response.header(HxResponseHeaders.Redirect, "/")
					call.respond(HttpStatusCode.NoContent)
				} else {
					respondToast(
						result.displayMessageText(call) ?: call.msg("account_delete_error_generic"),
						Toast.Error,
						HttpStatusCode.BadRequest
					)
				}
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
			"nameForUrl" to ProjectName.projectSegment(project.name, project.uuid),
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

private const val INVITES_FRAGMENT = "partials/coauthor-invites.mustache"
private const val SHARED_PROJECTS_FRAGMENT = "partials/shared-projects.mustache"

private suspend fun hasPenName(accountsRepository: AccountsRepository, userId: Long): Boolean =
	!accountsRepository.getAccount(userId).pen_name.isNullOrBlank()

/** [error] shows under the invite [errorInviteId], whose pen name field keeps [penNameDraft]. */
private suspend fun ApplicationCall.invitesModel(
	invites: List<ReceivedInvite>,
	hasPenName: Boolean,
	errorInviteId: Long? = null,
	error: String? = null,
	penNameDraft: String = "",
): Map<String, Any> {
	val locale = getLocale()
	return mapOf(
		"hasInvites" to invites.isNotEmpty(),
		"hasPenName" to hasPenName,
		"invites" to invites.map { invite ->
			val isErrored = invite.id == errorInviteId
			buildMap {
				put("id", invite.id)
				put("projectName", invite.projectName)
				put(
					"ownerLine",
					localizedMsg(
						locale,
						"dashboard_invite_from",
						invite.ownerName ?: localizedMsg(locale, "dashboard_shared_by_unknown"),
					),
				)
				put("penNameDraft", if (isErrored) penNameDraft else "")
				put("hasError", isErrored && error != null)
				put("error", if (isErrored) error.orEmpty() else "")
			}
		},
	)
}

/** Projects the account co-authors, most recently synced first. */
private suspend fun ApplicationCall.sharedProjectsModel(
	projectAccessResolver: ProjectAccessResolver,
	userId: Long,
): Map<String, Any> {
	val locale = getLocale()
	val shared = projectAccessResolver.memberProjects(userId).sortedByDescending { it.lastSync }
	return mapOf(
		"hasSharedProjects" to shared.isNotEmpty(),
		"sharedProjects" to shared.map {
			mapOf(
				"name" to it.projectDef.name,
				"nameForUrl" to ProjectName.projectSegment(it.projectDef.name, it.projectDef.uuid.id),
				"sharedBy" to localizedMsg(
					locale,
					"dashboard_shared_by",
					it.ownerName ?: localizedMsg(locale, "dashboard_shared_by_unknown"),
				),
				"lastSync" to formatSyncDate(it.lastSync),
			)
		},
	)
}

private suspend fun penNameResultToMessage(call: ApplicationCall, result: PenNameResult): String {
	return when (result) {
		PenNameResult.VALID -> call.msg("penname_validation_valid")
		PenNameResult.TOO_SHORT -> call.msg("penname_validation_too_short", PenNameUseCase.MIN_PEN_NAME_LENGTH)
		PenNameResult.TOO_LONG -> call.msg("penname_validation_too_long", PenNameUseCase.MAX_PEN_NAME_LENGTH)
		PenNameResult.INVALID_CHARACTERS -> call.msg("penname_validation_invalid_chars")
		PenNameResult.NOT_AVAILABLE -> call.msg("penname_validation_taken")
	}
}

/** What the bio endpoint sends back: the swapped-in content plus the toast that goes with it. */
private data class BioOutcome(
	val message: String,
	val content: String = "",
	val toast: Toast = Toast.Error,
	val status: HttpStatusCode = HttpStatusCode.BadRequest
)

@Serializable
data class PenNameCheckResponse(
	val valid: Boolean,
	val available: Boolean,
	val message: String
)
