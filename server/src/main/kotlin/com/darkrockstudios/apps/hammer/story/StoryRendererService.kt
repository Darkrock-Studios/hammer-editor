package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.TreeChapter
import com.darkrockstudios.apps.hammer.base.chapterScenes
import com.darkrockstudios.apps.hammer.base.estimateReadingMinutes
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.markdown.countWords
import com.darkrockstudios.apps.hammer.base.storyChapters
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.MarkdownRenderer
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

class StoryRendererService(
	private val projectEntityDatasource: ProjectEntityDatasource,
	private val markdownRenderer: MarkdownRenderer,
	private val renderCache: StoryRenderCache? = null,
) {

	/**
	 * Scenes are separate passages. Without the blank line the last paragraph of one scene and the
	 * first of the next parse as a single paragraph; the trim keeps a scene that already ends in
	 * newlines from gaining a spurious break.
	 */
	private fun StringBuilder.appendScene(content: String) {
		append(content.trimEnd())
		append("\n\n")
	}

	/**
	 * Resolve a story once, so a caller that needs its [PreparedExport.version] before deciding to
	 * render doesn't pay for the lookup twice. A whole-story export costs one project lookup plus
	 * one indexed hash query with no decryption. Null when the project doesn't exist.
	 */
	suspend fun prepareExport(
		userId: Long,
		projectId: ProjectId,
		sceneFilter: Set<Int>? = null,
	): PreparedExport? {
		val projectDef = projectEntityDatasource.getProject(userId, projectId) ?: return null
		val allHashes = sceneHashes(userId, projectDef)

		if (sceneFilter == null) {
			return PreparedExport(
				userId = userId,
				projectId = projectId,
				projectDef = projectDef,
				sceneHashes = allHashes,
				version = StoryRenderCache.fingerprint(projectDef.name, allHashes),
			)
		}

		val presentIds = allHashes.map { it.id }.toSet()
		val selectedIds = presentIds intersect sceneFilter
		if (selectedIds.isEmpty()) return null

		// Where a selected scene falls in the reading order is decided by the groups above it, so
		// the share's validator has to cover those too or a reordered chapter is answered 304 with
		// the old order. A scene's path is its whole ancestor chain, so one more read settles it.
		val selected = loadScenes(userId, projectDef, selectedIds)
		val ancestorIds = selected.flatMapTo(mutableSetOf()) { it.path }
			.intersect(presentIds) - selectedIds
		val ancestors = loadScenes(userId, projectDef, ancestorIds)

		val coveredIds = selectedIds + ancestorIds
		// Still only this share's scenes and the structure around them: an edit to a scene the
		// share doesn't show leaves the validator alone, and two shares of different scene sets
		// never collide on one.
		val sceneHashes = allHashes.filter { it.id in coveredIds }
		return PreparedExport(
			userId = userId,
			projectId = projectId,
			projectDef = projectDef,
			sceneHashes = sceneHashes,
			sceneFilter = sceneFilter,
			// Read once here and carried to the render, so a cache miss doesn't decrypt twice.
			scenes = selected + ancestors,
			scenesComplete = selected.size + ancestors.size == coveredIds.size,
			version = StoryRenderCache.fingerprint(projectDef.name, sceneHashes),
		)
	}

	/** Loads [ids], skipping any that fail; the caller compares sizes to detect a partial read. */
	private suspend fun loadScenes(
		userId: Long,
		projectDef: ProjectDefinition,
		ids: Collection<Int>,
	): List<ApiProjectEntity.SceneEntity> = ids.mapNotNull { id ->
		val result = projectEntityDatasource.loadEntity(
			userId = userId,
			projectDef = projectDef,
			entityId = id,
			entityType = ApiProjectEntity.Type.SCENE,
			serializer = ApiProjectEntity.SceneEntity.serializer()
		)
		if (isSuccess(result)) result.data else null
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
	): StoryResult<PaginatedStoryExportResult> = readProject(userId, projectId) { projectDef ->
		// Hashes only identify a cache entry, so an uncached render never pays for them.
		val sceneHashes = if (cacheable) sceneHashes(userId, projectDef) else emptyList()
		renderOrCache(userId, projectId, projectDef, sceneHashes, page, wordsPerPage, cacheable)
	}

	/** As [renderStoryAsHtmlPaginated], reusing a [PreparedExport] the caller already resolved. */
	suspend fun renderStoryAsHtmlPaginated(
		prepared: PreparedExport,
		page: Int = 1,
		wordsPerPage: Int = DEFAULT_WORDS_PER_PAGE,
		cacheable: Boolean = false,
	): StoryResult<PaginatedStoryExportResult> = readSafely {
		renderOrCache(
			userId = prepared.userId,
			projectId = prepared.projectId,
			projectDef = prepared.projectDef,
			sceneHashes = prepared.sceneHashes,
			page = page,
			wordsPerPage = wordsPerPage,
			cacheable = cacheable,
			sceneFilter = prepared.sceneFilter,
			preloaded = prepared.scenes?.let { PreloadedScenes(it, prepared.scenesComplete) },
		)
	}

	private suspend fun renderOrCache(
		userId: Long,
		projectId: ProjectId,
		projectDef: ProjectDefinition,
		sceneHashes: List<EntityHash>,
		page: Int,
		wordsPerPage: Int,
		cacheable: Boolean,
		sceneFilter: Set<Int>? = null,
		preloaded: PreloadedScenes? = null,
	): PaginatedStoryExportResult {
		val cache = renderCache.takeIf { cacheable }
		return if (cache != null) {
			cache.getOrRender(
				projectId = projectId,
				projectName = projectDef.name,
				page = page,
				wordsPerPage = wordsPerPage,
				sceneHashes = sceneHashes,
			) { renderPaginated(userId, projectDef, page, wordsPerPage, sceneFilter, preloaded) }
		} else {
			renderPaginated(userId, projectDef, page, wordsPerPage, sceneFilter, preloaded).result
		}
	}

	/** Runs [read] on the project, turning a missing project and any failure into results. */
	private suspend fun <T> readProject(
		userId: Long,
		projectId: ProjectId,
		read: suspend (ProjectDefinition) -> T,
	): StoryResult<T> {
		val projectDef = projectEntityDatasource.getProject(userId, projectId) ?: return StoryResult.ProjectNotFound
		return readSafely { read(projectDef) }
	}

	/** [read]'s value, or an [StoryResult.Error] for any failure; cancellation still propagates. */
	private suspend fun <T> readSafely(read: suspend () -> T): StoryResult<T> = try {
		StoryResult.Success(read())
	} catch (e: CancellationException) {
		throw e
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		StoryResult.Error(e.message ?: "Unknown error occurred")
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
		sceneFilter: Set<Int>? = null,
		preloaded: PreloadedScenes? = null,
	): StoryRender {
		// A scene-limited share arrives with its scenes and their ancestor groups already read;
		// nothing else in the project is touched, so unshared prose is never decrypted. A whole
		// story reads every scene, which is also every scene it renders.
		val scenes: List<ApiProjectEntity.SceneEntity>
		val complete: Boolean
		if (preloaded != null) {
			scenes = preloaded.scenes
			complete = preloaded.complete
		} else {
			val loaded = loadAllScenes(userId, projectDef)
			scenes = loaded.scenes
			complete = loaded.complete
		}

		if (scenes.isEmpty()) {
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
					estimatedReadingTimeMinutes = 0
				),
				complete = complete,
			)
		}

		val selected = walkScenes(scenes)
			.let { ordered -> if (sceneFilter == null) ordered else ordered.filter { it.id in sceneFilter } }

		val processedScenes = selected.map { scene ->
			ProcessedScene(
				scene = scene,
				wordCount = countWords(scene.content),
				markdown = scene.content
			)
		}

		val totalWordCount = processedScenes.sumOf { it.wordCount }
		val estimatedReadingTime = estimateReadingMinutes(totalWordCount)

		// Group scenes into pages based on word count
		val pages = paginateScenes(processedScenes, wordsPerPage)
		val totalPages = pages.size.coerceAtLeast(1)
		val currentPage = page.coerceIn(1, totalPages)

		// Get scenes for the current page
		val currentPageScenes = if (pages.isNotEmpty()) pages[currentPage - 1] else emptyList()

		// Build markdown and HTML for current page only
		val pageMarkdown = buildPaginatedMarkdown(projectDef.name, currentPageScenes, currentPage == 1)
		val pageHtml = markdownRenderer.markdownToSafeHtml(pageMarkdown, preserveLineBreaks = true, images = true)

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
			complete = complete,
		)
	}

	/**
	 * Flattens the scene tree to its leaf scenes in the order the author sees them: depth first,
	 * siblings by [ApiProjectEntity.SceneEntity.order], with unreachable scenes last.
	 */
	private fun walkScenes(scenes: List<ApiProjectEntity.SceneEntity>): List<ApiProjectEntity.SceneEntity> =
		collectChapters(scenes).flatMap { it.scenes }

	/**
	 * Splits the story into chapters by base's [storyChapters], the rules the app's reader uses.
	 * Scenes the root can't reach follow as chapters of their own so nothing the author wrote goes
	 * missing.
	 */
	private fun collectChapters(
		scenes: List<ApiProjectEntity.SceneEntity>,
	): List<TreeChapter<ApiProjectEntity.SceneEntity>> {
		val byId = scenes.associateBy { it.id }
		val scenesByParent = scenes.groupBy { it.path.lastOrNull() ?: ROOT_KEY }
		val visited = mutableSetOf<Int>()

		// Lists each scene at most once, so a path cycle can't recurse forever.
		fun children(parent: ApiProjectEntity.SceneEntity?) =
			scenesByParent[parent?.id ?: ROOT_ID].orEmpty().sortedBy { it.order }.filter { visited.add(it.id) }

		val isScene = { scene: ApiProjectEntity.SceneEntity -> scene.sceneType == ApiSceneType.Scene }
		val chapters = storyChapters(children(null), ::children, isScene).toMutableList()

		// An unreachable scene stays with the outermost group still above it, so a group whose
		// parent is missing reads as one chapter rather than one per scene.
		for (scene in scenes) {
			if (!isScene(scene) || scene.id in visited) continue
			val group = outermostUnvisitedGroup(scene, byId, visited)
			if (group != null) {
				visited += group.id
				chapterScenes(group, ::children, isScene)
					.takeIf { it.isNotEmpty() }
					?.let { chapters += TreeChapter(group, it) }
			}
			if (visited.add(scene.id)) chapters += TreeChapter(scene, listOf(scene))
		}

		return chapters
	}

	/** The outermost group on [scene]'s path whose chain down to the scene is intact and unvisited. */
	private fun outermostUnvisitedGroup(
		scene: ApiProjectEntity.SceneEntity,
		byId: Map<Int, ApiProjectEntity.SceneEntity>,
		visited: Set<Int>,
	): ApiProjectEntity.SceneEntity? {
		var anchor: ApiProjectEntity.SceneEntity? = null
		for (id in scene.path.asReversed()) {
			val group = byId[id]
				?.takeIf { it.sceneType == ApiSceneType.Group && it.id !in visited }
				?: break
			anchor = group
		}
		return anchor
	}

	private fun chapterEntries(chapters: List<TreeChapter<ApiProjectEntity.SceneEntity>>): List<StoryChapter> =
		chapters.mapIndexed { index, chapter ->
			StoryChapter(number = index + 1, title = chapter.top.name, sceneCount = chapter.scenes.size)
		}

	/** Every scene entity in the project; [PreloadedScenes.complete] is false if any failed to load. */
	private suspend fun loadAllScenes(userId: Long, projectDef: ProjectDefinition): PreloadedScenes {
		val sceneDefs = projectEntityDatasource.getEntityDefsByType(
			userId = userId,
			projectDef = projectDef,
			type = ApiProjectEntity.Type.SCENE
		)
		val scenes = loadScenes(userId, projectDef, sceneDefs.map { it.id })
		if (scenes.size != sceneDefs.size) {
			logger.warn(
				"{} of {} scenes failed to load for project {}",
				sceneDefs.size - scenes.size,
				sceneDefs.size,
				projectDef.uuid.id,
			)
		}
		return PreloadedScenes(scenes, complete = scenes.size == sceneDefs.size)
	}

	/** The story's chapters and totals, without rendering any prose. */
	suspend fun storyOverview(userId: Long, projectId: ProjectId): StoryResult<StoryOverview> =
		readProject(userId, projectId) { projectDef ->
			val loaded = loadAllScenes(userId, projectDef)
			val chapters = collectChapters(loaded.scenes)
			StoryOverview(
				projectName = projectDef.name,
				chapters = chapterEntries(chapters),
				sceneCount = chapters.sumOf { it.scenes.size },
				totalWordCount = chapters.sumOf { chapter -> chapter.scenes.sumOf { countWords(it.content) } },
				complete = loaded.complete,
			)
		}

	/**
	 * Renders chapter [chapterNumber] (1-based, coerced into range) one scene at a time. Scene and
	 * chapter names are left out of the HTML; the page renders them itself. Nothing is word
	 * counted: the reader shows no counts, and counting a whole manuscript per page turn is costly.
	 */
	suspend fun renderChapter(userId: Long, projectId: ProjectId, chapterNumber: Int): StoryResult<ChapterRender> =
		readProject(userId, projectId) { projectDef ->
			val loaded = loadAllScenes(userId, projectDef)
			val chapters = collectChapters(loaded.scenes)
			val entries = chapterEntries(chapters)
			if (chapters.isEmpty()) {
				return@readProject ChapterRender(
					projectDef.name,
					entries,
					chapter = null,
					scenes = emptyList(),
					loaded.complete,
				)
			}

			val index = chapterNumber.coerceIn(1, chapters.size) - 1
			val scenes = chapters[index].scenes.map { scene ->
				ChapterScene(
					id = scene.id,
					name = scene.name,
					html = if (scene.content.isBlank()) {
						""
					} else {
						markdownRenderer.markdownToSafeHtml(scene.content, preserveLineBreaks = true, images = true)
					},
				)
			}
			ChapterRender(projectDef.name, entries, entries[index], scenes, loaded.complete)
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
				builder.appendScene(scene.markdown)
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
	suspend fun getSceneHierarchy(userId: Long, projectId: ProjectId): StoryResult<SceneHierarchy> =
		readProject(userId, projectId) { projectDef ->
			SceneHierarchy(
				projectName = projectDef.name,
				scenes = buildSceneHierarchy(loadAllScenes(userId, projectDef).scenes),
			)
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

	companion object {
		/** Bucket for a scene with no path at all; distinct from the real root, which is id 0. */
		private const val ROOT_KEY = -1
		private const val ROOT_ID = 0
		const val DEFAULT_WORDS_PER_PAGE = 2000
		private val logger = LoggerFactory.getLogger(StoryRendererService::class.java)
	}
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
	/** Scene ids the render is limited to; null renders the entire story. */
	internal val sceneFilter: Set<Int>? = null,
	/**
	 * The scenes a limited share renders and the groups that place them, read while resolving the
	 * version. Null for a whole-story export, which reads its scenes at render time instead.
	 */
	internal val scenes: List<ApiProjectEntity.SceneEntity>? = null,
	/** False when one of [scenes] failed to load, which bars the render from the cache. */
	internal val scenesComplete: Boolean = true,
)

/** Scenes resolved before the render, with whether the set is whole. */
internal class PreloadedScenes(
	val scenes: List<ApiProjectEntity.SceneEntity>,
	val complete: Boolean,
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

/** One chapter of the story; [number] is 1-based. */
data class StoryChapter(
	val number: Int,
	val title: String,
	val sceneCount: Int,
)

data class StoryOverview(
	val projectName: String,
	val chapters: List<StoryChapter>,
	/** Leaf scenes only; groups are structure, not scenes. */
	val sceneCount: Int,
	val totalWordCount: Int,
	/** False when a scene failed to load, so the chapters and totals leave it out. */
	val complete: Boolean,
) {
	val readingTimeMinutes: Int
		get() = estimateReadingMinutes(totalWordCount)
}

/** The outcome of reading a story; [Success.value] is what the read produced. */
sealed interface StoryResult<out T> {
	data class Success<out T>(val value: T) : StoryResult<T>
	data object ProjectNotFound : StoryResult<Nothing>
	data class Error(val message: String) : StoryResult<Nothing>
}

/** A scene's prose as HTML, empty when the scene has none. */
data class ChapterScene(
	val id: Int,
	val name: String,
	val html: String,
)

/**
 * One chapter ready to read, with every chapter listed for navigation. [chapter] is null only when
 * the story has no chapters at all; [complete] is false when a scene failed to load.
 */
data class ChapterRender(
	val projectName: String,
	val chapters: List<StoryChapter>,
	val chapter: StoryChapter?,
	val scenes: List<ChapterScene>,
	val complete: Boolean,
)


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
}

/** A project's scenes and groups in tree order, for the share and review scene pickers. */
data class SceneHierarchy(
	val projectName: String,
	val scenes: List<SceneHierarchyItem>,
)
