package com.darkrockstudios.apps.hammer.common.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

/** BitmapFactory ignores EXIF orientation, so [orientation] is applied by the matrix. */
internal actual fun transcodeToPng(bytes: ByteArray, orientation: Int, maxEdge: Int): ByteArray? {
	val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
	BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
	if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

	val longest = maxOf(bounds.outWidth, bounds.outHeight)
	var sampleSize = 1
	while (longest / (sampleSize * 2) >= maxEdge) sampleSize *= 2
	val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
	val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

	val target = transcodeSize(decoded.width, decoded.height, orientation, maxEdge)
	val matrix = Matrix().apply { setValues(orientationMatrix(decoded.width, decoded.height, orientation, target)) }
	return try {
		// Returns decoded itself when the matrix changes nothing.
		val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
		try {
			val out = ByteArrayOutputStream()
			if (upright.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
		} finally {
			if (upright !== decoded) upright.recycle()
		}
	} finally {
		decoded.recycle()
	}
}
