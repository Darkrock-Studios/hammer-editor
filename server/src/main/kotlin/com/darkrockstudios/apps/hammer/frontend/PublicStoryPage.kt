package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import com.darkrockstudios.apps.hammer.story.StoryExportResult
import com.darkrockstudios.apps.hammer.story.StoryExportService
import io.ktor.http.*
import io.ktor.server.htmx.hx
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.publicStoryPage(
	storyExportService: StoryExportService,
	projectAccessRepository: ProjectAccessRepository
) {
	route("/a/{penName}/{projectName}") {
		get {
			val penNameParam = call.parameters["penName"]
			val projectNameParam = call.parameters["projectName"]

			if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			// Decode URL: URL decode then replace dashes with spaces
			val penName = ProjectName.decodeFromUrl(penNameParam)
			val projectName = ProjectName.decodeFromUrl(projectNameParam)

			// Check for password in query parameter
			val password = call.request.queryParameters["p"]

			when (val result = projectAccessRepository.findAccessibleProject(penName, projectName, password)) {
				is PublicProjectResult.NotFound -> {
					call.respond(HttpStatusCode.NotFound)
				}

				is PublicProjectResult.PasswordRequired -> {
					// Show password form
					val model = call.withDefaults(
						mapOf(
							"page_stylesheet" to "/assets/css/story.css",
							"penName" to penNameParam,
							"projectName" to projectNameParam,
							"error" to (password != null) // Show error if password was provided but invalid
						)
					)
					call.respond(MustacheContent("password-form.mustache", model))
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
									"authorPenNameUrl" to ProjectName.formatForUrl(result.penName),
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

		hx.post {
			val penNameParam = call.parameters["penName"]
			val projectNameParam = call.parameters["projectName"]

			if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@post
			}

			val formParams = call.receiveParameters()
			val password = formParams["password"]

			// Redirect to GET with password in query param
			if (!password.isNullOrBlank()) {
				call.respondRedirect("/a/$penNameParam/$projectNameParam?p=$password")
			} else {
				call.respondRedirect("/a/$penNameParam/$projectNameParam")
			}
		}
	}
}
