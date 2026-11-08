package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.frontend.utils.StoryProject
import com.darkrockstudios.apps.hammer.frontend.utils.Toast
import com.darkrockstudios.apps.hammer.frontend.utils.authenticatedOnly
import com.darkrockstudios.apps.hammer.frontend.utils.findStoryProject
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
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import com.darkrockstudios.apps.hammer.project.InviteRefusal
import com.darkrockstudios.apps.hammer.project.InviteRefusedException
import com.darkrockstudios.apps.hammer.project.NotOwnerException
import com.darkrockstudios.apps.hammer.project.ProjectAccessResolver
import com.darkrockstudios.apps.hammer.project.ProjectMemberRepository
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import io.ktor.htmx.HxResponseHeaders
import io.ktor.htmx.HxSwap
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.htmx.hx
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.sessions

private const val PANEL = "partials/coauthor-panel.mustache"
private const val INVITE_FORM = "partials/coauthor-invite-form.mustache"
private const val CLOSE_INVITE_DIALOG = """<div id="coauthor-dialog-container" hx-swap-oob="innerHTML"></div>"""

/** The owner's management of who co-authors a story. Every route here is owner-only. */
fun Route.coAuthorRoutes(
	projectsRepository: ProjectsRepository,
	projectAccessResolver: ProjectAccessResolver,
	projectMemberRepository: ProjectMemberRepository,
	accountsRepository: AccountsRepository,
) {
	/** Answers 404 for a project the caller cannot see and 403 for one they co-author. */
	suspend fun RoutingContext.ownedProject(): StoryProject? {
		val session = call.sessions.requireUser()
		val project = call.parameters["projectName"]
			?.let { findStoryProject(projectsRepository, projectAccessResolver, session.userId, it) }
		when {
			project == null -> call.respond(HttpStatusCode.NotFound)
			!project.isOwner -> call.respond(HttpStatusCode.Forbidden)
			else -> return project
		}
		return null
	}

	suspend fun RoutingContext.panelModel(project: StoryProject): Map<String, Any> {
		val userId = call.sessions.requireUser().userId
		val hasPenName = !accountsRepository.getAccount(userId).pen_name.isNullOrBlank()
		return call.withDefaults(
			call.coAuthorPanelModel(projectMemberRepository, userId, project.projectId, project.segment, hasPenName)
		)
	}

	authenticatedOnly {
		route("/story/{projectName}/coauthors") {
			hx.get("/invite-dialog") {
				val project = ownedProject() ?: return@get
				val model = call.withDefaults(mapOf("projectNameForUrl" to project.segment))
				call.respond(MustacheContent("partials/coauthor-invite-dialog.mustache", model))
			}

			// The design caps invites with the login limiter rather than one of their own.
			rateLimit(RateLimitName(LOGIN_RATE_LIMIT)) {
				hx.post("/invites") {
					val project = ownedProject() ?: return@post
					val userId = call.sessions.requireUser().userId
					val email = call.receiveParameters()["email"]?.trim().orEmpty()

					val result = projectMemberRepository.createInvite(userId, project.projectId, email)
					if (result is ServerResult.Success) {
						respondHtmlWithToast(
							content = renderTemplate(PANEL, panelModel(project)) + CLOSE_INVITE_DIALOG,
							message = call.msg("coauthor_toast_invited", email.lowercase()),
						)
						return@post
					}

					when (val cause = (result as? ServerResult.Failure)?.exception) {
						is InviteRefusedException -> respondInviteRefused(project, email, cause.reason)
						is NotOwnerException -> respondToast(call.msg("api_projects_error_not_owner"), Toast.Error, HttpStatusCode.Forbidden)
						is ProjectNotFound -> respondToast(call.msg("coauthor_error_gone"), Toast.Error, HttpStatusCode.NotFound)
						else -> respondToast(call.msg("api_error_unknown"), Toast.Error, HttpStatusCode.InternalServerError)
					}
				}
			}

			hx.delete("/invites/{inviteId}") {
				val project = ownedProject() ?: return@delete
				val inviteId = call.parameters["inviteId"]?.toLongOrNull()
				if (inviteId == null) {
					call.respond(HttpStatusCode.BadRequest)
					return@delete
				}
				projectMemberRepository.revokeInvite(call.sessions.requireUser().userId, project.projectId, inviteId)
				respondTemplateWithToast(PANEL, panelModel(project), call.msg("coauthor_toast_revoked"), Toast.Info)
			}

			hx.delete("/members/{memberId}") {
				val project = ownedProject() ?: return@delete
				val memberId = call.parameters["memberId"]?.toLongOrNull()
				if (memberId == null) {
					call.respond(HttpStatusCode.BadRequest)
					return@delete
				}
				projectMemberRepository.removeMember(call.sessions.requireUser().userId, project.projectId, memberId)
				respondTemplateWithToast(PANEL, panelModel(project), call.msg("coauthor_toast_removed"), Toast.Info)
			}
		}
	}
}

/** Re-renders the invite form with the refusal under the address, leaving the dialog open. */
private suspend fun RoutingContext.respondInviteRefused(project: StoryProject, email: String, refusal: InviteRefusal) {
	val message = when (refusal) {
		InviteRefusal.INVALID_EMAIL -> call.msg("coauthor_error_invalid_email")
		InviteRefusal.OWNER_PEN_NAME_REQUIRED -> call.msg("coauthor_error_pen_name")
		InviteRefusal.OWN_ADDRESS -> call.msg("coauthor_error_own_address")
		InviteRefusal.ALREADY_MEMBER -> call.msg("coauthor_error_already_member")
		InviteRefusal.LIMIT_REACHED -> call.msg("coauthor_error_limit", ProjectMemberRepository.MAX_CO_AUTHORS)
	}
	val model = call.withDefaults(
		mapOf(
			"projectNameForUrl" to project.segment,
			"inviteEmail" to email,
			"inviteError" to message,
		)
	)
	call.response.header(HxResponseHeaders.Retarget, "#coauthor-invite-form")
	call.response.header(HxResponseHeaders.Reswap, HxSwap.outerHtml)
	respondInlineError(renderTemplate(INVITE_FORM, model))
}

/** The owner's Co-authors panel on the story page: members first, then pending invites. */
internal suspend fun ApplicationCall.coAuthorPanelModel(
	projectMemberRepository: ProjectMemberRepository,
	ownerId: Long,
	projectId: ProjectId,
	projectNameForUrl: String,
	hasPenName: Boolean,
): Map<String, Any> {
	val members = (projectMemberRepository.listMembers(ownerId, projectId) as? ServerResult.Success)
		?.data.orEmpty()
	val invites = (projectMemberRepository.listInvites(ownerId, projectId) as? ServerResult.Success)
		?.data.orEmpty()
	val people = members.size + invites.size
	val max = ProjectMemberRepository.MAX_CO_AUTHORS
	val locale = getLocale()

	return mapOf(
		"projectNameForUrl" to projectNameForUrl,
		"coauthorMembers" to members.map {
			mapOf(
				"userId" to it.userId,
				"penName" to (it.penName ?: localizedMsg(locale, "coauthor_member_no_pen_name")),
				"meta" to localizedMsg(locale, "coauthor_member_joined", formatSyncDate(it.joinedAt)),
			)
		},
		"coauthorInvites" to invites.map {
			mapOf(
				"id" to it.id,
				"email" to it.email,
				"meta" to localizedMsg(locale, "coauthor_invite_sent", formatSyncDate(it.createdAt)),
			)
		},
		"hasCoauthorPeople" to (people > 0),
		"coauthorCount" to localizedMsg(locale, "coauthor_panel_count", people, max),
		"coauthorNeedsPenName" to !hasPenName,
		"coauthorCanInvite" to (hasPenName && people < max),
		"coauthorAtCap" to (hasPenName && people >= max),
		"coauthorCapNote" to localizedMsg(locale, "coauthor_panel_at_cap", max),
	)
}
