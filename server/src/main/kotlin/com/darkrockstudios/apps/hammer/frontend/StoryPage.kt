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
import io.ktor.server.application.*
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
						val hasAnyAccess = projectAccessRepository.hasAnyAccess(session.userId, projectId)
						val accessEntries = projectAccessRepository.getPrivateAccessEntries(session.userId, projectId)
						val publicUrl = if (hasAnyAccess && hasPenName) {
							call.constructPublicUrl(account.pen_name, result.projectName)
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
								"hasAnyAccess" to hasAnyAccess,
								"publicUrl" to publicUrl,
								"lastSync" to lastSyncFormatted,
								"sceneCount" to result.sceneCount,
								"accessEntries" to accessEntries,
								"hasAccessEntries" to accessEntries.isNotEmpty()
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
					// Unpublish: delete only the public access record
					projectAccessRepository.unpublish(session.userId, projectId)
					Triple(false, call.msg("story_toast_unpublished"), Toast.Info)
				} else {
					// Publish: create public access record
					projectAccessRepository.publish(session.userId, projectId)
					Triple(true, call.msg("story_toast_published"), Toast.Success)
				}

				// Check if any access exists (public or private)
				val hasAnyAccess = projectAccessRepository.hasAnyAccess(session.userId, projectId)
				val accessEntries = projectAccessRepository.getPrivateAccessEntries(session.userId, projectId)

				// Build the public URL if any access exists
				val publicUrl = if (hasAnyAccess) {
					val account = accountsRepository.getAccount(session.userId)
					val project = projectsRepository.getProjectWithSyncDate(session.userId, projectId)
					if (account.pen_name != null && project != null) {
						call.constructPublicUrl(account.pen_name, project.name)
					} else {
						""
					}
				} else {
					""
				}

				// Render the partial with updated state
				val model = call.withDefaults(
					mapOf(
						"projectUuid" to projectUuidStr,
						"isPublished" to newIsPublished,
						"hasAnyAccess" to hasAnyAccess,
						"publicUrl" to publicUrl,
						"accessEntries" to accessEntries,
						"hasAccessEntries" to accessEntries.isNotEmpty()
					)
				)

				respondTemplateWithToast(
					templatePath = "partials/story-publish.mustache",
					model = model,
					message = toastMessage,
					toast = toastType
				)
			}

			hx.get("/share-dialog") {
				val projectUuidStr = call.parameters["projectUuid"]

				if (projectUuidStr.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				// Get tomorrow's date as minimum date for the date picker
				val tomorrow = java.time.LocalDate.now().plusDays(1)
				val minDate = tomorrow.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

				val model = call.withDefaults(
					mapOf(
						"projectUuid" to projectUuidStr,
						"minDate" to minDate
					)
				)

				call.respond(MustacheContent("partials/share-dialog.mustache", model))
			}

			hx.get("/publish-warning") {
				val projectUuidStr = call.parameters["projectUuid"]

				if (projectUuidStr.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				val model = call.withDefaults(
					mapOf(
						"projectUuid" to projectUuidStr
					)
				)

				call.respond(MustacheContent("partials/publish-warning-dialog.mustache", model))
			}

			hx.post("/access") {
				val session = call.sessions.requireUser()
				val projectUuidStr = call.parameters["projectUuid"]

				if (projectUuidStr.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@post
				}

				val formParams = call.receiveParameters()
				val password = formParams["password"]
				val expiresAt = formParams["expiresAt"]?.ifBlank { null }

				if (password.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@post
				}

				val projectId = ProjectId(projectUuidStr)

				// Convert date to SQLite datetime format (YYYY-MM-DD HH:MM:SS)
				val expiresAtSqlite = expiresAt?.let { "$it 23:59:59" }

				projectAccessRepository.createPrivateAccess(
					userId = session.userId,
					projectUuid = projectId,
					password = password,
					expiresAt = expiresAtSqlite
				)

				// Return updated publish section
				val isPublished = projectAccessRepository.isPublished(session.userId, projectId)
				val hasAnyAccess = projectAccessRepository.hasAnyAccess(session.userId, projectId)
				val accessEntries = projectAccessRepository.getPrivateAccessEntries(session.userId, projectId)

				val publicUrl = if (hasAnyAccess) {
					val account = accountsRepository.getAccount(session.userId)
					val project = projectsRepository.getProjectWithSyncDate(session.userId, projectId)
					if (account.pen_name != null && project != null) {
						call.constructPublicUrl(account.pen_name, project.name)
					} else ""
				} else ""

				val model = call.withDefaults(
					mapOf(
						"projectUuid" to projectUuidStr,
						"isPublished" to isPublished,
						"hasAnyAccess" to hasAnyAccess,
						"publicUrl" to publicUrl,
						"accessEntries" to accessEntries,
						"hasAccessEntries" to accessEntries.isNotEmpty()
					)
				)

				respondTemplateWithToast(
					templatePath = "partials/story-publish.mustache",
					model = model,
					message = call.msg("story_toast_access_created"),
					toast = Toast.Success
				)
			}

			hx.delete("/access/{accessId}") {
				val session = call.sessions.requireUser()
				val projectUuidStr = call.parameters["projectUuid"]
				val accessIdStr = call.parameters["accessId"]

				if (projectUuidStr.isNullOrBlank() || accessIdStr.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@delete
				}

				val projectId = ProjectId(projectUuidStr)
				val accessId = accessIdStr.toLongOrNull()

				if (accessId == null) {
					call.respond(HttpStatusCode.BadRequest)
					return@delete
				}

				projectAccessRepository.deleteAccessById(session.userId, projectId, accessId)

				// Return updated publish section
				val isPublished = projectAccessRepository.isPublished(session.userId, projectId)
				val hasAnyAccess = projectAccessRepository.hasAnyAccess(session.userId, projectId)
				val accessEntries = projectAccessRepository.getPrivateAccessEntries(session.userId, projectId)

				val publicUrl = if (hasAnyAccess) {
					val account = accountsRepository.getAccount(session.userId)
					val project = projectsRepository.getProjectWithSyncDate(session.userId, projectId)
					if (account.pen_name != null && project != null) {
						call.constructPublicUrl(account.pen_name, project.name)
					} else ""
				} else ""

				val model = call.withDefaults(
					mapOf(
						"projectUuid" to projectUuidStr,
						"isPublished" to isPublished,
						"hasAnyAccess" to hasAnyAccess,
						"publicUrl" to publicUrl,
						"accessEntries" to accessEntries,
						"hasAccessEntries" to accessEntries.isNotEmpty()
					)
				)

				respondTemplateWithToast(
					templatePath = "partials/story-publish.mustache",
					model = model,
					message = call.msg("story_toast_access_deleted"),
					toast = Toast.Info
				)
			}
		}
	}
}

private fun ApplicationCall.constructPublicUrl(penName: String, projectName: String): String {
	val penNameForUrl = ProjectName.formatForUrl(penName)
	val projectNameForUrl = ProjectName.formatForUrl(projectName)
	return "${request.origin.scheme}://${request.host()}:${request.port()}/a/$penNameForUrl/$projectNameForUrl"
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
