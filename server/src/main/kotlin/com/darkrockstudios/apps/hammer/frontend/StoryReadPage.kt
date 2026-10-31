package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.romanNumeral
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.authenticatedOnly
import com.darkrockstudios.apps.hammer.frontend.utils.findProjectByUrlSegment
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.requireUser
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.ChapterRender
import com.darkrockstudios.apps.hammer.story.ChapterRenderResult
import com.darkrockstudios.apps.hammer.story.StoryRendererService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions

/** The author's own chapter-at-a-time reader, modelled on the app's reader mode. */
fun Route.storyReadPage(
	storyRendererService: StoryRendererService,
	projectsRepository: ProjectsRepository,
	serverProjectDataRepository: ServerProjectDataRepository,
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
			val requestedChapter = call.request.queryParameters["chapter"]?.toIntOrNull() ?: 1

			val (status, content) = when (
				val result = storyRendererService.renderChapter(session.userId, projectId, requestedChapter)
			) {
				is ChapterRenderResult.Success -> {
					val model = call.readerModel(result.render, projectSegment, project.uuid)
					serverProjectDataRepository.loadProjectLanguage(
						userId = session.userId,
						projectDef = ProjectDefinition(name = project.name, uuid = projectId),
					)?.let { model["storyLanguage"] = it }
					HttpStatusCode.OK to MustacheContent("story-read.mustache", model)
				}

				is ChapterRenderResult.ProjectNotFound -> HttpStatusCode.NotFound to null

				is ChapterRenderResult.Error -> {
					val model = call.withDefaults(
						mapOf(
							"page_stylesheet" to "/assets/css/story.css",
							"errorMessage" to result.message,
						)
					)
					HttpStatusCode.InternalServerError to MustacheContent("storyerror.mustache", model)
				}
			}

			if (content == null) {
				call.respond(status)
			} else {
				call.respond(status, content)
			}
		}
	}
}

private suspend fun ApplicationCall.readerModel(
	render: ChapterRender,
	projectSegment: String,
	projectUuid: String,
): MutableMap<String, Any> {
	val overview = render.overview
	val chapter = render.chapter
	val readUrl = "/story/$projectSegment/read"

	val model = withDefaults(
		mapOf(
			"page_stylesheet" to "/assets/css/story.css",
			"page_pre_script" to "/assets/js/story-read-logic.js",
			"page_script" to "/assets/js/story-read.js",
			"projectName" to overview.projectName,
			"projectKey" to projectUuid,
			"dashboardUrl" to "/story/$projectSegment",
			"complete" to overview.complete,
			"hasChapter" to (chapter != null),
		)
	)
	if (chapter == null) {
		model["title"] = "${overview.projectName} · Hammer"
		return model
	}

	val roman = romanNumeral(chapter.number)
	val total = overview.chapters.size
	val scenes = render.scenes.mapIndexed { index, scene ->
		mapOf<String, Any>(
			"id" to scene.id,
			"name" to scene.name,
			"marker" to "§ $roman.${index + 1}",
			"html" to resolveAuthorImages(scene.html, projectSegment),
			"hasProse" to scene.html.isNotEmpty(),
			"isFirst" to (index == 0),
		)
	}
	val toc = overview.chapters.map { entry ->
		val isCurrent = entry.number == chapter.number
		mapOf<String, Any>(
			"number" to entry.number,
			"roman" to romanNumeral(entry.number),
			"title" to entry.title,
			"sceneCount" to msg("story_read_scene_count", entry.sceneCount),
			"url" to "$readUrl?chapter=${entry.number}",
			"isCurrent" to isCurrent,
			"scenes" to if (isCurrent) render.scenes.map { mapOf("id" to it.id, "name" to it.name) } else emptyList(),
		)
	}

	model.putAll(
		mapOf(
			"title" to "${chapter.title} · ${overview.projectName} · Hammer",
			"chapterNumber" to chapter.number,
			"chapterRoman" to roman,
			"chapterTitle" to chapter.title,
			"chapterSceneCount" to msg("story_read_scene_count", chapter.sceneCount),
			"chapterPosition" to msg("story_read_chapter_position", chapter.number, total),
			"scenes" to scenes,
			"hasScenes" to scenes.isNotEmpty(),
			"toc" to toc,
			"hasPrev" to (chapter.number > 1),
			"prevUrl" to "$readUrl?chapter=${chapter.number - 1}",
			"hasNext" to (chapter.number < total),
			"nextUrl" to "$readUrl?chapter=${chapter.number + 1}",
		)
	)
	return model
}
