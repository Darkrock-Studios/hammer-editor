package com.darkrockstudios.apps.hammer.common.compose.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A request for the image file at [path]. Coil keys a file by its path alone, so the content
 * [hash] goes into the memory cache key: a file rewritten in place gets a fresh entry.
 *
 * [placeholderFromPath] uses the path's un-hashed memory cache entry as the placeholder.
 * [crossfadeMillis] of null keeps the image loader's default; 0 disables it.
 */
@Composable
fun rememberImageRequest(
	path: String,
	hash: String?,
	placeholderFromPath: Boolean = false,
	crossfadeMillis: Int? = null,
): ImageRequest {
	val context = LocalPlatformContext.current
	return remember(context, path, hash, placeholderFromPath, crossfadeMillis) {
		ImageRequest.Builder(context)
			.data(path)
			.memoryCacheKeyExtras(mapOf("hash" to hash.toString()))
			.apply {
				if (placeholderFromPath) placeholderMemoryCacheKey(path)
				if (crossfadeMillis != null) crossfade(crossfadeMillis)
			}
			.build()
	}
}

/**
 * Drops every cached decode of the image file at [path]: all hashes and sizes, and the viewer's
 * full-screen entries. Call it once the file has been replaced or deleted.
 */
fun ImageLoader.evictImageFile(path: String) {
	memoryCache?.let { cache ->
		val viewerPrefix = "$VIEWER_CACHE_KEY_PREFIX$path#"
		cache.keys
			.filter { it.key == path || it.key.startsWith(viewerPrefix) }
			.forEach { cache.remove(it) }
	}
	diskCache?.remove(path)
}

/** Prefixes the memory cache keys of the image viewer's full-screen decodes. */
internal const val VIEWER_CACHE_KEY_PREFIX = "image-viewer:"
