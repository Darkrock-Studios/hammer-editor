package com.darkrockstudios.apps.hammer.frontend.utils

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * [text] as a QR code in inline SVG, one module per user unit. Dark runs on each row are merged
 * into a single rect, which keeps an otpauth URI to a few hundred elements.
 */
fun qrCodeSvg(text: String, quietZone: Int = 4): String {
	val hints = mapOf(
		EncodeHintType.MARGIN to quietZone,
		EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
	)
	// Zero size asks for the smallest matrix: one bit per module, quiet zone included.
	val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
	val size = matrix.width

	return buildString {
		append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $size" """)
		append("""shape-rendering="crispEdges" class="qr-code" role="img">""")
		append("""<rect width="$size" height="$size" fill="#fff"/><g fill="#000">""")
		for (y in 0 until size) {
			var x = 0
			while (x < size) {
				if (!matrix[x, y]) {
					x++
					continue
				}
				val start = x
				while (x < size && matrix[x, y]) x++
				append("""<rect x="$start" y="$y" width="${x - start}" height="1"/>""")
			}
		}
		append("</g></svg>")
	}
}
