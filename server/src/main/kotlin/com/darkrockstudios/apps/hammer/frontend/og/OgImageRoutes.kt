package com.darkrockstudios.apps.hammer.frontend.og

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.publicBaseUrl
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Dynamically generated per-entity OpenGraph images. Registered only when
 * [com.darkrockstudios.apps.hammer.ServerConfig.richLinkPreviews] is on.
 *
 * The routes resolve their subject purely from a stable identifier — the account id for an author,
 * the project UUID for a story — and render only fields read back from the database. Nothing the
 * caller supplies (path text, the `Host` header) is ever drawn into the image or mixed into the
 * cache key. Only public, indexable subjects render: community authors, and publicly-published
 * (password-free, unexpired) stories; everything else 404s so the page's static fallback card is
 * used instead. Rendering is CPU-bound (headless AWT), so it runs off the event loop; the disk
 * cache in [OgImageService] collapses a scraper burst to a single render per subject.
 */
fun Route.ogImageRoutes(
	accountsRepository: AccountsRepository,
	projectAccessRepository: ProjectAccessRepository,
	ogImageService: OgImageService,
) {
	get("/og/a/{accountId}.png") {
		val accountId = call.parameters["accountId"]?.toLongOrNull()
		if (accountId == null) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val account = accountsRepository.getAccountOrNull(accountId)
		val penName = account?.pen_name
		if (account == null || penName == null || !account.community_member) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val subtitle = call.msg("og_author_subtitle", call.ogHost())
		val bytes = withContext(Dispatchers.IO) {
			ogImageService.authorCard(account.id, penName, subtitle)
		}
		call.respondBytes(bytes, ContentType.Image.PNG)
	}

	get("/og/s/{projectUuid}.png") {
		val projectUuid = call.parameters["projectUuid"]
		// Validate the UUID shape before querying: project.uuid is a Postgres UUID column, so a
		// malformed path segment would raise a cast error (500) rather than a clean miss.
		if (projectUuid.isNullOrBlank() || runCatching { UUID.fromString(projectUuid) }.isFailure) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val result = projectAccessRepository.findPublicProjectByUuid(ProjectId(projectUuid))
		if (result !is PublicProjectResult.Success) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val kicker = call.msg("public_story_by")
		val attribution = call.msg("og_attribution")
		val bytes = withContext(Dispatchers.IO) {
			ogImageService.storyCard(result.projectUuid.id, result.projectName, result.penName, kicker, attribution)
		}
		call.respondBytes(bytes, ContentType.Image.PNG)
	}
}

/**
 * The server's own host for the author card subtitle, taken from the configured public URL. Never
 * the request `Host` (client-controlled); falls back to the brand name when the public URL is unset
 * so the value stays stable and is safe to bake into the cached image.
 */
private fun ApplicationCall.ogHost(): String =
	publicBaseUrl()?.substringAfter("://")?.substringBefore('/')?.takeIf { it.isNotBlank() } ?: "Hammer"
