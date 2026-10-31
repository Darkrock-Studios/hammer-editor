package com.darkrockstudios.apps.hammer.common.data.export

import com.conamobile.pdfkmp.image.readImageInfo
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.image.ImageFormat

/** The share of the text width an embedded image takes: the editor's `HammerImageBlockSize`. */
internal const val IMAGE_WIDTH_FRACTION = 0.7f

/** The share of the text height (the page height in PDF) a tall embedded image is shrunk to fit. */
internal const val MAX_IMAGE_HEIGHT_FRACTION = 0.6f

/** A project image as upright PNG or JPEG [bytes] an export can embed, with its pixel size. */
internal class ExportRaster(
	val bytes: ByteArray,
	val format: Format,
	val widthPx: Int,
	val heightPx: Int,
) {
	enum class Format(val extension: String, val mimeType: String) {
		Png("png", "image/png"),
		Jpeg("jpeg", "image/jpeg"),
	}

	/** Height over width. */
	val aspectRatio: Float get() = heightPx.toFloat() / widthPx
}

/**
 * [image] as an [ExportRaster]: PNG and JPEG as they are, WebP and a JPEG with an EXIF rotation or
 * flip re-encoded upright as PNG. Null for SVG, an extension Hammer does not allow, a file whose
 * header cannot be read or gives a side outside 1 to [MAX_RASTER_SIDE] pixels, or one that must be
 * re-encoded and does not decode. A PNG or JPEG whose header reads but whose body is corrupt still
 * gives a raster.
 */
internal fun exportRaster(image: ExportImage): ExportRaster? = when (ImageFormat.mimeType(image.fileExtension)) {
	"image/png", "image/jpeg" -> uprightRaster(image.bytes)
	"image/webp" -> transcodeToPng(image.bytes)?.let(::uprightRaster)
	else -> null
}

/**
 * The project cover from [images] as [decode] reads it, or null when the project has no cover, its
 * image is not in [images], or it does not decode. A format opens with a cover page only for a
 * non-null result.
 */
internal fun <T : Any> exportCover(
	projectData: ProjectData,
	images: Map<Int, ExportImage>,
	decode: (ExportImage) -> T?,
): T? = projectData.coverImageId?.let(images::get)?.let(decode)

/**
 * The widest or tallest raster an export embeds: JPEG's limit. A PNG header can claim any u32 side,
 * and `RtfPicture` throws on one over `Int.MAX_VALUE / 15`. The lower bound repeats pdfkmp's own
 * rejection of a zero side, so a size of nothing never reaches a renderer either way.
 */
internal const val MAX_RASTER_SIDE = 65535

private val RASTER_SIDES = 1..MAX_RASTER_SIDE

/** pdfkmp and some word processors draw a JPEG by its stored pixels, ignoring EXIF orientation. */
private fun uprightRaster(bytes: ByteArray): ExportRaster? {
	val orientation = jpegExifOrientation(bytes)
	val upright = if (orientation == 1) bytes else transcodeToPng(bytes, orientation) ?: return null
	// Only PNG and JPEG headers read, so the format follows from the first byte.
	val info = readImageInfo(upright)
		?.takeIf { it.widthPx in RASTER_SIDES && it.heightPx in RASTER_SIDES }
		?: return null
	val format = if (upright[0] == 0xFF.toByte()) ExportRaster.Format.Jpeg else ExportRaster.Format.Png
	return ExportRaster(upright, format, info.widthPx, info.heightPx)
}
