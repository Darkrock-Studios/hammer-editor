package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.applyRevalidationHeaders
import com.darkrockstudios.apps.hammer.frontend.utils.canonicalUrl
import com.darkrockstudios.apps.hammer.frontend.utils.findProjectByUrlSegment
import com.darkrockstudios.apps.hammer.frontend.utils.matchesETag
import com.darkrockstudios.apps.hammer.frontend.utils.metaDescription
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.pageETag
import com.darkrockstudios.apps.hammer.frontend.utils.resolveByPenName
import com.darkrockstudios.apps.hammer.frontend.utils.storyArticleJsonLd
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderCollector
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.PaginatedExportResult
import com.darkrockstudios.apps.hammer.story.StoryRendererService
import com.darkrockstudios.apps.hammer.story.WordCountUtils
import io.ktor.http.HttpStatusCode
import io.ktor.server.htmx.hx
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun Route.publicStoryPage(
	storyRendererService: StoryRendererService,
	projectAccessRepository: ProjectAccessRepository,
	projectDao: ProjectDao,
	storyReaderCollector: StoryReaderCollector,
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
	serverProjectDataRepository: ServerProjectDataRepository,
	serverConfig: ServerConfig,
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
			val projectName = projectsRepository.findProjectByUrlSegment(account.id, projectNameParam)?.name
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
					// Only publicly-published stories by authors who participate in the community
					// feature are indexable. `isPublic` comes from the access layer, which knows
					// whether it granted access with no password or unlocked a private share.
					val indexable = resolved.isPublic && account.community_member
					call.applyRobotsTag(indexable = indexable)

					val passwordParam = if (!password.isNullOrBlank()) {
						"&p=${URLEncoder.encode(password, StandardCharsets.UTF_8)}"
					} else {
						""
					}

					val projectDef = ProjectDefinition(name = projectName, uuid = resolved.projectUuid)

					// The page shell — everything not derived from the story render. Built first so
					// it can be hashed into the validator, then filled in with the render below.
					val model = call.withDefaults(
						mapOf(
							"page_stylesheet" to "/assets/css/story.css",
							"title" to "$projectName · ${resolved.penName} — Hammer",
							// Self-referential canonical: each page is its own indexable URL. The
							// password (?p) is never included — it's a secret and those pages are noindex.
							"canonicalUrl" to (call.canonicalUrl() + if (page > 1) "?page=$page" else ""),
							"ogType" to "article",
							// Dynamic card only for public (password-free) access, matching the OG
							// route; a private share falls back to the static card, never a 404.
							"ogImage" to if (serverConfig.richLinkPreviews && resolved.isPublic) {
								call.canonicalUrl("/og/s/${resolved.projectUuid.id}.png")
							} else {
								call.canonicalUrl("/assets/images/og-story.png")
							},
							"page_pre_script" to "/assets/js/story-reader-logic.js",
							"page_script" to "/assets/js/story-reader.js",
							"projectName" to projectName,
							"authorPenName" to resolved.penName,
							"authorPenNameUrl" to ProjectName.penNameForUrl(resolved.penName),
						)
					)

					// Resolved once and reused by the render below, so the ETag costs no extra queries.
					val prepared =
						storyRendererService.prepareExport(resolved.userId, resolved.projectUuid)

					// Stands in for everything project-data carries (notably the declared story
					// language rendered below): a hash-only lookup, so the revalidation path never
					// transfers or parses the data blob.
					val projectDataHash = serverProjectDataRepository.loadDataHash(resolved.userId, projectDef)

					// A reader who already holds this exact page is answered without rendering it.
					// `indexable` is an explicit input: it gates the JSON-LD block but never reaches
					// the shell model.
					val etag = prepared?.let {
						pageETag(model, it.version, page, passwordParam, indexable, projectDataHash)
					}
					if (etag != null && call.matchesETag(etag)) {
						call.applyRevalidationHeaders(etag)
						call.respond(HttpStatusCode.NotModified)
						return@get
					}

					// The unique-reader count is no longer recorded here on page load — that
					// counts everyone who clicks, including bounces. Instead the page loads a
					// tiny script that fires a beacon to POST .../read once the visitor has
					// actually dwelt on the page for a few seconds (see story-reader.js). That
					// beacon is what best-effort filters out drive-by clicks.

					val exportResult = if (prepared == null) {
						PaginatedExportResult.ProjectNotFound
					} else {
						storyRendererService.renderStoryAsHtmlPaginated(
							prepared = prepared,
							page = page,
							// Only a publicly-reachable story may be written to disk; a private
							// share's prose is encrypted at rest and must stay that way.
							cacheable = resolved.isPublic,
						)
					}

					when (exportResult) {
						is PaginatedExportResult.Success -> {
							val data = exportResult.data

							// The story's declared language, when set, becomes the page's content
							// language: <html lang> via the `locale` model key and the JSON-LD
							// `inLanguage` below. Overridden only now, after withDefaults, so the
							// header/footer extra links stay localized to the viewer's UI locale;
							// the ETag covers it through projectDataHash above.
							val storyLanguage = serverProjectDataRepository.loadProjectLanguage(
								userId = resolved.userId,
								projectDef = projectDef,
							)
							if (storyLanguage != null) {
								model["locale"] = storyLanguage
							}

							model.putAll(
								mapOf(
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
							metaDescription(
								call.msg("public_story_meta_description", data.projectName, resolved.penName),
							)?.let { model["metaDescription"] = it }
							if (indexable) {
								model["jsonLd"] = storyArticleJsonLd(
									title = data.projectName,
									url = call.canonicalUrl("/a/$penNameForUrl/$projectNameForUrl"),
									authorName = resolved.penName,
									authorUrl = call.canonicalUrl("/a/${ProjectName.penNameForUrl(resolved.penName)}"),
									wordCount = data.totalWordCount.toLong(),
									inLanguage = storyLanguage,
								)
							}
							etag?.let { call.applyRevalidationHeaders(it) }
							call.respond(MustacheContent("publicstory.mustache", model))
						}

						is PaginatedExportResult.ProjectNotFound -> {
							call.respond(HttpStatusCode.NotFound)
						}

						is PaginatedExportResult.Error -> {
							model["errorMessage"] = exportResult.message
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

		// Best-effort "reader" beacon. story-reader.js fires this only after the visitor
		// has actually dwelt on the page for a few seconds, so drive-by clicks (bounces)
		// never reach here. Resolution mirrors the GET exactly — including the author-skip
		// and access checks — so a beacon can only record a read the visitor could load.
		post("/read") {
			val penNameParam = call.parameters["penName"]
			val projectNameParam = call.parameters["projectName"]

			// A beacon is fire-and-forget: always answer 204 and never leak resolution
			// outcomes, so a bad beacon looks the same as a good one.
			if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) {
				call.respond(HttpStatusCode.NoContent)
				return@post
			}

			val password = call.request.queryParameters["p"]

			val account = resolveByPenName(penNameParam) { accountsRepository.findAccountByPenName(it) }
			val penName = account?.pen_name
			if (account == null || penName == null) {
				call.respond(HttpStatusCode.NoContent)
				return@post
			}

			val projectName = projectsRepository.findProjectByUrlSegment(account.id, projectNameParam)?.name
			if (projectName == null) {
				call.respond(HttpStatusCode.NoContent)
				return@post
			}

			val resolved = projectAccessRepository.findAccessibleProject(penName, projectName, password)
			if (resolved is PublicProjectResult.Success) {
				// Skip the author reading their own story.
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
			}

			call.respond(HttpStatusCode.NoContent)
		}
	}
}
