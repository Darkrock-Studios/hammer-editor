package com.darkrockstudios.apps.hammer.utilities

import kotlin.time.Duration

/** A disk cache that can reclaim entries older than a given age. */
fun interface PrunableCache {
	/** Evict entries not used within [maxAge], then enforce the cache's size bound. */
	fun prune(maxAge: Duration)
}
