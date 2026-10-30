package data.export

import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** An opaque orange image written by ImageIO in [format]; opaque, so a PDF holds no soft mask for it. */
internal fun encoded(format: String, width: Int = 40, height: Int = 20): ByteArray {
	val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
	image.createGraphics().apply {
		color = java.awt.Color.ORANGE
		fillRect(0, 0, width, height)
		dispose()
	}
	return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
}

/** An opaque orange WebP, which ImageIO cannot write. */
internal fun webp(width: Int = 40, height: Int = 20): ByteArray {
	val surface = Surface.makeRasterN32Premul(width, height)
	surface.canvas.clear(Color.makeRGB(255, 160, 0))
	return checkNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.WEBP)).bytes
}

/** [jpeg] with a big-endian EXIF APP1 segment carrying [orientation] spliced in after SOI. */
internal fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
	val tiff = byteArrayOf(
		'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8,
		0, 1,
		0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0,
		0, 0, 0, 0,
	)
	val payload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
	val length = payload.size + 2
	val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
	return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
}
