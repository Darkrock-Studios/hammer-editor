package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.common.util.toByteArray
import com.darkrockstudios.apps.hammer.common.util.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

/**
 * UIImage reads a JPEG's EXIF orientation into its `imageOrientation` and its `size`, and drawing it
 * applies that, so [orientation] is already honoured here.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun transcodeToPng(bytes: ByteArray, orientation: Int, maxEdge: Int): ByteArray? {
	if (bytes.isEmpty()) return null
	val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
	val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
	if (width < 1.0 || height < 1.0) return null

	val target = transcodeSize(width.toInt(), height.toInt(), orientation = 1, maxEdge)
	val format = UIGraphicsImageRendererFormat().apply {
		scale = 1.0
		// Keeps an opaque image's PNG free of an alpha channel, which pdfkmp would embed as a soft mask.
		opaque = image.CGImage?.let { CGImageGetAlphaInfo(it) in OPAQUE_ALPHA } ?: false
	}
	val renderer = UIGraphicsImageRenderer(
		size = CGSizeMake(target.width.toDouble(), target.height.toDouble()),
		format = format,
	)
	val upright = renderer.imageWithActions {
		image.drawInRect(CGRectMake(0.0, 0.0, target.width.toDouble(), target.height.toDouble()))
	}
	return UIImagePNGRepresentation(upright)?.toByteArray()?.takeIf { it.isNotEmpty() }
}

private val OPAQUE_ALPHA = setOf(
	CGImageAlphaInfo.kCGImageAlphaNone,
	CGImageAlphaInfo.kCGImageAlphaNoneSkipLast,
	CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst,
)
