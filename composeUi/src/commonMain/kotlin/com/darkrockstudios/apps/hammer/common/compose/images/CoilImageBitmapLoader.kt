package com.darkrockstudios.apps.hammer.common.compose.images

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult

/**
 * Decodes through Coil, keyed in its memory cache as [rememberImageRequest] keys a file, so evicting
 * a replaced file drops these decodes too. Large images are decoded down to [maxSize] on their
 * longer side: the editor never draws one wider than the page.
 */
class CoilImageBitmapLoader(
	private val imageLoader: ImageLoader,
	private val context: PlatformContext,
	private val maxSize: Int = MAX_EDITOR_IMAGE_SIZE,
) : ImageBitmapLoader {
	override suspend fun load(path: String, version: String): ImageBitmap? {
		val request = ImageRequest.Builder(context)
			.data(path)
			.memoryCacheKeyExtras(mapOf("version" to version))
			.size(maxSize)
			.build()
		val result = imageLoader.execute(request) as? SuccessResult ?: return null
		return result.image.toImageBitmap()
	}

	companion object {
		const val MAX_EDITOR_IMAGE_SIZE = 2048
	}
}

/** Converts a decoded Coil image to a Compose bitmap. */
expect fun Image.toImageBitmap(): ImageBitmap
