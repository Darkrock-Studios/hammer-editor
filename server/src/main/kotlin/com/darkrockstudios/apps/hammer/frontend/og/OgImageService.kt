package com.darkrockstudios.apps.hammer.frontend.og

import com.darkrockstudios.apps.hammer.utilities.LruDiskCache
import com.darkrockstudios.apps.hammer.utilities.PrunableCache
import okio.FileSystem
import okio.Path
import kotlin.time.Duration

/**
 * Renders OpenGraph share cards through a size-bounded disk cache, so a viral link's scraper
 * traffic renders each card at most once. Cache keys are built from the exact render inputs — so a
 * renamed story, a re-titled author, a different language, or a different server host all
 * regenerate automatically (the localized labels and host are part of the key). Bump
 * [TEMPLATE_VERSION] to invalidate every card after a design change.
 */
class OgImageService(
	private val renderer: OgImageRenderer,
	fileSystem: FileSystem,
	cacheDirectory: Path,
	maxCacheBytes: Long = DEFAULT_MAX_BYTES,
) : PrunableCache {
	private val cache = LruDiskCache(fileSystem, cacheDirectory, maxCacheBytes)

	fun authorCard(accountId: Long, penName: String, subtitle: String): ByteArray =
		cache.getOrPut("author:$TEMPLATE_VERSION:$accountId:$penName:$subtitle") {
			renderer.render(penName, subtitle)
		}

	fun storyCard(
		projectUuid: String,
		projectName: String,
		penName: String,
		kicker: String,
		attribution: String,
	): ByteArray =
		cache.getOrPut("story:$TEMPLATE_VERSION:$projectUuid:$projectName:$penName:$kicker:$attribution") {
			renderer.renderStoryCard(projectName, penName, kicker, attribution)
		}

	/** Evict cards not requested within [maxAge], then enforce the size bound. */
	override fun prune(maxAge: Duration) = cache.prune(maxAge)

	private companion object {
		const val TEMPLATE_VERSION = "v1"
		const val DEFAULT_MAX_BYTES = 200L * 1024 * 1024
	}
}
