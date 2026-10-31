package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.romanNumeral
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.applyRevalidationHeaders
import com.darkrockstudios.apps.hammer.frontend.utils.authenticatedOnly
import com.darkrockstudios.apps.hammer.frontend.utils.findProjectByUrlSegment
import com.darkrockstudios.apps.hammer.frontend.utils.getLocale
import com.darkrockstudios.apps.hammer.frontend.utils.localizedMsg
import com.darkrockstudios.apps.hammer.frontend.utils.matchesETag
import com.darkrockstudios.apps.hammer.frontend.utils.pageETag
import com.darkrockstudios.apps.hammer.frontend.utils.requireUser
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.ChapterRender
import com.darkrockstudios.apps.hammer.story.StoryImageService
import com.darkrockstudios.apps.hammer.story.StoryRendererService
import com.darkrockstudios.apps.hammer.story.StoryResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import java.util.Locale

/**
 * The author's own chapter-at-a-time reader, modelled on the app's reader mode. With no `chapter`
 * asked for, a story with a cover opens on the cover as its first page.
 */
fun Route.storyReadPage(
	storyRendererService: StoryRendererService,
	projectsRepository: ProjectsRepository,
	serverProjectDataRepository: ServerProjectDataRepository,
	storyImageService: StoryImageService,
) {
	authenticatedOnly {
		get("/story/{projectName}/read") {
			val session = call.sessions.requireUser()
			val project = call.parameters["projectName"]
				?.let { projectsRepository.findProjectByUrlSegment(session.userId, it) }
			if (project == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val projectId = ProjectId(project.uuid)
			val projectSegment = ProjectName.projectSegment(project.name, project.uuid)
			val projectDef = ProjectDefinition(name = project.name, uuid = projectId)
			val chapterParam = call.request.queryParameters["chapter"]
			val coverUrl = storyCoverImageId(serverProjectDataRepository, storyImageService, session.userId, projectDef)
				?.let { authorImageUrl(projectSegment, it) }
			val showCover = chapterParam == null && coverUrl != null
			val requestedChapter = chapterParam?.toIntOrNull() ?: 1
			val readUrl = "/story/$projectSegment/read"

			val model = call.withDefaults(
				mapOf(
					"page_stylesheet" to "/assets/css/story.css",
					"page_pre_script" to "/assets/js/story-read-logic.js",
					"page_script" to "/assets/js/story-read.js",
					"projectKey" to project.uuid,
					"dashboardUrl" to "/story/$projectSegment",
					"readUrl" to readUrl,
				)
			)

			// The page follows from the story's version and these inputs, so an unchanged chapter is
			// answered without decrypting the manuscript. The data hash stands in for the language.
			val etag = storyRendererService.prepareExport(session.userId, projectId)?.let {
				pageETag(
					model,
					it.version,
					showCover,
					requestedChapter,
					coverUrl,
					serverProjectDataRepository.loadDataHash(session.userId, projectDef),
				)
			}
			if (etag != null && call.matchesETag(etag)) {
				call.applyRevalidationHeaders(etag)
				call.respond(HttpStatusCode.NotModified)
				return@get
			}

			val (status, content) = when (
				val result = storyRendererService.renderChapter(session.userId, projectId, requestedChapter)
			) {
				is StoryResult.Success -> {
					val render = result.value
					call.addReaderModel(model, render, projectSegment, readUrl, coverUrl, showCover)
					serverProjectDataRepository.loadProjectLanguage(
						userId = session.userId,
						projectDef = projectDef,
					)?.let { model["storyLanguage"] = it }
					// A scene that failed to load would otherwise be pinned in the browser until an edit.
					if (render.complete) etag?.let { call.applyRevalidationHeaders(it) }
					HttpStatusCode.OK to MustacheContent("story-read.mustache", model)
				}

				is StoryResult.ProjectNotFound -> HttpStatusCode.NotFound to null

				is StoryResult.Error -> HttpStatusCode.InternalServerError to call.storyErrorContent(result.message)
			}

			if (content == null) {
				call.respond(status)
			} else {
				call.respond(status, content)
			}
		}
	}
}

/** Fills [model] with the chapter, or the cover, that [render] holds. */
private suspend fun ApplicationCall.addReaderModel(
	model: MutableMap<String, Any>,
	render: ChapterRender,
	projectSegment: String,
	readUrl: String,
	coverUrl: String?,
	showCover: Boolean,
) {
	val chapter = render.chapter
	model.putAll(
		mapOf(
			"projectName" to render.projectName,
			"complete" to render.complete,
			"hasChapter" to (chapter != null),
		)
	)
	if (chapter == null) {
		model["title"] = "${render.projectName} · Hammer"
		return
	}

	// Resolved once: a long table of contents would otherwise look the locale up per chapter.
	val locale = getLocale()
	val untitled = localizedMsg(locale, "story_read_untitled")
	fun named(name: String) = name.ifBlank { untitled }

	val total = render.chapters.size
	val coverLabel = localizedMsg(locale, "story_read_cover")
	if (showCover && coverUrl != null) {
		model.putAll(
			mapOf(
				"title" to "${render.projectName} · Hammer",
				"isCover" to true,
				"coverUrl" to coverUrl,
				"position" to coverLabel,
				"toc" to tableOfContents(render, readUrl, current = null, currentScenes = emptyList(), ::named, locale),
				"hasCoverEntry" to true,
				"coverIsCurrent" to true,
				"coverLabel" to coverLabel,
				"hasPrev" to false,
				"hasNext" to true,
				"nextUrl" to "$readUrl?chapter=1",
			)
		)
		return
	}

	val roman = romanNumeral(chapter.number)
	val scenes = render.scenes.mapIndexed { index, scene ->
		mapOf<String, Any>(
			"id" to scene.id,
			"name" to named(scene.name),
			"marker" to "§ $roman.${index + 1}",
			"html" to resolveAuthorImages(scene.html, projectSegment),
			"hasProse" to scene.html.isNotEmpty(),
			"isFirst" to (index == 0),
		)
	}
	val currentScenes = scenes.map { mapOf("id" to it.getValue("id"), "name" to it.getValue("name")) }
	val toc = tableOfContents(render, readUrl, chapter.number, currentScenes, ::named, locale)
	val prevIsCover = chapter.number == 1 && coverUrl != null

	model.putAll(
		mapOf(
			"title" to "${named(chapter.title)} · ${render.projectName} · Hammer",
			"chapterNumber" to chapter.number,
			"chapterRoman" to roman,
			"chapterTitle" to named(chapter.title),
			"chapterSceneCount" to localizedMsg(locale, "story_read_scene_count", chapter.sceneCount),
			"position" to localizedMsg(locale, "story_read_chapter_position", chapter.number, total),
			"scenes" to scenes,
			"hasScenes" to scenes.isNotEmpty(),
			"toc" to toc,
			"hasCoverEntry" to (coverUrl != null),
			"coverLabel" to coverLabel,
			"hasPrev" to (chapter.number > 1 || prevIsCover),
			"prevUrl" to if (prevIsCover) readUrl else "$readUrl?chapter=${chapter.number - 1}",
			"prevLabel" to if (prevIsCover) coverLabel else localizedMsg(locale, "story_read_prev"),
			"hasNext" to (chapter.number < total),
			"nextUrl" to "$readUrl?chapter=${chapter.number + 1}",
		)
	)
}

private fun tableOfContents(
	render: ChapterRender,
	readUrl: String,
	current: Int?,
	currentScenes: List<Map<String, Any>>,
	named: (String) -> String,
	locale: Locale,
): List<Map<String, Any>> = render.chapters.map { entry ->
	val isCurrent = entry.number == current
	mapOf(
		"number" to entry.number,
		"roman" to romanNumeral(entry.number),
		"title" to named(entry.title),
		"sceneCount" to localizedMsg(locale, "story_read_scene_count", entry.sceneCount),
		"url" to "$readUrl?chapter=${entry.number}",
		"isCurrent" to isCurrent,
		"scenes" to if (isCurrent) currentScenes else emptyList(),
	)
}
