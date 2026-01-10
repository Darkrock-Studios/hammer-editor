package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

fun Route.feedPage(
	projectAccessRepository: ProjectAccessRepository,
	serverConfig: ServerConfig
) {
	route("/feed") {
		get {
			// Only accessible if community is enabled
			if (!serverConfig.communityEnabled) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val pageSize = 10
			val queryPage = call.request.queryParameters["page"]?.toIntOrNull() ?: 0

			val totalCount = projectAccessRepository.countCommunityFeedStories()
			val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
			val currentPage = if (totalPages > 0) queryPage.coerceIn(0, totalPages - 1) else 0

			val stories = projectAccessRepository.getCommunityFeedStories(currentPage, pageSize)

			val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
			val storiesForTemplate = stories.map { story ->
				val formattedDate = try {
					val instant = sqliteDateTimeStringToInstant(story.publishedAt)
					val zoned = java.time.Instant.ofEpochSecond(instant.epochSeconds).atZone(ZoneId.systemDefault())
					dateFormatter.format(zoned)
				} catch (e: Exception) {
					story.publishedAt
				}

				mapOf(
					"projectName" to story.projectName,
					"projectNameUrl" to ProjectName.formatForUrl(story.projectName),
					"penName" to story.penName,
					"penNameUrl" to ProjectName.formatForUrl(story.penName),
					"publishedAt" to formattedDate
				)
			}

			val model = call.withDefaults(
				mapOf(
					"page_stylesheet" to "/assets/css/feed.css",
					"feedActive" to true,
					"stories" to storiesForTemplate,
					"hasStories" to stories.isNotEmpty(),
					"storyCount" to totalCount,
					"currentPage" to currentPage,
					"currentPageDisplay" to currentPage + 1,
					"totalPages" to totalPages,
					"hasNextPage" to (currentPage < totalPages - 1),
					"hasPrevPage" to (currentPage > 0),
					"nextPage" to currentPage + 1,
					"prevPage" to currentPage - 1,
					"isPaged" to (totalPages > 1)
				)
			)

			call.respond(MustacheContent("feed.mustache", model))
		}
	}
}
