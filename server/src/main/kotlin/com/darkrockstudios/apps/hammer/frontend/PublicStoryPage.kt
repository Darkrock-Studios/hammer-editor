package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.applyRevalidationHeaders
import com.darkrockstudios.apps.hammer.frontend.utils.canonicalUrl
import com.darkrockstudios.apps.hammer.frontend.utils.getLocale
import com.darkrockstudios.apps.hammer.frontend.utils.lookUpStory
import com.darkrockstudios.apps.hammer.frontend.utils.matchesETag
import com.darkrockstudios.apps.hammer.frontend.utils.metaDescription
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.pageETag
import com.darkrockstudios.apps.hammer.frontend.utils.rememberUnlockedShare
import com.darkrockstudios.apps.hammer.frontend.utils.storyArticleJsonLd
import com.darkrockstudios.apps.hammer.frontend.utils.unlockedShares
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderCollector
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.StoryImageLinks
import com.darkrockstudios.apps.hammer.story.StoryImageService
import com.darkrockstudios.apps.hammer.story.StoryImageSigner
import com.darkrockstudios.apps.hammer.story.StoryRendererService
import com.darkrockstudios.apps.hammer.story.StoryResult
import io.ktor.htmx.HxResponseHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.htmx.hx
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.userAgent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlin.time.Clock

fun Route.publicStoryPage(
	storyRendererService: StoryRendererService,
	projectAccessRepository: ProjectAccessRepository,
	storyReaderCollector: StoryReaderCollector,
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
	serverProjectDataRepository: ServerProjectDataRepository,
	storyImageService: StoryImageService,
	storyImageSigner: StoryImageSigner,
	serverConfig: ServerConfig,
	clock: Clock,
) {
	route("/a/{penName}/{projectName}") {
		get {
			// A private share opens with the shares this browser has unlocked. An old link may still
			// carry the share password (?p=): it unlocks once, then the reader goes to the clean URL.
			val password = call.request.queryParameters["p"]
			val pageParam = call.request.queryParameters["page"]
			val page = pageParam?.toIntOrNull() ?: 1

			val lookup = call.lookUpStory(
				accountsRepository,
				projectsRepository,
				projectAccessRepository,
				password = password,
				unlockedShares = call.unlockedShares(clock.now()),
			)
			if (lookup == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}
			val account = lookup.account
			val projectName = lookup.projectName
			val result = lookup.result

			val penNameForUrl = lookup.penNameSegment
			val projectNameForUrl = lookup.projectSegment

			when (val resolved = result) {
				is PublicProjectResult.NotFound -> {
					call.respond(HttpStatusCode.NotFound)
				}

				is PublicProjectResult.PasswordRequired -> {
					// Show an error if a password was provided but invalid
					call.respondPasswordForm(
						penNameForUrl,
						projectNameForUrl,
						error = password != null,
						page = requestedPage(pageParam),
					)
				}

				is PublicProjectResult.Success -> {
					// A link carrying the password has done its job: remember the share in this
					// browser and move the reader to the clean URL, so the password leaves the
					// address bar, history and anything copied from it.
					if (password != null) {
						resolved.accessId?.let { accessId ->
							call.rememberUnlockedShare(accessId, password, resolved.accessExpiresAt, clock.now())
						}
						call.respondRedirect(storyLocation(penNameForUrl, projectNameForUrl, requestedPage(pageParam)))
						return@get
					}

					// Only publicly-published stories by authors who participate in the community
					// feature are indexable. `isPublic` comes from the access layer, which knows
					// whether it granted access with no password or unlocked a private share.
					val indexable = resolved.isPublic && account.community_member
					call.applyRobotsTag(indexable = indexable)

					val projectDef = ProjectDefinition(name = projectName, uuid = resolved.projectUuid)
					val storyUrl = "/a/$penNameForUrl/$projectNameForUrl"

					suspend fun signedImageUrl(imageId: Int): String {
						val signature = storyImageSigner.sign(resolved.projectUuid, imageId)
						return "$storyUrl/image/$imageId?s=$signature"
					}

					// A story with a cover opens on it, as its own first page; the prose starts at ?page=1.
					val coverImageUrl = storyCoverImageId(
						serverProjectDataRepository,
						storyImageService,
						resolved.owner,
						projectDef,
					)?.let { signedImageUrl(it) }
					val isCoverPage = pageParam == null && coverImageUrl != null
					val coverPageUrl = storyUrl

					// The page shell — everything not derived from the story render. Built first so
					// it can be hashed into the validator, then filled in with the render below.
					val model = call.withDefaults(
						mapOf(
							"page_stylesheet" to "/assets/css/story.css",
							"title" to "$projectName · ${resolved.penName} — Hammer",
							// Self-referential canonical: each page is its own indexable URL. The
							// password (?p) is never included — it's a secret and those pages are noindex.
							"canonicalUrl" to (call.canonicalUrl() + when {
								isCoverPage -> ""
								page > 1 || coverImageUrl != null -> "?page=$page"
								else -> ""
							}),
							"ogType" to "article",
							// Dynamic card only for public (password-free) access, matching the OG
							// route; a private share falls back to the static card, never a 404.
							"ogImage" to if (serverConfig.richLinkPreviews && resolved.isPublic) {
								call.canonicalUrl("/og/s/${resolved.projectUuid.id}.png")
							} else {
								call.canonicalUrl("/assets/images/og-story.png")
							},
							"page_script" to "/assets/js/story-reader.js",
							"projectName" to projectName,
							"authorPenName" to resolved.penName,
							"authorPenNameUrl" to ProjectName.penNameForUrl(resolved.penName),
						)
					)
					if (resolved.isPublic) {
						model["kudosFragmentUrl"] = "$storyUrl/kudos"
					}

					// Prose pages set up their reading controls from the head, before the prose paints.
					// The cover has none, so it loads only the beacon's helpers, at the end of the body.
					if (isCoverPage) {
						model["page_pre_scripts"] = listOf("/assets/js/story-reader-logic.js", "/assets/js/story-read-logic.js")
					} else {
						model["page_head_scripts"] = READER_HEAD_SCRIPTS
						model["page_pre_script"] = "/assets/js/story-reader-logic.js"
					}

					// In the shell, so the validator changes when the cover is set or cleared, and when
					// its image reaches the server or leaves it: every page links back to the cover.
					if (coverImageUrl != null) {
						model["hasCover"] = true
						if (isCoverPage) {
							model["isCoverPage"] = true
							model["coverImageUrl"] = coverImageUrl
							model["startReadingUrl"] = "$storyUrl?page=1"
						}
					}

					// Resolved once and reused by the render below.
					// A scene-limited share prepares only its scene subset; if every selected scene
					// is gone this returns null and the link 404s like a nonexistent share.
					val prepared = storyRendererService.prepareExport(
						resolved.owner,
						resolved.projectUuid,
						resolved.sceneIds,
					)

					// Stands in for the rest of project data (notably the declared story language
					// rendered below), so it need not be read before deciding on a 304.
					val projectDataHash = serverProjectDataRepository.loadDataHash(resolved.owner, projectDef)

					// A reader who already holds this exact page is answered without rendering it.
					// `indexable` is an explicit input: it gates the JSON-LD block but never reaches
					// the shell model. The signing key generation is too, so rotating the key
					// reissues the page's signed image links before the old generation is retired.
					// So is the share, as two shares of one story can show different scenes.
					val etag = prepared?.let {
						pageETag(
							model,
							it.version,
							page,
							resolved.accessId,
							indexable,
							projectDataHash,
							storyImageSigner.keyGeneration(),
						)
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
						StoryResult.ProjectNotFound
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
						is StoryResult.Success -> {
							// The stats are the viewer's UI, so they take the viewer's locale, not the story's.
							val viewerLocale = call.getLocale()
							val data = exportResult.value

							// The story's declared language, when set, becomes the page's content
							// language: <html lang> via the `locale` model key and the JSON-LD
							// `inLanguage` below. Overridden only now, after withDefaults, so the
							// header/footer extra links stay localized to the viewer's UI locale;
							// the ETag covers it through projectDataHash above.
							val storyLanguage = serverProjectDataRepository.loadProjectLanguage(
								owner = resolved.owner,
								projectDef = projectDef,
							)
							if (storyLanguage != null) {
								model["locale"] = storyLanguage
								model["storyLanguage"] = storyLanguage
							}

							model.putAll(
								mapOf(
									"storyHtml" to StoryImageLinks.resolve(data.pageHtml, ::signedImageUrl),
									"hasContent" to data.hasContent,
									"sceneCount" to data.sceneCount,
									"totalWordCount" to data.totalWordCount,
									"formattedWordCount" to formatCount(viewerLocale, data.totalWordCount),
									"readingTime" to readingTime(viewerLocale, data.estimatedReadingTimeMinutes),
									"currentPage" to data.currentPage,
									"totalPages" to data.totalPages,
									// The page nav also carries the way back to a cover; the count needs real pages.
									"hasPagination" to (data.totalPages > 1 || coverImageUrl != null),
									"hasPageCount" to (data.totalPages > 1),
									"hasNextPage" to data.hasNextPage,
									"hasPrevPage" to (data.hasPrevPage || coverImageUrl != null),
									"prevIsCover" to (!data.hasPrevPage && coverImageUrl != null),
									"nextPageUrl" to "$storyUrl?page=${data.nextPage}",
									"prevPageUrl" to if (data.hasPrevPage) {
										"$storyUrl?page=${data.prevPage}"
									} else {
										coverPageUrl
									},
								)
							)
							metaDescription(
								call.msg("public_story_meta_description", data.projectName, resolved.penName),
							)?.let { model["metaDescription"] = it }
							if (indexable) {
								model["jsonLd"] = storyArticleJsonLd(
									title = data.projectName,
									url = call.canonicalUrl(storyUrl),
									authorName = resolved.penName,
									authorUrl = call.canonicalUrl("/a/${ProjectName.penNameForUrl(resolved.penName)}"),
									wordCount = data.totalWordCount.toLong(),
									inLanguage = storyLanguage,
								)
							}
							etag?.let { call.applyRevalidationHeaders(it) }
							call.respond(MustacheContent("publicstory.mustache", model))
						}

						is StoryResult.ProjectNotFound -> {
							call.respond(HttpStatusCode.NotFound)
						}

						is StoryResult.Error -> {
							call.respond(
								HttpStatusCode.InternalServerError,
								call.storyErrorContent(exportResult.message, model),
							)
						}
					}
				}
			}
		}

		// Only an image the server itself linked from a page this reader could open is served: the
		// signature stands in for checking the prose for a reference, which would mean decrypting it.
		get("/image/{imageId}") {
			val imageId = call.parameters["imageId"]?.toIntOrNull()
			val signature = call.request.queryParameters["s"]
			if (imageId == null || signature.isNullOrBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val resolved = call.lookUpStory(
				accountsRepository,
				projectsRepository,
				projectAccessRepository,
				password = null,
				unlockedShares = call.unlockedShares(clock.now()),
			)?.result as? PublicProjectResult.Success
			if (resolved == null || !storyImageSigner.verify(resolved.projectUuid, imageId, signature)) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			call.respondStoryImage(
				storyImageService = storyImageService,
				owner = resolved.owner,
				projectDef = ProjectDefinition(name = resolved.projectName, uuid = resolved.projectUuid),
				imageId = imageId,
			)
		}

		// The password form. A right password unlocks the share in this browser and sends the reader
		// to the clean story URL as a full page load (HX-Redirect), at the page they asked for; the
		// password goes in no URL. A wrong one shows the form again with the error.
		hx.post {
			val formParams = call.receiveParameters()
			val password = formParams["password"]?.takeIf { it.isNotBlank() }
			val page = requestedPage(formParams["page"])

			val lookup = call.lookUpStory(accountsRepository, projectsRepository, projectAccessRepository, password)
			when (val resolved = lookup?.result) {
				null, PublicProjectResult.NotFound -> call.respond(HttpStatusCode.NotFound)

				PublicProjectResult.PasswordRequired -> call.respondPasswordForm(
					lookup.penNameSegment,
					lookup.projectSegment,
					error = password != null,
					page = page,
				)

				is PublicProjectResult.Success -> {
					if (password != null) {
						resolved.accessId?.let { accessId ->
							call.rememberUnlockedShare(accessId, password, resolved.accessExpiresAt, clock.now())
						}
					}
					call.response.header(
						HxResponseHeaders.Redirect,
						storyLocation(lookup.penNameSegment, lookup.projectSegment, page),
					)
					call.respond(HttpStatusCode.NoContent)
				}
			}
		}

		// Best-effort "reader" beacon. story-reader.js fires this only after the visitor
		// has actually dwelt on the page for a few seconds, so drive-by clicks (bounces)
		// never reach here. Resolution mirrors the GET exactly — including the author-skip
		// and access checks — so a beacon can only record a read the visitor could load.
		post("/read") {
			// A beacon is fire-and-forget: always answer 204 and never leak resolution
			// outcomes, so a bad beacon looks the same as a good one.
			val resolved = call.lookUpStory(
				accountsRepository,
				projectsRepository,
				projectAccessRepository,
				password = null,
				unlockedShares = call.unlockedShares(clock.now()),
			)?.result
			if (resolved is PublicProjectResult.Success) {
				// Skip the author reading their own story.
				val viewerId = call.sessions.get<UserSession>()?.userId
				if (viewerId != resolved.userId) {
					projectsRepository.findProjectRowId(resolved.userId, resolved.projectUuid)?.let { projectId ->
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

/**
 * A story's address for a Location or HX-Redirect header, at [page] if one was asked for. Path
 * parameters arrive decoded, so they are encoded again: a raw non-ASCII letter in a header goes out
 * as a Latin-1 byte ("ë" as 0xEB), and the browser follows /a/Zo%EB/... instead of
 * /a/Zo%C3%AB/..., which is a 404.
 */
private fun storyLocation(penName: String, projectName: String, page: Int?): String =
	"/a/${penName.encodeURLPathPart()}/${projectName.encodeURLPathPart()}" + (page?.let { "?page=$it" } ?: "")

/** A requested page number, if it is one: a positive whole number. */
private fun requestedPage(value: String?): Int? = value?.toIntOrNull()?.takeIf { it > 0 }

/** The password form for a private share, keeping the page the reader asked for. */
private suspend fun ApplicationCall.respondPasswordForm(
	penName: String,
	projectName: String,
	error: Boolean,
	page: Int?,
) {
	// Password-protected stories are private shares — never index them.
	applyRobotsTag(indexable = false)

	val model = withDefaults(
		mapOf(
			"page_stylesheet" to "/assets/css/story.css",
			"penName" to penName,
			"projectName" to projectName,
			"error" to error,
		)
	)
	page?.let { model["requestedPage"] = it }
	respond(MustacheContent("password-form.mustache", model))
}
