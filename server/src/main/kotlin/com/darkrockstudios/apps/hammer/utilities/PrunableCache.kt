package com.darkrockstudios.apps.hammer.utilities

import okio.FileSystem
import okio.Path
import kotlin.time.Duration

/** A disk cache that can reclaim entries older than a given age. */
fun interface PrunableCache {
	/** Evict entries not used within [maxAge], then enforce the cache's size bound. */
	fun prune(maxAge: Duration)
}

/** Where the regenerable disk cache named [name] lives, under the server's data directory. */
fun cacheDirectory(fileSystem: FileSystem, name: String): Path =
	getRootDataDirectory(fileSystem) / "cache" / name
