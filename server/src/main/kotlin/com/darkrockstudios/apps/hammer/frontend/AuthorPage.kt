package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.authorProfileJsonLd
import com.darkrockstudios.apps.hammer.frontend.utils.canonicalUrl
import com.darkrockstudios.apps.hammer.frontend.utils.metaDescription
import com.darkrockstudios.apps.hammer.frontend.utils.resolveByPenName
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant

fun Route.authorPage(
	accountsRepository: AccountsRepository,
	projectAccessRepository: ProjectAccessRepository,
	markdownService: MarkdownService,
	serverConfig: ServerConfig,
) {
	route("/a/{penName}") {
		get {
			val penNameParam = call.parameters["penName"]

			if (penNameParam.isNullOrBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			// Resolve the account from the pen-name segment (verbatim, then dashes as spaces).
			val account = resolveByPenName(penNameParam) { accountsRepository.findAccountByPenName(it) }
			val penName = account?.pen_name
			if (account == null || penName == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}
			val penNameForUrl = ProjectName.penNameForUrl(penName)

			// Only surface author pages of community participants to search indexes.
			call.applyRobotsTag(indexable = account.community_member)

			// Get published stories for this author
			val stories = projectAccessRepository.getPublishedStoriesByPenName(penName)

			// Render bio markdown to sanitized HTML
			val bioHtml = account.bio?.let { markdownService.markdownToSafeHtml(it) }

			// Format stories for the template
			val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
			val formattedStories = stories.map { story ->
				val formattedDate = try {
					dateFormatter.format(story.publishedAt.toJavaInstant().atZone(ZoneId.systemDefault()))
				} catch (_: Exception) {
					story.publishedAt.toString()
				}

				mapOf(
					"name" to story.projectName,
					"urlName" to ProjectName.projectSegment(story.projectName, story.projectUuid),
					"urlPenName" to penNameForUrl,
					"publishedAt" to formattedDate
				)
			}

			val model = call.withDefaults(
				mapOf(
					"page_stylesheet" to "/assets/css/author.css",
					"title" to "$penName — Hammer",
					"ogType" to "profile",
					// Dynamic card only when the OG route would actually serve it (community author);
					// otherwise the static fallback, so the share preview is never a broken 404.
					"ogImage" to if (serverConfig.richLinkPreviews && account.community_member) {
						call.canonicalUrl("/og/a/${account.id}.png")
					} else {
						call.canonicalUrl("/assets/images/og-author.png")
					},
					"penName" to penName,
					"urlPenName" to penNameForUrl,
					"bio" to (account.bio ?: ""),
					"bioHtml" to (bioHtml ?: ""),
					"stories" to formattedStories,
					"hasStories" to stories.isNotEmpty(),
					"storyCount" to stories.size
				)
			)
			metaDescription(account.bio)?.let { model["metaDescription"] = it }
			// Structured data only for indexable (community) authors.
			if (account.community_member) {
				model["jsonLd"] = authorProfileJsonLd(
					name = penName,
					url = call.canonicalUrl("/a/$penNameForUrl"),
					description = metaDescription(account.bio),
				)
			}
			call.respond(MustacheContent("author.mustache", model))
		}
	}
}
