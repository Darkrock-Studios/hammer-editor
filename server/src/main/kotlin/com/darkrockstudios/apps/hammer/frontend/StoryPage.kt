package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.frontend.utils.*
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.*
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
		route("/story/{projectName}") {
			get {
				val session = call.sessions.requireUser()
				val projectNameParam = call.parameters["projectName"]

				if (projectNameParam.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				val projectName = ProjectName.decodeFromUrl(projectNameParam)
				val project = projectsRepository.getProjectByName(session.userId, projectName)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@get
				}

				val projectId = ProjectId(project.uuid)
				val projectNameForUrl = ProjectName.formatForUrl(project.name)

				// Get scene hierarchy first to determine what to show
				val hierarchyResult = storyExportService.getSceneHierarchy(session.userId, projectId)
				val sceneHierarchyItems = when (hierarchyResult) {
					is SceneHierarchyResult.Success -> hierarchyResult.scenes
					else -> emptyList()
				}

				// If there are scenes, load the first one; otherwise load the full story (which will be empty)
				val firstSceneId = sceneHierarchyItems.firstOrNull()?.id
				val (storyHtml, hasContent) = if (firstSceneId != null) {
					when (val sceneResult =
						storyExportService.exportSceneAsHtml(session.userId, projectId, firstSceneId)) {
						is SingleSceneExportResult.Success -> sceneResult.html to sceneResult.hasContent
						else -> "" to false
					}
				} else {
					"" to false
				}

				// Get full story stats (for sidebar info)
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

						val lastSyncFormatted = formatSyncDate(project.lastSync)

						// Build scene hierarchy with selected state
						val sceneHierarchy = sceneHierarchyItems.map { scene ->
							mapOf(
								"id" to scene.id,
								"name" to scene.name,
								"isGroup" to scene.isGroup,
								"isScene" to scene.isScene,
								"depth" to scene.depth,
								"indent" to scene.getIndent(),
								"isSelected" to (scene.id == firstSceneId)
							)
						}

						val model = call.withDefaults(
							mapOf(
								"page_stylesheet" to "/assets/css/story.css",
								"page_script" to "/assets/js/story.js",
								"projectName" to result.projectName,
								"projectNameForUrl" to projectNameForUrl,
								"storyHtml" to storyHtml,
								"hasContent" to hasContent,
								"hasPenName" to hasPenName,
								"isPublished" to isPublished,
								"hasAnyAccess" to hasAnyAccess,
								"publicUrl" to publicUrl,
								"lastSync" to lastSyncFormatted,
								"sceneCount" to result.sceneCount,
								"totalWordCount" to result.totalWordCount,
								"formattedWordCount" to WordCountUtils.formatWordCount(result.totalWordCount),
								"accessEntries" to accessEntries,
								"hasAccessEntries" to accessEntries.isNotEmpty(),
								"sceneHierarchy" to sceneHierarchy,
								"hasScenes" to sceneHierarchy.isNotEmpty()
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

			hx.get("/scene") {
				val session = call.sessions.requireUser()
				val projectNameParam = call.parameters["projectName"]
				val sceneIdStr = call.request.queryParameters["sceneId"]

				if (projectNameParam.isNullOrBlank() || sceneIdStr.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				val projectName = ProjectName.decodeFromUrl(projectNameParam)
				val project = projectsRepository.getProjectByName(session.userId, projectName)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@get
				}

				val projectId = ProjectId(project.uuid)

				// Handle "all" as a special case for full story view
				if (sceneIdStr == "all") {
					val result = storyExportService.exportStoryAsHtml(
						userId = session.userId,
						projectId = projectId
					)

					when (result) {
						is StoryExportResult.Success -> {
							val model = call.withDefaults(
								mapOf(
									"storyHtml" to result.html,
									"hasContent" to result.hasContent
								)
							)
							call.respond(MustacheContent("partials/story-content.mustache", model))
						}

						is StoryExportResult.ProjectNotFound -> {
							call.respond(HttpStatusCode.NotFound)
						}

						is StoryExportResult.Error -> {
							call.respond(HttpStatusCode.InternalServerError, result.message)
						}
					}
					return@get
				}

				// Parse scene ID
				val sceneId = sceneIdStr.toIntOrNull()
				if (sceneId == null) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				val result = storyExportService.exportSceneAsHtml(
					userId = session.userId,
					projectId = projectId,
					sceneId = sceneId
				)

				when (result) {
					is SingleSceneExportResult.Success -> {
						val model = call.withDefaults(
							mapOf(
								"storyHtml" to result.html,
								"hasContent" to result.hasContent
							)
						)
						call.respond(MustacheContent("partials/story-content.mustache", model))
					}

					is SingleSceneExportResult.ProjectNotFound -> {
						call.respond(HttpStatusCode.NotFound)
					}

					is SingleSceneExportResult.SceneNotFound -> {
						call.respond(HttpStatusCode.NotFound, "Scene not found")
					}

					is SingleSceneExportResult.Error -> {
						call.respond(HttpStatusCode.InternalServerError, result.message)
					}
				}
			}

			hx.post("/publish") {
				val session = call.sessions.requireUser()
				val projectNameParam = call.parameters["projectName"]

				if (projectNameParam.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@post
				}

				val projectName = ProjectName.decodeFromUrl(projectNameParam)
				val project = projectsRepository.getProjectByName(session.userId, projectName)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@post
				}

				val projectId = ProjectId(project.uuid)
				val projectNameForUrl = ProjectName.formatForUrl(project.name)
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
					if (account.pen_name != null) {
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
						"projectNameForUrl" to projectNameForUrl,
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
				val projectNameParam = call.parameters["projectName"]

				if (projectNameParam.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				// Get tomorrow's date as minimum date for the date picker
				val tomorrow = java.time.LocalDate.now().plusDays(1)
				val minDate = tomorrow.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

				val model = call.withDefaults(
					mapOf(
						"projectNameForUrl" to projectNameParam,
						"minDate" to minDate
					)
				)

				call.respond(MustacheContent("partials/share-dialog.mustache", model))
			}

			hx.get("/publish-warning") {
				val projectNameParam = call.parameters["projectName"]

				if (projectNameParam.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				val model = call.withDefaults(
					mapOf(
						"projectNameForUrl" to projectNameParam
					)
				)

				call.respond(MustacheContent("partials/publish-warning-dialog.mustache", model))
			}

			hx.post("/access") {
				val session = call.sessions.requireUser()
				val projectNameParam = call.parameters["projectName"]

				if (projectNameParam.isNullOrBlank()) {
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

				val projectName = ProjectName.decodeFromUrl(projectNameParam)
				val project = projectsRepository.getProjectByName(session.userId, projectName)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@post
				}

				val projectId = ProjectId(project.uuid)
				val projectNameForUrl = ProjectName.formatForUrl(project.name)

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
					if (account.pen_name != null) {
						call.constructPublicUrl(account.pen_name, project.name)
					} else ""
				} else ""

				val model = call.withDefaults(
					mapOf(
						"projectNameForUrl" to projectNameForUrl,
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
				val projectNameParam = call.parameters["projectName"]
				val accessIdStr = call.parameters["accessId"]

				if (projectNameParam.isNullOrBlank() || accessIdStr.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@delete
				}

				val projectName = ProjectName.decodeFromUrl(projectNameParam)
				val project = projectsRepository.getProjectByName(session.userId, projectName)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@delete
				}

				val projectId = ProjectId(project.uuid)
				val projectNameForUrl = ProjectName.formatForUrl(project.name)
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
					if (account.pen_name != null) {
						call.constructPublicUrl(account.pen_name, project.name)
					} else ""
				} else ""

				val model = call.withDefaults(
					mapOf(
						"projectNameForUrl" to projectNameForUrl,
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

fun ApplicationCall.constructPublicUrl(penName: String, projectName: String): String {
	return buildPublicUrl(
		scheme = request.origin.scheme,
		host = request.host(),
		port = request.port(),
		penName = penName,
		projectName = projectName
	)
}

fun buildPublicUrl(
	scheme: String,
	host: String,
	port: Int,
	penName: String,
	projectName: String
): String {
	val penNameForUrl = ProjectName.formatForUrl(penName)
	val projectNameForUrl = ProjectName.formatForUrl(projectName)
	return if (port == 80 || port == 443) {
		"$scheme://$host/a/$penNameForUrl/$projectNameForUrl"
	} else {
		"$scheme://$host:$port/a/$penNameForUrl/$projectNameForUrl"
	}
}
