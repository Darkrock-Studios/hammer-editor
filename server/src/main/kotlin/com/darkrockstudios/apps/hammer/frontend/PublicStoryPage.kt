package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderCollector
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import com.darkrockstudios.apps.hammer.story.PaginatedExportResult
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.story.WordCountUtils
import io.ktor.http.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun Route.publicStoryPage(
	storyExportService: StoryExportService,
	projectAccessRepository: ProjectAccessRepository,
	projectDao: ProjectDao,
	storyReaderCollector: StoryReaderCollector,
) {
	route("/a/{penName}/{projectName}") {
		get {
			val penNameParam = call.parameters["penName"]
			val projectNameParam = call.parameters["projectName"]

			if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			// Check for password and page in query parameters
			val password = call.request.queryParameters["p"]
			val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

			// The router has already percent-decoded the segments, so they are the exact names.
			// Fall back to the legacy dash-for-space slug so links made before the fix still resolve.
			var penName = penNameParam
			var projectName = projectNameParam
			var result = projectAccessRepository.findAccessibleProject(penName, projectName, password)
			if (result is PublicProjectResult.NotFound) {
				val legacyPenName = ProjectName.legacyUrlNameToName(penNameParam)
				val legacyProjectName = ProjectName.legacyUrlNameToName(projectNameParam)
				if (legacyPenName != penName || legacyProjectName != projectName) {
					val legacyResult =
						projectAccessRepository.findAccessibleProject(legacyPenName, legacyProjectName, password)
					if (legacyResult !is PublicProjectResult.NotFound) {
						penName = legacyPenName
						projectName = legacyProjectName
						result = legacyResult
					}
				}
			}
			val penNameForUrl = ProjectName.formatForUrl(penName)
			val projectNameForUrl = ProjectName.formatForUrl(projectName)

			when (val resolved = result) {
				is PublicProjectResult.NotFound -> {
					call.respond(HttpStatusCode.NotFound)
				}

				is PublicProjectResult.PasswordRequired -> {
					// Show password form
					val model = call.withDefaults(
						mapOf(
							"page_stylesheet" to "/assets/css/story.css",
							"penName" to penNameForUrl,
							"projectName" to projectNameForUrl,
							"error" to (password != null) // Show error if password was provided but invalid
						)
					)
					call.respond(MustacheContent("password-form.mustache", model))
				}

				is PublicProjectResult.Success -> {
					// Best-effort unique-reader count, skipping the author viewing their own story.
					val viewerId = call.sessions.get<UserSession>()?.userId
					if (viewerId != resolved.userId) {
						projectDao.getProjectIdOrNull(resolved.userId, resolved.projectUuid)?.let { projectId ->
							storyReaderCollector.record(
								projectId = projectId,
								clientIp = call.request.origin.remoteAddress,
								userAgent = call.request.userAgent(),
							)
						}
					}

					val exportResult = storyExportService.exportStoryAsHtmlPaginated(
						userId = resolved.userId,
						projectId = resolved.projectUuid,
						page = page
					)

					when (exportResult) {
						is PaginatedExportResult.Success -> {
							val data = exportResult.data
							val passwordParam = if (!password.isNullOrBlank()) "&p=${
								URLEncoder.encode(
									password,
									StandardCharsets.UTF_8
								)
							}" else ""

							val model = call.withDefaults(
								mapOf(
									"page_stylesheet" to "/assets/css/story.css",
									"projectName" to data.projectName,
									"authorPenName" to resolved.penName,
									"authorPenNameUrl" to ProjectName.formatForUrl(resolved.penName),
									"storyHtml" to data.pageHtml,
									"hasContent" to data.hasContent,
									"sceneCount" to data.sceneCount,
									"totalWordCount" to data.totalWordCount,
									"formattedWordCount" to WordCountUtils.formatWordCount(data.totalWordCount),
									"estimatedReadingTime" to data.estimatedReadingTimeMinutes,
									"currentPage" to data.currentPage,
									"totalPages" to data.totalPages,
									"hasPagination" to (data.totalPages > 1),
									"hasNextPage" to data.hasNextPage,
									"hasPrevPage" to data.hasPrevPage,
									"nextPageUrl" to "/a/$penNameForUrl/$projectNameForUrl?page=${data.nextPage}$passwordParam",
									"prevPageUrl" to "/a/$penNameForUrl/$projectNameForUrl?page=${data.prevPage}$passwordParam"
								)
							)
							call.respond(MustacheContent("publicstory.mustache", model))
						}

						is PaginatedExportResult.ProjectNotFound -> {
							call.respond(HttpStatusCode.NotFound)
						}

						is PaginatedExportResult.Error -> {
							val model = call.withDefaults(
								mapOf(
									"page_stylesheet" to "/assets/css/story.css",
									"errorMessage" to exportResult.message,
								)
							)
							call.respond(
								HttpStatusCode.InternalServerError,
								MustacheContent("storyerror.mustache", model)
							)
						}
					}
				}
			}
		}

		hx.post {
			val penNameParam = call.parameters["penName"]
			val projectNameParam = call.parameters["projectName"]

			if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@post
			}

			val formParams = call.receiveParameters()
			val password = formParams["password"]

			// Re-encode the segments: they arrive percent-decoded but go back into a URL path.
			val penNameForUrl = ProjectName.formatForUrl(penNameParam)
			val projectNameForUrl = ProjectName.formatForUrl(projectNameParam)

			// Redirect to GET with password in query param (URL encoded for safety)
			if (!password.isNullOrBlank()) {
				call.respondRedirect(
					"/a/$penNameForUrl/$projectNameForUrl?p=${
						URLEncoder.encode(
							password,
							StandardCharsets.UTF_8
						)
					}"
				)
			} else {
				call.respondRedirect("/a/$penNameForUrl/$projectNameForUrl")
			}
		}
	}
}
