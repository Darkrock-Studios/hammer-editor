package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.markdown.countWords
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
			val html = markdownRenderer.markdownToSafeHtml(markdown, preserveLineBreaks = true, images = true)

			// Calculate total word count from scene content only (not group names)
			val totalWordCount = scenes
				.filter { it.sceneType == ApiSceneType.Scene }
				.sumOf { countWords(it.content) }

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

		for (scene in rootScenes) {
			// The author's own heading, verbatim: a story renders as written, never renumbered.
			builder.append("## ${scene.name}\n\n")

			if (scene.sceneType == ApiSceneType.Scene) {
				// Write scene content directly
				if (scene.content.isNotBlank()) {
					builder.appendScene(scene.content)
				}
			} else {
				// It's a Group - write all child scenes' content
				writeGroupChildren(builder, scene.id, scenesByParent)
			}
		}

		// A scene the walk can't reach still belongs to the author, and their readers can see it on
		// the published page; it would be worse for it to be missing here than to sit at the end.
		for (orphan in walkScenes(scenes).orphans) {
			builder.append("## ${orphan.name}\n\n")
			if (orphan.content.isNotBlank()) builder.appendScene(orphan.content)
		}

		return builder.toString()
	}

	/** Appends every scene under [parentId], depth first, and returns their total word count. */
	private fun writeGroupChildren(
		builder: StringBuilder,
		parentId: Int,
		scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>>
	): Int {
		val children = scenesByParent[parentId]?.sortedBy { it.order } ?: return 0

		var wordCount = 0
		for (child in children) {
			if (child.sceneType == ApiSceneType.Scene) {
				if (child.content.isNotBlank()) {
					builder.appendScene(child.content)
					wordCount += countWords(child.content)
				}
			} else {
				wordCount += writeGroupChildren(builder, child.id, scenesByParent)
			}
		}
		return wordCount
	}

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
		sceneFilter = prepared.sceneFilter,
		preloaded = prepared.scenes?.let { PreloadedScenes(it, prepared.scenesComplete) },
	)

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
				) { renderPaginated(userId, projectDef, page, wordsPerPage, sceneFilter, preloaded) }
			} else {
				renderPaginated(userId, projectDef, page, wordsPerPage, sceneFilter, preloaded).result
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
					estimatedReadingTimeMinutes = 1
				),
				complete = complete,
			)
		}

		val selected = walkScenes(scenes).scenes
			.let { ordered -> if (sceneFilter == null) ordered else ordered.filter { it.id in sceneFilter } }

		val processedScenes = selected.map { scene ->
			ProcessedScene(
				scene = scene,
				wordCount = countWords(scene.content),
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
	 * siblings by [ApiProjectEntity.SceneEntity.order], with orphans last.
	 */
	private fun walkScenes(scenes: List<ApiProjectEntity.SceneEntity>): SceneWalk {
		val chapters = collectChapters(scenes)
		return SceneWalk(
			scenes = chapters.flatMap { it.scenes },
			orphans = chapters.filter { it.orphaned }.flatMap { it.scenes },
		)
	}

	/** Appends the leaf scenes under [parentId] to [out], depth first, siblings by order. */
	private fun appendLeaves(
		parentId: Int,
		scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>>,
		visited: MutableSet<Int>,
		out: MutableList<ApiProjectEntity.SceneEntity>,
	) {
		val children = scenesByParent[parentId] ?: return
		for (scene in children.sortedBy { it.order }) {
			// A path cycle would otherwise recurse forever.
			if (!visited.add(scene.id)) continue
			if (scene.sceneType == ApiSceneType.Scene) {
				out += scene
			} else {
				appendLeaves(scene.id, scenesByParent, visited, out)
			}
		}
	}

	/**
	 * Splits the story into chapters the way the app's reader does: each top-level node is one
	 * chapter, a top-level group flattened to its leaf scenes and a top-level scene standing alone.
	 * Scenes the root can't reach follow as chapters of their own so nothing the author wrote goes
	 * missing.
	 */
	private fun collectChapters(scenes: List<ApiProjectEntity.SceneEntity>): List<CollectedChapter> {
		val byId = scenes.associateBy { it.id }
		val scenesByParent = scenes.groupBy { it.path.lastOrNull() ?: ROOT_KEY }
		val visited = mutableSetOf<Int>()
		val chapters = mutableListOf<CollectedChapter>()

		fun addChapter(top: ApiProjectEntity.SceneEntity, orphaned: Boolean) {
			if (!visited.add(top.id)) return
			val leaves = if (top.sceneType == ApiSceneType.Scene) {
				listOf(top)
			} else {
				mutableListOf<ApiProjectEntity.SceneEntity>()
					.also { appendLeaves(top.id, scenesByParent, visited, it) }
			}
			if (!orphaned || leaves.isNotEmpty()) chapters += CollectedChapter(top.name, leaves, orphaned)
		}

		scenesByParent[ROOT_ID].orEmpty().sortedBy { it.order }.forEach { addChapter(it, orphaned = false) }

		// An unreachable scene stays with the outermost group still above it, so a group whose
		// parent is missing reads as one chapter rather than one per scene.
		for (scene in scenes) {
			if (scene.sceneType != ApiSceneType.Scene || scene.id in visited) continue
			outermostUnvisitedGroup(scene, byId, visited)?.let { addChapter(it, orphaned = true) }
			addChapter(scene, orphaned = true)
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

	private class CollectedChapter(
		val title: String,
		val scenes: List<ApiProjectEntity.SceneEntity>,
		val orphaned: Boolean,
	)

	private fun chapterEntries(chapters: List<CollectedChapter>): List<StoryChapter> =
		chapters.mapIndexed { index, chapter ->
			StoryChapter(number = index + 1, title = chapter.title, sceneCount = chapter.scenes.size)
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
	suspend fun storyOverview(userId: Long, projectId: ProjectId): StoryOverviewResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return StoryOverviewResult.ProjectNotFound

		return try {
			val loaded = loadAllScenes(userId, projectDef)
			val chapters = collectChapters(loaded.scenes)
			StoryOverviewResult.Success(
				StoryOverview(
					projectName = projectDef.name,
					chapters = chapterEntries(chapters),
					sceneCount = chapters.sumOf { it.scenes.size },
					totalWordCount = chapters.sumOf { chapter -> chapter.scenes.sumOf { countWords(it.content) } },
					complete = loaded.complete,
				)
			)
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			StoryOverviewResult.Error(e.message ?: "Unknown error occurred")
		}
	}

	/**
	 * Renders chapter [chapterNumber] (1-based, coerced into range) one scene at a time. Scene and
	 * chapter names are left out of the HTML; the page renders them itself. Nothing is word
	 * counted: the reader shows no counts, and counting a whole manuscript per page turn is costly.
	 */
	suspend fun renderChapter(userId: Long, projectId: ProjectId, chapterNumber: Int): ChapterRenderResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return ChapterRenderResult.ProjectNotFound

		return try {
			val loaded = loadAllScenes(userId, projectDef)
			val chapters = collectChapters(loaded.scenes)
			val entries = chapterEntries(chapters)
			if (chapters.isEmpty()) {
				return ChapterRenderResult.Success(
					ChapterRender(projectDef.name, entries, chapter = null, scenes = emptyList(), loaded.complete)
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
			ChapterRenderResult.Success(
				ChapterRender(projectDef.name, entries, entries[index], scenes, loaded.complete)
			)
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			ChapterRenderResult.Error(e.message ?: "Unknown error occurred")
		}
	}

	/**
	 * The story's leaf scenes in reading order. [orphans] are the ones the walk couldn't reach,
	 * a scene whose parent group is missing; they land at the end of [scenes] so that nothing the
	 * author wrote silently disappears from a render.
	 */
	private class SceneWalk(
		val scenes: List<ApiProjectEntity.SceneEntity>,
		val orphans: List<ApiProjectEntity.SceneEntity>,
	)

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
	suspend fun getSceneHierarchy(
		userId: Long,
		projectId: ProjectId
	): SceneHierarchyResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return SceneHierarchyResult.ProjectNotFound

		return try {
			val hierarchyItems = buildSceneHierarchy(loadAllScenes(userId, projectDef).scenes)

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
					"## ${targetScene.name}\n\n${targetScene.content.trimEnd()}\n"
				} else {
					"## ${targetScene.name}\n"
				}
				content to countWords(targetScene.content)
			} else {
				// Group - collect all child scenes' content
				buildGroupMarkdown(targetScene, scenesByParent)
			}

			val html = markdownRenderer.markdownToSafeHtml(markdown, preserveLineBreaks = true, images = true)

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
		builder.append("## ${group.name}\n\n")
		val wordCount = writeGroupChildren(builder, group.id, scenesByParent)

		return builder.toString() to wordCount
	}

	companion object {
		/** Bucket for a scene with no path at all; distinct from the real root, which is id 0. */
		private const val ROOT_KEY = -1
		private const val ROOT_ID = 0
		const val DEFAULT_WORDS_PER_PAGE = 2000
		private val logger = LoggerFactory.getLogger(StoryRendererService::class.java)
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
	/** Zero for a story with no words; otherwise rounded up to at least a minute. */
	val readingTimeMinutes: Int
		get() = if (totalWordCount == 0) 0 else WordCountUtils.estimateReadingTimeMinutes(totalWordCount)
}

sealed class StoryOverviewResult {
	data class Success(val overview: StoryOverview) : StoryOverviewResult()
	data object ProjectNotFound : StoryOverviewResult()
	data class Error(val message: String) : StoryOverviewResult()
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

sealed class ChapterRenderResult {
	data class Success(val render: ChapterRender) : ChapterRenderResult()
	data object ProjectNotFound : ChapterRenderResult()
	data class Error(val message: String) : ChapterRenderResult()
}

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
