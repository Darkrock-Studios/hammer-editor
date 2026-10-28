package com.darkrockstudios.apps.hammer.common.compose.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
