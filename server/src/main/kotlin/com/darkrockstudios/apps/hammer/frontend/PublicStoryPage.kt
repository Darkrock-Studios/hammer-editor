package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import com.darkrockstudios.apps.hammer.story.StoryExportResult
import com.darkrockstudios.apps.hammer.story.StoryExportService
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URLDecoder

fun Route.publicStoryPage(
	storyExportService: StoryExportService,
	projectAccessRepository: ProjectAccessRepository
) {
	route("/u/{penName}/{projectName}") {
		get {
			val penNameParam = call.parameters["penName"]
			val projectNameParam = call.parameters["projectName"]

			if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			// Decode URL: URL decode then replace dashes with spaces
			val penName = decodeFromUrl(penNameParam)
			val projectName = decodeFromUrl(projectNameParam)

			when (val result = projectAccessRepository.findPublicProject(penName, projectName)) {
				is PublicProjectResult.NotFound -> {
					call.respond(HttpStatusCode.NotFound)
				}

				is PublicProjectResult.Success -> {
					val exportResult = storyExportService.exportStoryAsHtml(
						userId = result.userId,
						projectId = result.projectUuid
					)

					when (exportResult) {
						is StoryExportResult.Success -> {
							val model = call.withDefaults(
								mapOf(
									"page_stylesheet" to "/assets/css/story.css",
									"projectName" to exportResult.projectName,
									"authorPenName" to result.penName,
									"storyHtml" to exportResult.html,
									"hasContent" to exportResult.hasContent,
									"sceneCount" to exportResult.sceneCount
								)
							)
							call.respond(MustacheContent("publicstory.mustache", model))
						}

						is StoryExportResult.ProjectNotFound -> {
							call.respond(HttpStatusCode.NotFound)
						}

						is StoryExportResult.Error -> {
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
	}
}

/**
 * Decode a URL segment: URL decode then replace dashes with spaces.
 * Example: "My-Story-Name" -> "My Story Name"
 */
private fun decodeFromUrl(urlSegment: String): String =
	URLDecoder.decode(urlSegment, "UTF-8").replace('-', ' ')
