package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.frontend.utils.*
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.StoryExportResult
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import io.ktor.http.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.storyPage(
	storyExportService: StoryExportService,
	projectAccessRepository: ProjectAccessRepository,
	projectsRepository: ProjectsRepository,
	accountsRepository: AccountsRepository
) {
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
						val account = accountsRepository.getAccount(session.userId)
						val hasPenName = !account.pen_name.isNullOrBlank()

						val isPublished = projectAccessRepository.isPublished(session.userId, projectId)
						val publicUrl = if (isPublished) {
							"${call.request.origin.scheme}://${call.request.host()}:${call.request.port()}/public/story/$projectUuidStr"
						} else {
							""
						}

						val projectSyncData = projectsRepository.getProjectWithSyncDate(session.userId, projectId)
						val lastSyncFormatted = projectSyncData?.let { formatSyncDate(it.lastSync) } ?: ""

						val model = call.withDefaults(
							mapOf(
								"page_stylesheet" to "/assets/css/story.css",
								"page_script" to "/assets/js/story.js",
								"projectName" to result.projectName,
								"projectUuid" to projectUuidStr,
								"storyHtml" to result.html,
								"hasContent" to result.hasContent,
								"hasPenName" to hasPenName,
								"isPublished" to isPublished,
								"publicUrl" to publicUrl,
								"lastSync" to lastSyncFormatted,
								"sceneCount" to result.sceneCount
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

			hx.post("/publish") {
				val session = call.sessions.requireUser()
				val projectUuidStr = call.parameters["projectUuid"]

				if (projectUuidStr.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@post
				}

				val projectId = ProjectId(projectUuidStr)
				val isCurrentlyPublished = projectAccessRepository.isPublished(session.userId, projectId)

				val (newIsPublished, toastMessage, toastType) = if (isCurrentlyPublished) {
					// Unpublish: delete the access record
					projectAccessRepository.deleteAccess(session.userId, projectId)
					Triple(false, call.msg("story_toast_unpublished"), Toast.Info)
				} else {
					// Publish: create access record with null password and null expiry
					projectAccessRepository.setAccess(
						userId = session.userId,
						projectUuid = projectId,
						password = null,
						expiresAt = null
					)
					Triple(true, call.msg("story_toast_published"), Toast.Success)
				}

				// Build the public URL if published
				val publicUrl = if (newIsPublished) {
					"${call.request.origin.scheme}://${call.request.host()}:${call.request.port()}/public/story/$projectUuidStr"
				} else {
					""
				}

				// Render the partial with updated state
				val model = call.withDefaults(
					mapOf(
						"projectUuid" to projectUuidStr,
						"isPublished" to newIsPublished,
						"publicUrl" to publicUrl
					)
				)

				respondTemplateWithToast(
					templatePath = "partials/story-publish.mustache",
					model = model,
					message = toastMessage,
					toast = toastType
				)
			}
		}
	}
}

private fun formatSyncDate(sqliteDateTime: String): String {
	return try {
		val instant = sqliteDateTimeStringToInstant(sqliteDateTime)
		val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
		val zoned = java.time.Instant.ofEpochSecond(instant.epochSeconds).atZone(java.time.ZoneId.systemDefault())
		formatter.format(zoned)
	} catch (e: Exception) {
		sqliteDateTime
	}
}
