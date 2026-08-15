package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.CacheConfig
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.utilities.LruDiskCache
import com.darkrockstudios.apps.hammer.utilities.PrunableCache
import com.darkrockstudios.apps.hammer.utilities.TouchableFileSystem
import kotlinx.serialization.json.Json
import okio.Path
import kotlin.time.Duration

/**
 * Caches rendered story pages on disk, so a story that hasn't changed is turned into HTML once
 * rather than once per reader. Rendering a page decrypts and deserializes every scene in the
 * project, word-counts them to work out pagination, then parses and sanitizes markdown.
 *
 * Keys are built from the render's inputs — the project name and every scene's stored content hash
 * — so a synced edit, rename, reorder, or deletion lands on a different key and renders fresh. The
 * superseded entry is simply orphaned, then reclaimed by size eviction or [prune]. Bump
 * [RENDER_VERSION] to invalidate everything after a change to how markdown becomes HTML.
 */
class StoryRenderCache(
	fileSystem: TouchableFileSystem,
	cacheDirectory: Path,
	maxCacheBytes: Long = CacheConfig().maxSizeBytes,
) : PrunableCache {
	private val cache = LruDiskCache(fileSystem, cacheDirectory, maxCacheBytes)
	private val json = Json { ignoreUnknownKeys = true }

	/**
	 * Serve this page from the cache, or [render] it. A render marked
	 * [StoryRender.complete]`= false` is returned to the caller but never stored — a page missing
	 * scenes that failed to load must not outlive the failure that produced it.
	 */
	suspend fun getOrRender(
		projectId: ProjectId,
		projectName: String,
		page: Int,
		wordsPerPage: Int,
		sceneHashes: List<EntityHash>,
		render: suspend () -> StoryRender,
	): PaginatedStoryExportResult {
		val key = buildKey(projectId, projectName, page, wordsPerPage, sceneHashes)

		var rendered: StoryRender? = null
		val cached = cache.getOrPutSuspending(key) {
			render().also { rendered = it }.let { if (it.complete) encode(it.result) else null }
		}

		// Rendered on this call — return it whether or not it was worth storing.
		rendered?.let { return it.result }
		// Otherwise it came from disk; a corrupt entry falls back to a fresh render.
		return cached?.let { decode(it) } ?: render().result
	}

	/** Evict renders not requested within [maxAge], then enforce the size bound. */
	override fun prune(maxAge: Duration) = cache.prune(maxAge)

	private fun buildKey(
		projectId: ProjectId,
		projectName: String,
		page: Int,
		wordsPerPage: Int,
		sceneHashes: List<EntityHash>,
	): String =
		"story-html:$RENDER_VERSION:${projectId.id}:$page:$wordsPerPage:${fingerprint(projectName, sceneHashes)}"

	private fun encode(result: PaginatedStoryExportResult): ByteArray =
		json.encodeToString(result).toByteArray(Charsets.UTF_8)

	// A truncated or hand-mangled file is a miss, not an error; the caller re-renders.
	private fun decode(bytes: ByteArray): PaginatedStoryExportResult? =
		runCatching { json.decodeFromString<PaginatedStoryExportResult>(bytes.toString(Charsets.UTF_8)) }
			.getOrNull()

	companion object {
		private const val RENDER_VERSION = "v4"

		/**
		 * Everything a render of this story depends on, collapsed to a string: the title heading
		 * and every scene's stored content hash. Doubles as the HTTP validator for story pages.
		 *
		 * The name is length-prefixed so one containing the delimiters can't imitate a scene list.
		 */
		fun fingerprint(projectName: String, sceneHashes: List<EntityHash>): String =
			"${projectName.length}:$projectName|" + sceneHashes.joinToString(",") { "${it.id}:${it.hash}" }
	}
}

/**
 * A rendered story page, plus whether it is complete enough to cache. An incomplete render is one
 * whose scenes didn't all load — still worth serving, never worth keeping.
 */
data class StoryRender(
	val result: PaginatedStoryExportResult,
	val complete: Boolean,
)
