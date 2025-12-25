package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

class StoryExportService(
	private val projectEntityDatasource: ProjectEntityDatasource,
) {
	private val markdownFlavour = CommonMarkFlavourDescriptor()
	private val markdownParser = MarkdownParser(markdownFlavour)

	suspend fun exportStoryAsHtml(
		userId: Long,
		projectId: ProjectId
	): StoryExportResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return StoryExportResult.ProjectNotFound

		return try {
			val sceneDefs = projectEntityDatasource.getEntityDefsByType(
				userId = userId,
				projectDef = projectDef,
				type = ApiProjectEntity.Type.SCENE
			)

			if (sceneDefs.isEmpty()) {
				return StoryExportResult.Success(
					projectName = projectDef.name,
					html = "",
					hasContent = false,
					sceneCount = 0,
					totalWordCount = 0
				)
			}

			val scenes: List<ApiProjectEntity.SceneEntity> = sceneDefs.mapNotNull { def ->
				val result = projectEntityDatasource.loadEntity(
					userId = userId,
					projectDef = projectDef,
					entityId = def.id,
					entityType = ApiProjectEntity.Type.SCENE,
					serializer = ApiProjectEntity.SceneEntity.serializer()
				)
				if (isSuccess(result)) result.data else null
			}

			val markdown = buildStoryMarkdown(projectDef.name, scenes)
			val html = markdownToHtml(markdown)

			// Calculate total word count from scene content only (not group names)
			val totalWordCount = scenes
				.filter { it.sceneType == ApiSceneType.Scene }
				.sumOf { WordCountUtils.countWords(it.content) }

			StoryExportResult.Success(
				projectName = projectDef.name,
				html = html,
				hasContent = true,
				sceneCount = scenes.size,
				totalWordCount = totalWordCount
			)
		} catch (e: Exception) {
			StoryExportResult.Error(e.message ?: "Unknown error occurred")
		}
	}

	private fun buildStoryMarkdown(
		projectName: String,
		scenes: List<ApiProjectEntity.SceneEntity>
	): String {
		val builder = StringBuilder()
		builder.append("# $projectName\n\n")

		// Group scenes by parent (last element of path, or ROOT_KEY if empty)
		val scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>> = scenes.groupBy { scene ->
			scene.path.lastOrNull() ?: ROOT_KEY
		}

		// Get root-level scenes (direct children of root)
		val rootScenes = scenesByParent[0]?.sortedBy { it.order } ?: emptyList()

		var chapterNumber = 1
		for (scene in rootScenes) {
			builder.append("\n## $chapterNumber. ${scene.name}\n\n")

			if (scene.sceneType == ApiSceneType.Scene) {
				// Write scene content directly
				if (scene.content.isNotBlank()) {
					builder.append(scene.content)
					builder.append("\n")
				}
			} else {
				// It's a Group - write all child scenes' content
				writeGroupChildren(builder, scene.id, scenesByParent)
			}

			chapterNumber++
		}

		return builder.toString()
	}

	private fun writeGroupChildren(
		builder: StringBuilder,
		parentId: Int,
		scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>>
	) {
		val children = scenesByParent[parentId]?.sortedBy { it.order } ?: return

		for (child in children) {
			if (child.sceneType == ApiSceneType.Scene) {
				if (child.content.isNotBlank()) {
					builder.append(child.content)
					builder.append("\n")
				}
			} else {
				// Recursively process nested groups
				writeGroupChildren(builder, child.id, scenesByParent)
			}
		}
	}

	private fun markdownToHtml(markdown: String): String {
		val parsedTree = markdownParser.buildMarkdownTreeFromString(markdown)
		return HtmlGenerator(markdown, parsedTree, markdownFlavour).generateHtml()
	}

	suspend fun exportStoryAsHtmlPaginated(
		userId: Long,
		projectId: ProjectId,
		page: Int = 1,
		wordsPerPage: Int = DEFAULT_WORDS_PER_PAGE
	): PaginatedExportResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return PaginatedExportResult.ProjectNotFound

		return try {
			val sceneDefs = projectEntityDatasource.getEntityDefsByType(
				userId = userId,
				projectDef = projectDef,
				type = ApiProjectEntity.Type.SCENE
			)

			if (sceneDefs.isEmpty()) {
				return PaginatedExportResult.Success(
					PaginatedStoryExportResult(
						projectName = projectDef.name,
						pageHtml = "",
						hasContent = false,
						sceneCount = 0,
						totalWordCount = 0,
						currentPage = 1,
						totalPages = 1,
						hasNextPage = false,
						hasPrevPage = false,
						nextPage = 1,
						prevPage = 1,
						estimatedReadingTimeMinutes = 1
					)
				)
			}

			val scenes: List<ApiProjectEntity.SceneEntity> = sceneDefs.mapNotNull { def ->
				val result = projectEntityDatasource.loadEntity(
					userId = userId,
					projectDef = projectDef,
					entityId = def.id,
					entityType = ApiProjectEntity.Type.SCENE,
					serializer = ApiProjectEntity.SceneEntity.serializer()
				)
				if (isSuccess(result)) result.data else null
			}

			// Process all scenes with their word counts (only count actual scenes, not groups)
			val processedScenes = scenes
				.filter { it.sceneType == ApiSceneType.Scene }
				.map { scene ->
					ProcessedScene(
						scene = scene,
						wordCount = WordCountUtils.countWords(scene.content),
						markdown = scene.content
					)
				}

			val totalWordCount = processedScenes.sumOf { it.wordCount }
			val estimatedReadingTime = WordCountUtils.estimateReadingTimeMinutes(totalWordCount)

			// Group scenes into pages based on word count
			val pages = paginateScenes(processedScenes, wordsPerPage)
			val totalPages = pages.size.coerceAtLeast(1)
			val currentPage = page.coerceIn(1, totalPages)

			// Get scenes for the current page
			val currentPageScenes = if (pages.isNotEmpty()) pages[currentPage - 1] else emptyList()

			// Build markdown and HTML for current page only
			val pageMarkdown = buildPaginatedMarkdown(projectDef.name, currentPageScenes, currentPage == 1)
			val pageHtml = markdownToHtml(pageMarkdown)

			PaginatedExportResult.Success(
				PaginatedStoryExportResult(
					projectName = projectDef.name,
					pageHtml = pageHtml,
					hasContent = currentPageScenes.isNotEmpty(),
					sceneCount = processedScenes.size,
					totalWordCount = totalWordCount,
					currentPage = currentPage,
					totalPages = totalPages,
					hasNextPage = currentPage < totalPages,
					hasPrevPage = currentPage > 1,
					nextPage = (currentPage + 1).coerceAtMost(totalPages),
					prevPage = (currentPage - 1).coerceAtLeast(1),
					estimatedReadingTimeMinutes = estimatedReadingTime
				)
			)
		} catch (e: Exception) {
			PaginatedExportResult.Error(e.message ?: "Unknown error occurred")
		}
	}

	private fun paginateScenes(
		scenes: List<ProcessedScene>,
		wordsPerPage: Int
	): List<List<ProcessedScene>> {
		if (scenes.isEmpty()) return emptyList()

		val pages = mutableListOf<List<ProcessedScene>>()
		var currentPageScenes = mutableListOf<ProcessedScene>()
		var currentPageWordCount = 0

		for (scene in scenes) {
			// If adding this scene would exceed the limit AND we already have content,
			// start a new page (unless it's the first scene on the page)
			if (currentPageWordCount > 0 && currentPageWordCount + scene.wordCount > wordsPerPage) {
				pages.add(currentPageScenes.toList())
				currentPageScenes = mutableListOf()
				currentPageWordCount = 0
			}

			currentPageScenes.add(scene)
			currentPageWordCount += scene.wordCount
		}

		// Don't forget the last page
		if (currentPageScenes.isNotEmpty()) {
			pages.add(currentPageScenes.toList())
		}

		return pages
	}

	private fun buildPaginatedMarkdown(
		projectName: String,
		scenes: List<ProcessedScene>,
		includeTitle: Boolean
	): String {
		val builder = StringBuilder()

		if (includeTitle) {
			builder.append("# $projectName\n\n")
		}

		for (scene in scenes) {
			if (scene.markdown.isNotBlank()) {
				// Add scene name as chapter header
				builder.append("## ${scene.scene.name}\n\n")
				builder.append(scene.markdown)
				builder.append("\n\n")
			}
		}

		return builder.toString()
	}

	private data class ProcessedScene(
		val scene: ApiProjectEntity.SceneEntity,
		val wordCount: Int,
		val markdown: String
	)

	companion object {
		private const val ROOT_KEY = -1
		const val DEFAULT_WORDS_PER_PAGE = 2000
	}
}

sealed class StoryExportResult {
	data class Success(
		val projectName: String,
		val html: String,
		val hasContent: Boolean,
		val sceneCount: Int,
		val totalWordCount: Int
	) : StoryExportResult()

	data object ProjectNotFound : StoryExportResult()

	data class Error(val message: String) : StoryExportResult()
}

data class PaginatedStoryExportResult(
	val projectName: String,
	val pageHtml: String,
	val hasContent: Boolean,
	val sceneCount: Int,
	val totalWordCount: Int,
	val currentPage: Int,
	val totalPages: Int,
	val hasNextPage: Boolean,
	val hasPrevPage: Boolean,
	val nextPage: Int,
	val prevPage: Int,
	val estimatedReadingTimeMinutes: Int
)

sealed class PaginatedExportResult {
	data class Success(val data: PaginatedStoryExportResult) : PaginatedExportResult()
	data object ProjectNotFound : PaginatedExportResult()
	data class Error(val message: String) : PaginatedExportResult()
}
