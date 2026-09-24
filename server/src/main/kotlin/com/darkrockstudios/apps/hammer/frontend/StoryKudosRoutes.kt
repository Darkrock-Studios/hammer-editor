package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.Toast
import com.darkrockstudios.apps.hammer.frontend.utils.lookUpStory
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.respondTemplateWithToast
import com.darkrockstudios.apps.hammer.kudos.KudosGroup
import com.darkrockstudios.apps.hammer.kudos.KudosKind
import com.darkrockstudios.apps.hammer.kudos.KudosTally
import com.darkrockstudios.apps.hammer.kudos.SetPicksResult
import com.darkrockstudios.apps.hammer.kudos.StoryKudosRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.htmx.hx
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

private const val KUDOS_FRAGMENT = "partials/story-kudos.mustache"

private data class KudosStory(
	val projectId: Long,
	val authorId: Long,
	val authorPenName: String,
)

private val reactionIcons = mapOf(
	KudosKind.MOVED_ME to "fa-heart",
	KudosKind.MADE_ME_THINK to "fa-lightbulb",
	KudosKind.MADE_ME_LAUGH to "fa-face-laugh-beam",
	KudosKind.PAGE_TURNER to "fa-book-open",
)

/**
 * Reader kudos on a publicly published story. The section loads as its own fragment so the
 * story page's ETag never depends on kudos, and so per-reader picks stay out of the page shell.
 */
fun Route.storyKudosRoutes(
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
	projectAccessRepository: ProjectAccessRepository,
	projectDao: ProjectDao,
	storyKudosRepository: StoryKudosRepository,
) {
	/** Only password-free public access qualifies; private shares never carry kudos. */
	suspend fun ApplicationCall.resolveKudosStory(): KudosStory? {
		val resolved = lookUpStory(accountsRepository, projectsRepository, projectAccessRepository, password = null)
			?.result
		if (resolved !is PublicProjectResult.Success || !resolved.isPublic) return null
		val projectId = projectDao.getProjectIdOrNull(resolved.userId, resolved.projectUuid) ?: return null
		return KudosStory(projectId = projectId, authorId = resolved.userId, authorPenName = resolved.penName)
	}

	route("/a/{penName}/{projectName}/kudos") {
		hx.get {
			call.response.header(HttpHeaders.CacheControl, "no-store")
			val story = call.resolveKudosStory()
			if (story == null || !storyKudosRepository.isEnabled(story.projectId)) {
				call.respond(HttpStatusCode.NoContent)
				return@get
			}
			val model = call.kudosFragmentModel(story, storyKudosRepository)
			model["animateIn"] = true
			call.respond(MustacheContent(KUDOS_FRAGMENT, model))
		}

		// Every failure re-renders the reader's saved picks, so the chips never show an unsaved state.
		hx.post {
			call.response.header(HttpHeaders.CacheControl, "no-store")
			val story = call.resolveKudosStory()
			if (story == null) {
				call.respond(HttpStatusCode.NotFound)
				return@post
			}

			// Not a 401: StatusPages replaces those bodies with the full unauthorized page.
			val session = call.sessions.get<UserSession>()
			if (session == null) {
				respondTemplateWithToast(
					templatePath = KUDOS_FRAGMENT,
					model = call.kudosFragmentModel(story, storyKudosRepository),
					message = call.msg("kudos_error_sign_in"),
					toast = Toast.Info,
				)
				return@post
			}

			val requested = call.receiveParameters().getAll("kind").orEmpty()
				.mapNotNullTo(mutableSetOf(), KudosKind::fromKey)

			val result = storyKudosRepository.setPicks(story.projectId, story.authorId, session.userId, requested)
			if (result is SetPicksResult.Saved) {
				val model = call.kudosFragmentModel(story, storyKudosRepository, savedPicks = result.picks)
				call.respond(MustacheContent(KUDOS_FRAGMENT, model))
				return@post
			}

			val (messageKey, status) = when (result) {
				SetPicksResult.OwnStory -> "kudos_error_own_story" to HttpStatusCode.Forbidden
				SetPicksResult.Disabled -> "kudos_error_disabled" to HttpStatusCode.Conflict
				else -> "kudos_error_over_cap" to HttpStatusCode.BadRequest
			}
			respondTemplateWithToast(
				templatePath = KUDOS_FRAGMENT,
				model = call.kudosFragmentModel(
					story,
					storyKudosRepository,
					closed = result == SetPicksResult.Disabled,
				),
				message = call.msg(messageKey),
				toast = Toast.Error,
				status = status,
			)
		}
	}
}

/**
 * The author's Kudos sidebar panel: full counts, which only the author ever sees. Shown once
 * the story is published or has kudos; otherwise it renders as a hidden placeholder that the
 * publish toggle can swap in out of band.
 */
internal suspend fun ApplicationCall.kudosPanelModel(
	storyKudosRepository: StoryKudosRepository,
	projectId: Long,
	isPublished: Boolean,
	model: Map<String, Any>,
): Map<String, Any> {
	@Suppress("UNCHECKED_CAST")
	val messages = model["msg"] as Map<String, String>
	val tally = storyKudosRepository.tally(projectId)
	fun rows(group: KudosGroup): List<Map<String, Any>> {
		val ranked = tally.ranked(group)
		val top = ranked.maxOfOrNull { it.second } ?: 1L
		return ranked.map { (kind, count) ->
			mapOf(
				"label" to (messages[kind.messageKey] ?: kind.key),
				"count" to "%,d".format(count),
				"percent" to (count * 100 / top),
				"icon" to reactionIcons[kind].orEmpty(),
				"isPublic" to (count >= KudosTally.PUBLIC_THRESHOLD),
			)
		}
	}
	val craft = rows(KudosGroup.CRAFT)
	val reactions = rows(KudosGroup.REACTION)
	return mapOf(
		"showKudosPanel" to (isPublished || tally.givers > 0),
		"kudosEnabled" to storyKudosRepository.isEnabled(projectId),
		"hasKudos" to (tally.givers > 0),
		"kudosGivers" to "%,d".format(tally.givers),
		"kudosGiversLabel" to msg("story_kudos_givers_label", tally.givers),
		"craftKudos" to craft,
		"reactionKudos" to reactions,
		"hasCraftKudos" to craft.isNotEmpty(),
		"hasReactionKudos" to reactions.isNotEmpty(),
		"hasPublicKudos" to (craft + reactions).any { it["isPublic"] == true },
	)
}

/**
 * [savedPicks] skips re-reading picks that were just written. [closed] renders every chip
 * locked, for a story whose author turned kudos off while the reader had it open.
 */
private suspend fun ApplicationCall.kudosFragmentModel(
	story: KudosStory,
	storyKudosRepository: StoryKudosRepository,
	savedPicks: Set<KudosKind>? = null,
	closed: Boolean = false,
): MutableMap<String, Any> {
	val viewerId = sessions.get<UserSession>()?.userId
	val isAuthor = viewerId == story.authorId
	val canGive = viewerId != null && !isAuthor && !closed
	val picks = when {
		savedPicks != null -> savedPicks
		viewerId != null && !isAuthor -> storyKudosRepository.picksFor(story.projectId, viewerId)
		else -> emptySet()
	}
	val tally = storyKudosRepository.tally(story.projectId)

	val model = withDefaults()
	@Suppress("UNCHECKED_CAST")
	val messages = model["msg"] as Map<String, String>
	val label = { kind: KudosKind -> messages[kind.messageKey] ?: kind.key }

	suspend fun group(group: KudosGroup, promptKey: String, hintKey: String): Map<String, Any> {
		val picked = picks.count { it.group == group }
		// A full reaction group stays open: picking another reaction swaps it in.
		val isCraft = group == KudosGroup.CRAFT
		val chips = KudosKind.entries.filter { it.group == group }.map { kind ->
			val checked = kind in picks
			mapOf(
				"key" to kind.key,
				"label" to label(kind),
				"icon" to reactionIcons[kind].orEmpty(),
				"checked" to checked,
				"disabled" to (!canGive || (isCraft && picked >= group.maxPicks && !checked)),
			)
		}
		return mapOf(
			"prompt" to (messages[promptKey] ?: ""),
			"hint" to (messages[hintKey] ?: ""),
			"isReaction" to !isCraft,
			"showCount" to (isCraft && canGive),
			"count" to if (isCraft && canGive) msg("kudos_pick_count", picked, group.maxPicks) else "",
			"chips" to chips,
		)
	}

	val craftHighlights = tally.publicHighlights(KudosGroup.CRAFT).map(label)
	val reactionHighlights = tally.publicHighlights(KudosGroup.REACTION).map(label)

	model.putAll(
		mapOf(
			"kudosTitle" to if (isAuthor) {
				messages["kudos_title_author"].orEmpty()
			} else {
				msg("kudos_title_for", story.authorPenName)
			},
			"kudosUrl" to request.local.uri.substringBefore('?'),
			"groups" to listOf(
				group(KudosGroup.CRAFT, "kudos_craft_prompt", "kudos_craft_hint"),
				group(KudosGroup.REACTION, "kudos_reaction_prompt", "kudos_reaction_hint"),
			),
			"craftHighlights" to craftHighlights,
			"reactionHighlights" to reactionHighlights,
			"hasCraftHighlights" to craftHighlights.isNotEmpty(),
			"hasReactionHighlights" to reactionHighlights.isNotEmpty(),
			"hasHighlights" to (craftHighlights.isNotEmpty() || reactionHighlights.isNotEmpty()),
			"canGive" to canGive,
			"isAuthor" to isAuthor,
			"isSignedOut" to (viewerId == null),
			"justSaved" to (savedPicks?.isNotEmpty() == true),
		)
	)
	return model
}
