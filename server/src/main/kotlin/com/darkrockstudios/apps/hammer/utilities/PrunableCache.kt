package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.CacheConfig
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Duration

/** A disk cache that can reclaim entries older than a given age. */
fun interface PrunableCache {
	/** Evict entries not used within [maxAge], then enforce the cache's size bound. */
	fun prune(maxAge: Duration)
}

/** The regenerable disk caches, each in its own subdirectory of the cache root. */
enum class DiskCache(val dirName: String) {
	OG_IMAGES("og"),
	STORY_HTML("story-html"),
}

/**
 * Where [cache] stores its entries: under [CacheConfig.directory] when the operator configured
 * one, otherwise `cache/` in the server's data directory.
 */
fun cacheDirectory(config: CacheConfig, fileSystem: FileSystem, cache: DiskCache): Path =
	cacheRoot(config, fileSystem) / cache.dirName

/** The directory holding every cache's subdirectory. */
fun cacheRoot(config: CacheConfig, fileSystem: FileSystem): Path =
	config.directory?.toPath() ?: (getRootDataDirectory(fileSystem) / DEFAULT_CACHE_DIR_NAME)

private const val DEFAULT_CACHE_DIR_NAME = "cache"
