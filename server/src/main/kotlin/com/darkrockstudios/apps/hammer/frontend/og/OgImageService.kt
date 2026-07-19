package com.darkrockstudios.apps.hammer.frontend.og

import com.darkrockstudios.apps.hammer.utilities.LruDiskCache
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Renders OpenGraph share cards through a size-bounded disk cache, so a viral link's scraper
 * traffic renders each card at most once. Cache keys embed the entity's mutable fields, so a
 * renamed story or author regenerates automatically; bump [TEMPLATE_VERSION] to invalidate every
 * card after a design change.
 */
class OgImageService(
	private val renderer: OgImageRenderer,
	cacheDirectory: Path,
	maxCacheBytes: Long = DEFAULT_MAX_BYTES,
) {
	private val cache = LruDiskCache(cacheDirectory, maxCacheBytes)

	fun authorCard(accountId: Long, penName: String): ByteArray =
		cache.getOrPut("author:$TEMPLATE_VERSION:$accountId:$penName") {
			renderer.render(penName, "An author on Hammer")
		}

	fun storyCard(projectUuid: String, projectName: String, penName: String): ByteArray =
		cache.getOrPut("story:$TEMPLATE_VERSION:$projectUuid:$projectName:$penName") {
			renderer.render(projectName, "by $penName")
		}

	/** Evict cards not requested within [maxAge], then enforce the size bound. */
	fun prune(maxAge: Duration) = cache.prune(maxAge)

	private companion object {
		const val TEMPLATE_VERSION = "v1"
		const val DEFAULT_MAX_BYTES = 200L * 1024 * 1024
	}
}
