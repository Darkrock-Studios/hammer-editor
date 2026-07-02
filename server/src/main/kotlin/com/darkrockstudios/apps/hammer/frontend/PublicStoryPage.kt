package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.resolveByPenName
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderCollector
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
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
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
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

			// Resolve the author by pen name (verbatim, then dashes as spaces).
			val account = resolveByPenName(penNameParam) { accountsRepository.findAccountByPenName(it) }
			val penName = account?.pen_name
			if (account == null || penName == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			// Resolve the project by the id embedded in its URL segment, scoped to this author's
			// projects; the slug beside the id is decorative and ignored.
			val projectId = ProjectName.idFromSegment(projectNameParam)
			val projectName = projectsRepository.getProjectsWithSyncDate(account.id)
				.find { ProjectName.shortId(it.uuid) == projectId }?.name
			if (projectName == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val result = projectAccessRepository.findAccessibleProject(penName, projectName, password)

			// Self-referential links reuse the incoming, already URL-safe segments.
			val penNameForUrl = penNameParam
			val projectNameForUrl = projectNameParam

			when (val resolved = result) {
				is PublicProjectResult.NotFound -> {
					call.respond(HttpStatusCode.NotFound)
				}

				is PublicProjectResult.PasswordRequired -> {
					// Password-protected stories are private shares — never index them.
					call.applyRobotsTag(indexable = false)

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
					// Only publicly-published stories (no password) by authors who participate in
					// the community feature are indexable. A crawler never supplies a password, so a
					// Success it reaches is necessarily public access; the password check keeps
					// private shares (reached with a valid password) out of the index too.
					val indexable = password.isNullOrBlank() && account.community_member
					call.applyRobotsTag(indexable = indexable)

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
									"authorPenNameUrl" to ProjectName.penNameForUrl(resolved.penName),
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

			// Redirect back to GET, reusing the (already URL-safe) incoming segments.
			if (!password.isNullOrBlank()) {
				call.respondRedirect(
					"/a/$penNameParam/$projectNameParam?p=${
						URLEncoder.encode(
							password,
							StandardCharsets.UTF_8
						)
					}"
				)
			} else {
				call.respondRedirect("/a/$penNameParam/$projectNameParam")
			}
		}
	}
}
