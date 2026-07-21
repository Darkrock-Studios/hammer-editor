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

/**
 * Where the regenerable disk cache named [name] lives: under [CacheConfig.directory] when the
 * operator configured one, otherwise `cache/` in the server's data directory.
 */
fun cacheDirectory(config: CacheConfig, fileSystem: FileSystem, name: String): Path =
	cacheRoot(config, fileSystem) / name

fun cacheRoot(config: CacheConfig, fileSystem: FileSystem): Path =
	config.directory?.toPath() ?: (getRootDataDirectory(fileSystem) / DEFAULT_CACHE_DIR_NAME)

private const val DEFAULT_CACHE_DIR_NAME = "cache"
