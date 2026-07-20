package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.utilities.LruDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
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
	cacheDirectory: Path,
	maxCacheBytes: Long = DEFAULT_MAX_BYTES,
) {
	private val cache = LruDiskCache(cacheDirectory, maxCacheBytes)
	private val json = Json { ignoreUnknownKeys = true }

	suspend fun getOrRender(
		projectId: ProjectId,
		projectName: String,
		page: Int,
		wordsPerPage: Int,
		sceneHashes: List<EntityHash>,
		render: suspend () -> PaginatedStoryExportResult,
	): PaginatedStoryExportResult {
		val key = buildKey(projectId, projectName, page, wordsPerPage, sceneHashes)

		val bytes = cache.getOrPutSuspending(key) { encode(render()) }
		decode(bytes)?.let { return it }

		return render().also { fresh ->
			withContext(Dispatchers.IO) { cache.put(key, encode(fresh)) }
		}
	}

	/** Evict renders not requested within [maxAge], then enforce the size bound. */
	fun prune(maxAge: Duration) = cache.prune(maxAge)

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
		private const val RENDER_VERSION = "v1"
		private const val DEFAULT_MAX_BYTES = 200L * 1024 * 1024

		/**
		 * Everything a render of this story depends on, collapsed to a string: the title heading
		 * and every scene's stored content hash. Doubles as the HTTP validator for story pages.
		 */
		fun fingerprint(projectName: String, sceneHashes: List<EntityHash>): String =
			"$projectName|" + sceneHashes.joinToString(",") { "${it.id}:${it.hash}" }
	}
}
