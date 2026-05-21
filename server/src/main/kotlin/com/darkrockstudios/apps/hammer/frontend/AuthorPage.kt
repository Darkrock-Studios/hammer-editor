package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
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
	markdownService: MarkdownService
) {
	route("/a/{penName}") {
		get {
			val penNameParam = call.parameters["penName"]

			if (penNameParam.isNullOrBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			// Decode URL: URL decode then replace dashes with spaces
			val penName = ProjectName.decodeFromUrl(penNameParam)

			// Check if the pen name exists
			val account = accountsRepository.findAccountByPenName(penName)
			if (account == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

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
					"urlName" to ProjectName.formatForUrl(story.projectName),
					"urlPenName" to penNameParam,
					"publishedAt" to formattedDate
				)
			}

			val model = call.withDefaults(
				mapOf(
					"page_stylesheet" to "/assets/css/author.css",
					"penName" to penName,
					"urlPenName" to penNameParam,
					"bio" to (account.bio ?: ""),
					"bioHtml" to (bioHtml ?: ""),
					"stories" to formattedStories,
					"hasStories" to stories.isNotEmpty(),
					"storyCount" to stories.size
				)
			)
			call.respond(MustacheContent("author.mustache", model))
		}
	}
}
