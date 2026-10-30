package com.darkrockstudios.apps.hammer.common.data.export

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The longest edge a transcoded image is decoded to. PDF, DOCX and RTF embed an image at most about
 * 4.5 inches wide (and 7 tall), so this keeps close to 300 dpi or better while bounding decode memory.
 */
internal const val MAX_TRANSCODE_EDGE = 2000

/**
 * Re-encodes a raster image the platform can decode as PNG, upright per its EXIF [orientation] and
 * shrunk so its longer edge is at most [maxEdge]. Null when the platform cannot decode [bytes].
 */
internal expect fun transcodeToPng(
	bytes: ByteArray,
	orientation: Int = 1,
	maxEdge: Int = MAX_TRANSCODE_EDGE,
): ByteArray?

/** Pixel size of a transcoded image; [width] and [height] are already upright. */
internal data class TranscodeSize(val width: Int, val height: Int)

/** The upright size of a [width] by [height] image in EXIF [orientation], shrunk to fit [maxEdge]. */
internal fun transcodeSize(width: Int, height: Int, orientation: Int, maxEdge: Int): TranscodeSize {
	val (uprightWidth, uprightHeight) = if (swapsEdges(orientation)) height to width else width to height
	val scale = minOf(1f, maxEdge.toFloat() / max(uprightWidth, uprightHeight))
	return TranscodeSize(
		width = (uprightWidth * scale).roundToInt().coerceAtLeast(1),
		height = (uprightHeight * scale).roundToInt().coerceAtLeast(1),
	)
}

/**
 * The affine transform, as a row-major 3x3 matrix, that draws a [width] by [height] image stored in
 * EXIF [orientation] upright and scaled to fill [target].
 */
internal fun orientationMatrix(width: Int, height: Int, orientation: Int, target: TranscodeSize): FloatArray {
	val w = width.toFloat()
	val h = height.toFloat()
	// x' = a*x + b*y + tx, y' = c*x + d*y + ty, mapping stored pixels to upright ones.
	val (a, b, tx) = when (orientation) {
		2, 3 -> Triple(-1f, 0f, w)
		5, 8 -> Triple(0f, 1f, 0f)
		6, 7 -> Triple(0f, -1f, h)
		else -> Triple(1f, 0f, 0f)
	}
	val (c, d, ty) = when (orientation) {
		3, 4 -> Triple(0f, -1f, h)
		5, 6 -> Triple(1f, 0f, 0f)
		7, 8 -> Triple(-1f, 0f, w)
		else -> Triple(0f, 1f, 0f)
	}
	val uprightWidth = if (swapsEdges(orientation)) h else w
	val uprightHeight = if (swapsEdges(orientation)) w else h
	val sx = target.width / uprightWidth
	val sy = target.height / uprightHeight
	return floatArrayOf(
		a * sx, b * sx, tx * sx,
		c * sy, d * sy, ty * sy,
		0f, 0f, 1f,
	)
}

private fun swapsEdges(orientation: Int): Boolean = orientation in 5..8
