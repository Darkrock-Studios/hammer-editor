package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import kotlinx.serialization.Serializable

class StoryRendererService(
	private val projectEntityDatasource: ProjectEntityDatasource,
	private val markdownService: MarkdownService,
	private val renderCache: StoryRenderCache? = null,
) {

	suspend fun renderStoryAsHtml(
		userId: Long,
		projectId: ProjectId
	): StoryRenderResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return StoryRenderResult.ProjectNotFound

		return try {
			val sceneDefs = projectEntityDatasource.getEntityDefsByType(
				userId = userId,
				projectDef = projectDef,
				type = ApiProjectEntity.Type.SCENE
			)

			if (sceneDefs.isEmpty()) {
				return StoryRenderResult.Success(
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
			val html = markdownService.markdownToSafeHtml(markdown)

			// Calculate total word count from scene content only (not group names)
			val totalWordCount = scenes
				.filter { it.sceneType == ApiSceneType.Scene }
				.sumOf { WordCountUtils.countWords(it.content) }

			StoryRenderResult.Success(
				projectName = projectDef.name,
				html = html,
				hasContent = true,
				sceneCount = scenes.size,
				totalWordCount = totalWordCount
			)
			// Export boundary: any failure becomes a StoryExportResult.Error.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			StoryRenderResult.Error(e.message ?: "Unknown error occurred")
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

	/**
	 * Resolve a story once, so a caller that needs its [PreparedExport.version] before deciding to
	 * render doesn't pay for the lookup twice. Costs one project lookup plus one indexed hash query
	 * with no decryption. Null when the project doesn't exist.
	 */
	suspend fun prepareExport(userId: Long, projectId: ProjectId): PreparedExport? {
		val projectDef = projectEntityDatasource.getProject(userId, projectId) ?: return null
		val sceneHashes = sceneHashes(userId, projectDef)
		return PreparedExport(
			userId = userId,
			projectId = projectId,
			projectDef = projectDef,
			sceneHashes = sceneHashes,
			version = StoryRenderCache.fingerprint(projectDef.name, sceneHashes),
		)
	}

	/**
	 * [cacheable] opts this render into the disk cache. Only pass true for stories reachable
	 * without a password: a cached page is decrypted prose sitting in plaintext on disk, which is
	 * harmless for a story anyone can already fetch over HTTP but not for a private share.
	 */
	suspend fun renderStoryAsHtmlPaginated(
		userId: Long,
		projectId: ProjectId,
		page: Int = 1,
		wordsPerPage: Int = DEFAULT_WORDS_PER_PAGE,
		cacheable: Boolean = false,
	): PaginatedExportResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return PaginatedExportResult.ProjectNotFound
		// Hashes only identify a cache entry, so an uncached render never pays for them.
		val sceneHashes = if (cacheable) sceneHashes(userId, projectDef) else emptyList()

		return renderOrCache(userId, projectId, projectDef, sceneHashes, page, wordsPerPage, cacheable)
	}

	/** As [renderStoryAsHtmlPaginated], reusing a [PreparedExport] the caller already resolved. */
	suspend fun renderStoryAsHtmlPaginated(
		prepared: PreparedExport,
		page: Int = 1,
		wordsPerPage: Int = DEFAULT_WORDS_PER_PAGE,
		cacheable: Boolean = false,
	): PaginatedExportResult = renderOrCache(
		userId = prepared.userId,
		projectId = prepared.projectId,
		projectDef = prepared.projectDef,
		sceneHashes = prepared.sceneHashes,
		page = page,
		wordsPerPage = wordsPerPage,
		cacheable = cacheable,
	)

	private suspend fun renderOrCache(
		userId: Long,
		projectId: ProjectId,
		projectDef: ProjectDefinition,
		sceneHashes: List<EntityHash>,
		page: Int,
		wordsPerPage: Int,
		cacheable: Boolean,
	): PaginatedExportResult {
		val cache = renderCache.takeIf { cacheable }

		return try {
			val result = if (cache != null) {
				cache.getOrRender(
					projectId = projectId,
					projectName = projectDef.name,
					page = page,
					wordsPerPage = wordsPerPage,
					sceneHashes = sceneHashes,
				) { renderPaginated(userId, projectDef, page, wordsPerPage) }
			} else {
				renderPaginated(userId, projectDef, page, wordsPerPage).result
			}
			PaginatedExportResult.Success(result)
		} catch (e: Exception) {
			PaginatedExportResult.Error(e.message ?: "Unknown error occurred")
		}
	}

	private suspend fun sceneHashes(userId: Long, projectDef: ProjectDefinition): List<EntityHash> =
		projectEntityDatasource.getEntityHashes(
			userId = userId,
			projectDef = projectDef,
			type = ApiProjectEntity.Type.SCENE,
		)

	private suspend fun renderPaginated(
		userId: Long,
		projectDef: ProjectDefinition,
		page: Int,
		wordsPerPage: Int,
	): StoryRender {
		val sceneDefs = projectEntityDatasource.getEntityDefsByType(
			userId = userId,
			projectDef = projectDef,
			type = ApiProjectEntity.Type.SCENE
		)

		if (sceneDefs.isEmpty()) {
			return StoryRender(
				result = PaginatedStoryExportResult(
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
				),
				complete = true,
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
		val pageHtml = markdownService.markdownToSafeHtml(pageMarkdown)

		return StoryRender(
			result = PaginatedStoryExportResult(
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
			),
			// A scene that failed to load is silently absent from the prose. Serve the page, but
			// never persist it: a transient decrypt or DB failure must not outlive itself in the
			// cache, where the key wouldn't change until the author next edits.
			complete = scenes.size == sceneDefs.size,
		)
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

	/**
	 * Get the scene hierarchy for a project, suitable for populating a dropdown.
	 */
	suspend fun getSceneHierarchy(
		userId: Long,
		projectId: ProjectId
	): SceneHierarchyResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return SceneHierarchyResult.ProjectNotFound

		return try {
			val sceneDefs = projectEntityDatasource.getEntityDefsByType(
				userId = userId,
				projectDef = projectDef,
				type = ApiProjectEntity.Type.SCENE
			)

			if (sceneDefs.isEmpty()) {
				return SceneHierarchyResult.Success(
					projectName = projectDef.name,
					scenes = emptyList()
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

			val hierarchyItems = buildSceneHierarchy(scenes)

			SceneHierarchyResult.Success(
				projectName = projectDef.name,
				scenes = hierarchyItems
			)
		} catch (e: Exception) {
			SceneHierarchyResult.Error(e.message ?: "Unknown error occurred")
		}
	}

	/**
	 * Build a flat list of scene hierarchy items from the scene list.
	 * Items are ordered by their position in the tree, with depth indicating nesting level.
	 */
	private fun buildSceneHierarchy(
		scenes: List<ApiProjectEntity.SceneEntity>
	): List<SceneHierarchyItem> {
		val result = mutableListOf<SceneHierarchyItem>()
		val scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>> = scenes.groupBy { scene ->
			scene.path.lastOrNull() ?: ROOT_KEY
		}

		fun addScenesRecursively(parentId: Int, depth: Int) {
			val children = scenesByParent[parentId]?.sortedBy { it.order } ?: return
			for (scene in children) {
				result.add(
					SceneHierarchyItem(
						id = scene.id,
						name = scene.name,
						type = scene.sceneType,
						depth = depth,
						order = scene.order
					)
				)
				// If it's a group, recursively add its children
				if (scene.sceneType == ApiSceneType.Group) {
					addScenesRecursively(scene.id, depth + 1)
				}
			}
		}

		// Start with root-level scenes (parent = 0)
		addScenesRecursively(0, 0)

		return result
	}

	/**
	 * Export a single scene or group as HTML.
	 * If sceneId is a group, renders all scenes within it.
	 */
	suspend fun renderSceneAsHtml(
		userId: Long,
		projectId: ProjectId,
		sceneId: Int
	): SingleSceneExportResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return SingleSceneExportResult.ProjectNotFound

		return try {
			val sceneDefs = projectEntityDatasource.getEntityDefsByType(
				userId = userId,
				projectDef = projectDef,
				type = ApiProjectEntity.Type.SCENE
			)

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

			val targetScene = scenes.find { it.id == sceneId }
				?: return SingleSceneExportResult.SceneNotFound

			val scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>> = scenes.groupBy { scene ->
				scene.path.lastOrNull() ?: ROOT_KEY
			}

			val (markdown, wordCount) = if (targetScene.sceneType == ApiSceneType.Scene) {
				// Single scene - just its content
				val content = if (targetScene.content.isNotBlank()) {
					"## ${targetScene.name}\n\n${targetScene.content}\n"
				} else {
					"## ${targetScene.name}\n"
				}
				content to WordCountUtils.countWords(targetScene.content)
			} else {
				// Group - collect all child scenes' content
				buildGroupMarkdown(targetScene, scenesByParent)
			}

			val html = markdownService.markdownToSafeHtml(markdown)

			SingleSceneExportResult.Success(
				projectName = projectDef.name,
				sceneName = targetScene.name,
				html = html,
				hasContent = markdown.isNotBlank() && wordCount > 0,
				wordCount = wordCount
			)
		} catch (e: Exception) {
			SingleSceneExportResult.Error(e.message ?: "Unknown error occurred")
		}
	}

	/**
	 * Build markdown content for a group and all its children.
	 * Returns the markdown string and total word count.
	 */
	private fun buildGroupMarkdown(
		group: ApiProjectEntity.SceneEntity,
		scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>>
	): Pair<String, Int> {
		val builder = StringBuilder()
		var totalWordCount = 0

		builder.append("## ${group.name}\n\n")

		fun collectGroupContent(parentId: Int) {
			val children = scenesByParent[parentId]?.sortedBy { it.order } ?: return
			for (child in children) {
				if (child.sceneType == ApiSceneType.Scene) {
					if (child.content.isNotBlank()) {
						builder.append(child.content)
						builder.append("\n")
						totalWordCount += WordCountUtils.countWords(child.content)
					}
				} else {
					// Recursively collect nested group content
					collectGroupContent(child.id)
				}
			}
		}

		collectGroupContent(group.id)

		return builder.toString() to totalWordCount
	}

	companion object {
		private const val ROOT_KEY = -1
		const val DEFAULT_WORDS_PER_PAGE = 2000
	}
}

sealed class StoryRenderResult {
	data class Success(
		val projectName: String,
		val html: String,
		val hasContent: Boolean,
		val sceneCount: Int,
		val totalWordCount: Int
	) : StoryRenderResult()

	data object ProjectNotFound : StoryRenderResult()

	data class Error(val message: String) : StoryRenderResult()
}

/**
 * A story resolved once and ready to render. [version] changes whenever anything a render depends
 * on changes, so it serves as both the cache identity and the page's HTTP validator.
 */
class PreparedExport internal constructor(
	internal val userId: Long,
	internal val projectId: ProjectId,
	internal val projectDef: ProjectDefinition,
	internal val sceneHashes: List<EntityHash>,
	val version: String,
)

@Serializable
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

/**
 * Represents a scene or group in the hierarchy for dropdown display.
 */
data class SceneHierarchyItem(
	val id: Int,
	val name: String,
	val type: ApiSceneType,
	val depth: Int,
	val order: Int
) {
	val isGroup: Boolean get() = type == ApiSceneType.Group
	val isScene: Boolean get() = type == ApiSceneType.Scene

	/**
	 * Returns indentation string for dropdown display (using em-dashes).
	 */
	fun getIndent(): String = if (depth > 0) "\u2003".repeat(depth) + "— " else ""
}

/**
 * Result of getting the scene hierarchy for a project.
 */
sealed class SceneHierarchyResult {
	data class Success(
		val projectName: String,
		val scenes: List<SceneHierarchyItem>
	) : SceneHierarchyResult()

	data object ProjectNotFound : SceneHierarchyResult()
	data class Error(val message: String) : SceneHierarchyResult()
}

/**
 * Result of rendering a single scene or group.
 */
sealed class SingleSceneExportResult {
	data class Success(
		val projectName: String,
		val sceneName: String,
		val html: String,
		val hasContent: Boolean,
		val wordCount: Int
	) : SingleSceneExportResult()

	data object ProjectNotFound : SingleSceneExportResult()
	data object SceneNotFound : SingleSceneExportResult()
	data class Error(val message: String) : SingleSceneExportResult()
}
