package com.darkrockstudios.apps.hammer.utilities

import java.nio.file.Path
import kotlin.time.Duration

/** A disk cache that can reclaim entries older than a given age. */
fun interface PrunableCache {
	/** Evict entries not used within [maxAge], then enforce the cache's size bound. */
	fun prune(maxAge: Duration)
}

/**
 * Where a regenerable disk cache named [name] lives.
 *
 * These caches hold real files, so unlike the rest of the server's storage they can't be pointed at
 * a fake filesystem. [CACHE_ROOT_PROPERTY] lets a test redirect them to a temp directory instead of
 * scribbling in the developer's home.
 */
fun cacheDirectory(name: String): Path =
	Path.of(
		System.getProperty(CACHE_ROOT_PROPERTY) ?: System.getProperty("user.home"),
		DATA_DIR,
		"cache",
		name,
	)

const val CACHE_ROOT_PROPERTY = "hammer.cacheRoot"
