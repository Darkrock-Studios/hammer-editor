package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.findProjectByUrlSegment
import com.darkrockstudios.apps.hammer.frontend.utils.resolveByPenName
import com.darkrockstudios.apps.hammer.kudos.KudosGroup
import com.darkrockstudios.apps.hammer.kudos.KudosKind
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

private data class KudosStory(
	val projectId: Long,
	val authorId: Long,
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
		val penNameParam = parameters["penName"]
		val projectNameParam = parameters["projectName"]
		if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) return null

		val account = resolveByPenName(penNameParam) { accountsRepository.findAccountByPenName(it) }
		val penName = account?.pen_name ?: return null
		val projectName = projectsRepository.findProjectByUrlSegment(account.id, projectNameParam)?.name
			?: return null

		val resolved = projectAccessRepository.findAccessibleProject(penName, projectName, password = null)
		if (resolved !is PublicProjectResult.Success || !resolved.isPublic) return null

		val projectId = projectDao.getProjectIdOrNull(resolved.userId, resolved.projectUuid) ?: return null
		return KudosStory(projectId = projectId, authorId = resolved.userId)
	}

	route("/a/{penName}/{projectName}/kudos") {
		hx.get {
			call.response.header(HttpHeaders.CacheControl, "no-store")
			val story = call.resolveKudosStory()
			if (story == null || !storyKudosRepository.isEnabled(story.projectId)) {
				call.respond(HttpStatusCode.NoContent)
				return@get
			}
			call.respondKudosFragment(story, storyKudosRepository, justSaved = false)
		}

		hx.post {
			call.response.header(HttpHeaders.CacheControl, "no-store")
			val session = call.sessions.get<UserSession>()
			if (session == null) {
				call.respond(HttpStatusCode.Unauthorized)
				return@post
			}
			val story = call.resolveKudosStory()
			if (story == null) {
				call.respond(HttpStatusCode.NotFound)
				return@post
			}

			val requested = call.receiveParameters().getAll("kind").orEmpty()
				.mapNotNullTo(mutableSetOf(), KudosKind::fromKey)

			when (storyKudosRepository.setPicks(story.projectId, story.authorId, session.userId, requested)) {
				is SetPicksResult.Saved -> call.respondKudosFragment(story, storyKudosRepository, justSaved = true)
				SetPicksResult.OwnStory -> call.respond(HttpStatusCode.Forbidden)
				SetPicksResult.Disabled -> call.respond(HttpStatusCode.Conflict)
				SetPicksResult.OverCap -> call.respond(HttpStatusCode.BadRequest)
			}
		}
	}
}

/** The author's Kudos sidebar panel: full counts, which only the author ever sees. */
internal suspend fun kudosPanelModel(
	storyKudosRepository: StoryKudosRepository,
	projectId: Long,
	model: Map<String, Any>,
): Map<String, Any> {
	@Suppress("UNCHECKED_CAST")
	val messages = model["msg"] as Map<String, String>
	val tally = storyKudosRepository.tally(projectId)
	fun rows(group: KudosGroup) = tally.ranked(group).map { (kind, count) ->
		mapOf("label" to (messages[kind.messageKey] ?: kind.key), "count" to "%,d".format(count))
	}
	val craft = rows(KudosGroup.CRAFT)
	val reactions = rows(KudosGroup.REACTION)
	return mapOf(
		"kudosEnabled" to storyKudosRepository.isEnabled(projectId),
		"hasKudos" to (tally.givers > 0),
		"kudosGivers" to "%,d".format(tally.givers),
		"craftKudos" to craft,
		"reactionKudos" to reactions,
		"hasCraftKudos" to craft.isNotEmpty(),
		"hasReactionKudos" to reactions.isNotEmpty(),
	)
}

private suspend fun ApplicationCall.respondKudosFragment(
	story: KudosStory,
	storyKudosRepository: StoryKudosRepository,
	justSaved: Boolean,
) {
	val viewerId = sessions.get<UserSession>()?.userId
	val isAuthor = viewerId == story.authorId
	val canGive = viewerId != null && !isAuthor
	val picks = if (canGive) storyKudosRepository.picksFor(story.projectId, viewerId) else emptySet()
	val tally = storyKudosRepository.tally(story.projectId)

	val model = withDefaults()
	@Suppress("UNCHECKED_CAST")
	val messages = model["msg"] as Map<String, String>
	val label = { kind: KudosKind -> messages[kind.messageKey] ?: kind.key }

	val craftFull = picks.count { it.group == KudosGroup.CRAFT } >= KudosGroup.CRAFT.maxPicks
	fun chips(group: KudosGroup) = KudosKind.entries.filter { it.group == group }.map { kind ->
		val checked = kind in picks
		mapOf(
			"key" to kind.key,
			"label" to label(kind),
			"checked" to checked,
			"disabled" to (!canGive || (group == KudosGroup.CRAFT && craftFull && !checked)),
		)
	}

	val craftHighlights = tally.publicHighlights(KudosGroup.CRAFT).map(label)
	val reactionHighlights = tally.publicHighlights(KudosGroup.REACTION).map(label)

	model.putAll(
		mapOf(
			"kudosUrl" to request.local.uri.substringBefore('?'),
			"craftChips" to chips(KudosGroup.CRAFT),
			"reactionChips" to chips(KudosGroup.REACTION),
			"craftHighlights" to craftHighlights,
			"reactionHighlights" to reactionHighlights,
			"hasCraftHighlights" to craftHighlights.isNotEmpty(),
			"hasReactionHighlights" to reactionHighlights.isNotEmpty(),
			"hasHighlights" to (craftHighlights.isNotEmpty() || reactionHighlights.isNotEmpty()),
			"canGive" to canGive,
			"isAuthor" to isAuthor,
			"isSignedOut" to (viewerId == null),
			"justSaved" to (justSaved && picks.isNotEmpty()),
		)
	)
	respond(MustacheContent("partials/story-kudos.mustache", model))
}
