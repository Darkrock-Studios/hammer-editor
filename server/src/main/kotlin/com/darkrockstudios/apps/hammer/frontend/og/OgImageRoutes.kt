package com.darkrockstudios.apps.hammer.frontend.og

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.frontend.utils.displayHost
import com.darkrockstudios.apps.hammer.frontend.utils.findProjectByUrlSegment
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.resolveByPenName
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Dynamically generated per-entity OpenGraph images. Registered only when
 * [com.darkrockstudios.apps.hammer.ServerConfig.richLinkPreviews] is on. Only public, indexable
 * entities render — community authors, and publicly-published stories reached without a password —
 * so private or protected content never gets a share card (everything else 404s). Images are
 * disk-cached by [OgImageService], so scraper bursts render each card at most once.
 */
fun Route.ogImageRoutes(
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
	projectAccessRepository: ProjectAccessRepository,
	ogImageService: OgImageService,
) {
	get("/a/{penName}/og.png") {
		val penNameParam = call.parameters["penName"]
		if (penNameParam.isNullOrBlank()) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val account = resolveByPenName(penNameParam) { accountsRepository.findAccountByPenName(it) }
		val penName = account?.pen_name
		if (account == null || penName == null || !account.community_member) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val subtitle = call.msg("og_author_subtitle", call.displayHost())
		call.respondBytes(
			ogImageService.authorCard(account.id, penName, subtitle),
			ContentType.Image.PNG,
		)
	}

	get("/a/{penName}/{projectName}/og.png") {
		val penNameParam = call.parameters["penName"]
		val projectNameParam = call.parameters["projectName"]
		if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val account = resolveByPenName(penNameParam) { accountsRepository.findAccountByPenName(it) }
		val penName = account?.pen_name
		if (account == null || penName == null || !account.community_member) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val projectName = projectsRepository.findProjectByUrlSegment(account.id, projectNameParam)?.name
		if (projectName == null) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		// No password is supplied, so a Success here is necessarily public access.
		val result = projectAccessRepository.findAccessibleProject(penName, projectName, password = null)
		if (result !is PublicProjectResult.Success) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val kicker = call.msg("public_story_by")
		val attribution = call.msg("og_attribution")
		call.respondBytes(
			ogImageService.storyCard(
				result.projectUuid.toString(),
				projectName,
				penName,
				kicker,
				attribution,
			),
			ContentType.Image.PNG,
		)
	}
}
