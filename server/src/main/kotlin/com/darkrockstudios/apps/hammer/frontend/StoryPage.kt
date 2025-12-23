package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.frontend.utils.authenticatedOnly
import com.darkrockstudios.apps.hammer.frontend.utils.requireUser
import com.darkrockstudios.apps.hammer.story.StoryExportResult
import com.darkrockstudios.apps.hammer.story.StoryExportService
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.storyPage(storyExportService: StoryExportService) {
	authenticatedOnly {
		route("/story/{projectUuid}") {
			get {
				val session = call.sessions.requireUser()
				val projectUuidStr = call.parameters["projectUuid"]

				if (projectUuidStr.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				val projectId = ProjectId(projectUuidStr)
				val result = storyExportService.exportStoryAsHtml(
					userId = session.userId,
					projectId = projectId
				)

				when (result) {
					is StoryExportResult.Success -> {
						val model = call.withDefaults(
							mapOf(
								"page_stylesheet" to "/assets/css/story.css",
								"projectName" to result.projectName,
								"storyHtml" to result.html,
								"hasContent" to result.hasContent
							)
						)
						call.respond(MustacheContent("story.mustache", model))
					}

					is StoryExportResult.ProjectNotFound -> {
						call.respond(HttpStatusCode.NotFound)
					}

					is StoryExportResult.Error -> {
						val model = call.withDefaults(
							mapOf(
								"page_stylesheet" to "/assets/css/story.css",
								"errorMessage" to result.message,
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
