package com.darkrockstudios.apps.hammer.common.data.export

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/**
 * Skia applies a JPEG's EXIF orientation as it decodes, so [orientation] is already honoured here.
 * A missing Skia native runtime surfaces as a [LinkageError]; the image then falls back to its name.
 */
@Suppress("SwallowedException", "TooGenericExceptionCaught")
internal actual fun transcodeToPng(bytes: ByteArray, orientation: Int, maxEdge: Int): ByteArray? = try {
	Image.makeFromEncoded(bytes).use { image ->
		val target = transcodeSize(image.width, image.height, orientation = 1, maxEdge)
		// An opaque surface keeps an opaque image's PNG free of an alpha channel, which pdfkmp
		// would embed as a soft mask.
		val alpha = if (image.imageInfo.isOpaque) ColorAlphaType.OPAQUE else ColorAlphaType.PREMUL
		Surface.makeRaster(ImageInfo.makeN32(target.width, target.height, alpha)).use { surface ->
			surface.canvas.drawImageRect(
				image,
				Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
				Rect.makeWH(target.width.toFloat(), target.height.toFloat()),
				SamplingMode.LINEAR,
				null,
				true,
			)
			surface.makeImageSnapshot().use { snapshot ->
				snapshot.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes }
			}
		}
	}
} catch (_: Exception) {
	null
} catch (_: LinkageError) {
	null
}
