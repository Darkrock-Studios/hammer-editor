package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ReaderDay
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.Toast
import com.darkrockstudios.apps.hammer.frontend.utils.authenticatedOnly
import com.darkrockstudios.apps.hammer.frontend.utils.findProjectByUrlSegment
import com.darkrockstudios.apps.hammer.frontend.utils.formatInstant
import com.darkrockstudios.apps.hammer.frontend.utils.formatSyncDate
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.renderTemplate
import com.darkrockstudios.apps.hammer.frontend.utils.requireUser
import com.darkrockstudios.apps.hammer.frontend.utils.respondHtmlWithToast
import com.darkrockstudios.apps.hammer.frontend.utils.respondTemplateWithToast
import com.darkrockstudios.apps.hammer.frontend.utils.sceneTreeModel
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderRepository
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.SceneHierarchyResult
import com.darkrockstudios.apps.hammer.story.SingleSceneExportResult
import com.darkrockstudios.apps.hammer.story.StoryRenderResult
import com.darkrockstudios.apps.hammer.story.StoryRendererService
import com.darkrockstudios.apps.hammer.story.WordCountUtils
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.truncateToUtcDay
import com.github.aymanizz.ktori18n.R
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.htmx.hx
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.sessions
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

fun Route.storyPage(
	storyRendererService: StoryRendererService,
	projectAccessRepository: ProjectAccessRepository,
	projectsRepository: ProjectsRepository,
	accountsRepository: AccountsRepository,
	reviewRepository: com.darkrockstudios.apps.hammer.review.ReviewRepository,
	storyReaderRepository: StoryReaderRepository,
	serverProjectDataRepository: ServerProjectDataRepository,
	projectDao: ProjectDao,
	clock: kotlin.time.Clock,
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

				val project = projectsRepository.findProjectByUrlSegment(session.userId, projectNameParam)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@get
				}

				val projectId = ProjectId(project.uuid)
				val projectNameForUrl = ProjectName.projectSegment(project.name, project.uuid)

				// Get scene hierarchy first to determine what to show
				val hierarchyResult =
					storyRendererService.getSceneHierarchy(session.userId, projectId)
				val sceneHierarchyItems = when (hierarchyResult) {
					is SceneHierarchyResult.Success -> hierarchyResult.scenes
					else -> emptyList()
				}

				// If there are scenes, load the first one; otherwise load the full story (which will be empty)
				val firstSceneId = sceneHierarchyItems.firstOrNull()?.id
				val (storyHtml, hasContent) = if (firstSceneId != null) {
					when (val sceneResult =
						storyRendererService.renderSceneAsHtml(
							session.userId,
							projectId,
							firstSceneId
						)) {
						is SingleSceneExportResult.Success -> sceneResult.html to sceneResult.hasContent
						else -> "" to false
					}
				} else {
					"" to false
				}

				// Get full story stats (for sidebar info)
				val result = storyRendererService.renderStoryAsHtml(
					userId = session.userId,
					projectId = projectId
				)

				when (result) {
					is StoryRenderResult.Success -> {
						val account = accountsRepository.getAccount(session.userId)
						val hasPenName = !account.pen_name.isNullOrBlank()

						val isPublished = projectAccessRepository.isPublished(session.userId, projectId)
						val hasAnyAccess = projectAccessRepository.hasAnyAccess(session.userId, projectId)
						val accessEntries = projectAccessRepository.getPrivateAccessEntries(session.userId, projectId)

						val numericProjectId = projectDao.getProjectIdOrNull(session.userId, projectId)
						val totalReaders = numericProjectId
							?.let { storyReaderRepository.totalReadersForProject(it) } ?: 0L
						val readerBars = numericProjectId
							?.let { storyReaderRepository.dailyReadersForProject(it, clock.now() - 30.days) }
							?.let { buildReaderBars(it, clock.now()) }
							?: emptyList()
						val publicUrl = if (hasAnyAccess && hasPenName) {
							call.constructPublicUrl(account.pen_name, result.projectName, project.uuid)
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

						val reviewModels = call.reviewCards(reviewRepository, session.userId, projectId)

						// The public reader page declares the story's language on the prose; the
						// author's own view has to as well, or the browser hyphenates a French
						// story by the rules of whatever locale the author reads Hammer in.
						val storyLanguage = serverProjectDataRepository.loadProjectLanguage(
							userId = session.userId,
							projectDef = ProjectDefinition(name = project.name, uuid = projectId),
						)

						val model = call.withDefaults(
							mapOf(
								"page_stylesheet" to "/assets/css/story.css",
								"storyLanguage" to storyLanguage.orEmpty(),
								"page_script" to "/assets/js/story.js",
								"page_pre_script" to "/assets/js/review-form-logic.js",
								"reviews" to reviewModels,
								"hasReviews" to reviewModels.isNotEmpty(),
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
								"hasReaderStats" to (numericProjectId != null),
								"totalReaders" to "%,d".format(totalReaders),
								"readerBars" to readerBars,
								"hasReaderBars" to readerBars.any { (it["height"] as Int) > 0 },
								"sceneHierarchy" to sceneHierarchy,
								"hasScenes" to sceneHierarchy.isNotEmpty()
							)
						)
						call.respond(MustacheContent("story.mustache", model))
					}

					is StoryRenderResult.ProjectNotFound -> {
						call.respond(HttpStatusCode.NotFound)
					}

					is StoryRenderResult.Error -> {
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

				val project = projectsRepository.findProjectByUrlSegment(session.userId, projectNameParam)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@get
				}

				val projectId = ProjectId(project.uuid)

				// Handle "all" as a special case for full story view
				if (sceneIdStr == "all") {
					val result = storyRendererService.renderStoryAsHtml(
						userId = session.userId,
						projectId = projectId
					)

					when (result) {
						is StoryRenderResult.Success -> {
							val model = call.withDefaults(
								mapOf(
									"storyHtml" to result.html,
									"hasContent" to result.hasContent
								)
							)
							call.respond(MustacheContent("partials/story-content.mustache", model))
						}

						is StoryRenderResult.ProjectNotFound -> {
							call.respond(HttpStatusCode.NotFound)
						}

						is StoryRenderResult.Error -> {
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

				val result = storyRendererService.renderSceneAsHtml(
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

				val project = projectsRepository.findProjectByUrlSegment(session.userId, projectNameParam)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@post
				}

				val projectId = ProjectId(project.uuid)
				val projectNameForUrl = ProjectName.projectSegment(project.name, project.uuid)
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
						call.constructPublicUrl(account.pen_name, project.name, project.uuid)
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
				val session = call.sessions.requireUser()
				val projectNameParam = call.parameters["projectName"]

				if (projectNameParam.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				val project = projectsRepository.findProjectByUrlSegment(session.userId, projectNameParam)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@get
				}

				val hierarchyResult =
					storyRendererService.getSceneHierarchy(session.userId, ProjectId(project.uuid))
				// A failed tree load must not render as an innocent empty tree: with the limit
				// toggle on, an empty tree pins the submit button disabled with no explanation.
				val scenes = when (hierarchyResult) {
					is SceneHierarchyResult.Success -> hierarchyResult.scenes
					else -> {
						respondHtmlWithToast(
							content = "",
							message = call.msg("story_share_dialog_scenes_failed"),
							toast = Toast.Error,
						)
						return@get
					}
				}

				// Get tomorrow's date as minimum date for the date picker
				val tomorrow = java.time.LocalDate.now().plusDays(1)
				val minDate = tomorrow.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

				val model = call.withDefaults(
					mapOf(
						"projectNameForUrl" to ProjectName.projectSegment(project.name, project.uuid),
						"minDate" to minDate,
						"sceneTree" to sceneTreeModel(scenes),
						"sceneCount" to scenes.count { it.isScene },
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
				// Null means the entire story; a checked limit box always produces a set, and
				// the repository rejects an empty one rather than widening it.
				val sceneIds: Set<Int>? = if (formParams["limitScenes"] != null) {
					formParams.getAll("sceneIds")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
				} else {
					null
				}

				if (password.isNullOrBlank()) {
					call.respond(HttpStatusCode.BadRequest)
					return@post
				}

				val project = projectsRepository.findProjectByUrlSegment(session.userId, projectNameParam)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@post
				}

				val projectId = ProjectId(project.uuid)
				val projectNameForUrl = ProjectName.projectSegment(project.name, project.uuid)

				// Form expiresAt is a date (YYYY-MM-DD); treat the boundary as end-of-day UTC.
				val expiresAtInstant = expiresAt?.let {
					kotlin.time.Instant.parse("${it}T23:59:59Z")
				}

				val createResult = projectAccessRepository.createPrivateAccess(
					userId = session.userId,
					projectUuid = projectId,
					password = password,
					expiresAt = expiresAtInstant,
					sceneIds = sceneIds,
				)

				// Return updated publish section
				val isPublished = projectAccessRepository.isPublished(session.userId, projectId)
				val hasAnyAccess = projectAccessRepository.hasAnyAccess(session.userId, projectId)
				val accessEntries = projectAccessRepository.getPrivateAccessEntries(session.userId, projectId)

				val publicUrl = if (hasAnyAccess) {
					val account = accountsRepository.getAccount(session.userId)
					if (account.pen_name != null) {
						call.constructPublicUrl(account.pen_name, project.name, project.uuid)
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

				// Single respond after the when: responding from inside an exhaustive
				// when's branches trips Ktor's pipeline-subject ClassCastException.
				// The dialog is cleared via OOB swap only on success; a failure leaves
				// it open with the author's scene selection intact.
				val publishHtml = renderTemplate("partials/story-publish.mustache", model)
				val closeDialog = """<div id="share-dialog-container" hx-swap-oob="innerHTML"></div>"""
				val (content, toastMessage, toastType) = when (createResult) {
					is ServerResult.Failure -> Triple(
						publishHtml,
						createResult.displayMessageText(call, R("api_error_unknown")),
						Toast.Error,
					)

					is ServerResult.Success -> Triple(
						publishHtml + closeDialog,
						call.msg("story_toast_access_created"),
						Toast.Success,
					)
				}
				respondHtmlWithToast(
					content = content,
					message = toastMessage,
					toast = toastType
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

				val project = projectsRepository.findProjectByUrlSegment(session.userId, projectNameParam)
				if (project == null) {
					call.respond(HttpStatusCode.NotFound)
					return@delete
				}

				val projectId = ProjectId(project.uuid)
				val projectNameForUrl = ProjectName.projectSegment(project.name, project.uuid)
				val accessId = accessIdStr.toLongOrNull()

				if (accessId == null) {
					call.respond(HttpStatusCode.BadRequest)
					return@delete
				}

				val deleted = projectAccessRepository.deleteAccessById(session.userId, projectId, accessId)
				if (!deleted) {
					call.respond(HttpStatusCode.NotFound)
					return@delete
				}

				// Return updated publish section
				val isPublished = projectAccessRepository.isPublished(session.userId, projectId)
				val hasAnyAccess = projectAccessRepository.hasAnyAccess(session.userId, projectId)
				val accessEntries = projectAccessRepository.getPrivateAccessEntries(session.userId, projectId)

				val publicUrl = if (hasAnyAccess) {
					val account = accountsRepository.getAccount(session.userId)
					if (account.pen_name != null) {
						call.constructPublicUrl(account.pen_name, project.name, project.uuid)
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

/**
 * Builds a fixed 30-slot (oldest→newest) per-day bar model for the reader trend
 * graph: heights are scaled 0–100 against the busiest day, with zero-filled gaps so
 * quiet days still render a baseline.
 */
private fun buildReaderBars(daily: List<ReaderDay>, now: Instant): List<Map<String, Any>> {
	val byDay = daily.groupBy { it.day.truncateToUtcDay() }.mapValues { (_, rows) -> rows.sumOf { it.count } }
	val today = now.truncateToUtcDay()
	val days = (29 downTo 0).map { today - it.days }
	val counts = days.map { byDay[it] ?: 0L }
	val max = counts.maxOrNull() ?: 0L

	return days.zip(counts).map { (day, count) ->
		mapOf(
			"height" to if (max > 0) ((count * 100) / max).toInt() else 0,
			// Buckets are UTC-day floored, so label them in UTC too.
			"label" to "${formatInstant(day, "MMM dd", ZoneOffset.UTC)}: $count",
		)
	}
}

fun ApplicationCall.constructPublicUrl(penName: String, projectName: String, projectUuid: String): String {
	return buildPublicUrl(
		scheme = request.origin.scheme,
		host = request.host(),
		port = request.port(),
		penName = penName,
		projectName = projectName,
		projectUuid = projectUuid
	)
}

fun buildPublicUrl(
	scheme: String,
	host: String,
	port: Int,
	penName: String,
	projectName: String,
	projectUuid: String
): String {
	val penNameForUrl = ProjectName.penNameForUrl(penName)
	val projectNameForUrl = ProjectName.projectSegment(projectName, projectUuid)
	return if (port == 80 || port == 443) {
		"$scheme://$host/a/$penNameForUrl/$projectNameForUrl"
	} else {
		"$scheme://$host:$port/a/$penNameForUrl/$projectNameForUrl"
	}
}
