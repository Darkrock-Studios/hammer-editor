package com.darkrockstudios.apps.hammer.base.image

import com.darkrockstudios.apps.hammer.base.validate.FilenameEncoding
import com.darkrockstudios.apps.hammer.base.validate.ProjectNameValidationResult
import com.darkrockstudios.apps.hammer.base.validate.ProjectNameValidator
import korlibs.crypto.encoding.Base64

/** Why [ImageFormat.validate] rejected an image. */
enum class ImageProblem {
	InvalidName,
	UnsupportedType,
	TooLarge,

	/** The bytes are not an image of the declared extension. */
	ContentMismatch,
}

/** Format rules for Image entities, shared by the client and the server. */
object ImageFormat {
	/** Lowercase file extensions an Image entity may have. */
	val ALLOWED_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "gif", "svg")

	const val MAX_IMAGE_MB: Int = 8
	const val MAX_IMAGE_BYTES: Long = MAX_IMAGE_MB * 1024L * 1024

	/** How much of a file to decode when looking for the SVG root element past the XML prolog. */
	private const val SVG_SNIFF_WINDOW = 1024

	fun isAllowedExtension(extension: String): Boolean = extension.lowercase() in ALLOWED_EXTENSIONS

	/** The media type an image of [extension] is served as, or null for an extension Hammer does not allow. */
	fun mimeType(extension: String): String? = when (extension.lowercase()) {
		"jpg", "jpeg" -> "image/jpeg"
		"png" -> "image/png"
		"webp" -> "image/webp"
		"gif" -> "image/gif"
		"svg" -> "image/svg+xml"
		else -> null
	}

	/**
	 * A name the client can store as a filename and read back unchanged. Anything else would hash
	 * differently once read back from disk, so the server rejects it too.
	 */
	fun isValidName(name: String): Boolean =
		ProjectNameValidator.validate(name, usedAsRawFilename = false) == ProjectNameValidationResult.VALID &&
			FilenameEncoding.roundTrips(name)

	/** The first rule an image breaks, or null when it may be stored. [bytes] are the decoded image. */
	fun validate(name: String, fileExtension: String, bytes: ByteArray): ImageProblem? = when {
		!isValidName(name) -> ImageProblem.InvalidName
		!isAllowedExtension(fileExtension) -> ImageProblem.UnsupportedType
		bytes.size > MAX_IMAGE_BYTES -> ImageProblem.TooLarge
		!sniff(bytes, fileExtension) -> ImageProblem.ContentMismatch
		else -> null
	}

	/** Decodes the URL-safe base64 wire form of an image, or null when it is not valid base64. */
	@Suppress("TooGenericExceptionCaught", "SwallowedException")
	fun decodeBase64(base64: String): ByteArray? = try {
		Base64.decode(base64, url = true)
	} catch (_: Exception) {
		null
	}

	/** Whether [bytes] start the way an image of [extension] does. */
	fun sniff(bytes: ByteArray, extension: String): Boolean {
		fun startsWith(offset: Int, vararg signature: Int) =
			bytes.size >= offset + signature.size &&
				signature.indices.all { bytes[offset + it] == signature[it].toByte() }
		return when (extension.lowercase()) {
			"png" -> startsWith(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
			"jpg", "jpeg" -> startsWith(0, 0xFF, 0xD8, 0xFF)
			"webp" -> startsWith(0, 0x52, 0x49, 0x46, 0x46) && startsWith(8, 0x57, 0x45, 0x42, 0x50)
			"gif" -> startsWith(0, 0x47, 0x49, 0x46, 0x38)
			"svg" -> isSvg(bytes)
			else -> false
		}
	}

	/** The root element must be `<svg`, after an optional BOM, XML declaration, comments and DOCTYPE. */
	private fun isSvg(bytes: ByteArray): Boolean {
		var text = bytes.copyOf(minOf(bytes.size, SVG_SNIFF_WINDOW)).decodeToString().removePrefix("\uFEFF")
		while (true) {
			text = text.trimStart()
			val rest = when {
				text.startsWith("<?") -> after(text, "?>")
				text.startsWith("<!--") -> after(text, "-->")
				text.startsWith("<!DOCTYPE", ignoreCase = true) -> afterDoctype(text)
				else -> return text.startsWith("<svg")
			} ?: return false
			text = rest
		}
	}

	private fun after(text: String, terminator: String): String? {
		val end = text.indexOf(terminator)
		return if (end < 0) null else text.substring(end + terminator.length)
	}

	// An internal subset (`<!DOCTYPE svg [ ... ]>`) may itself contain `>`.
	private fun afterDoctype(text: String): String? {
		val close = text.indexOf('>')
		val subset = text.indexOf('[')
		return if (subset in 0..<close) {
			after(text.substring(subset), "]")?.let { after(it, ">") }
		} else {
			after(text, ">")
		}
	}
}
