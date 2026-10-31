package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.util.zip.InMemoryZipEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.io.encoding.Base64

/** The zip format's limit on an entry name, in UTF-8 bytes. */
private const val MAX_ARCHIVE_PATH_BYTES = 65535

/** How many characters a refusal quotes from a path too long to accept. */
private const val QUOTED_LONG_PATH_CHARS = 64

/**
 * The files an exporter with `output = "archive"` replied with, in order, ready to zip. The whole
 * reply is checked before any entry is returned, so a refused one leaves nothing half written.
 *
 * @param images the images sent in the request, by id: the only ones an `image` entry may name
 * @throws ArchiveRefusal when the reply is not an archive reply, or breaks one of its rules
 */
internal fun archiveEntries(reply: ByteArray, images: Map<Int, ExportImage>): List<InMemoryZipEntry> {
	val files = try {
		WasmPlugin.ReplyJson.decodeFromString<ArchiveReply>(reply.decodeToString()).files
	} catch (e: IllegalArgumentException) {
		throw ArchiveRefusal.Malformed(e.message)
	}
	fun refuse(problem: String): Nothing = throw ArchiveRefusal.Invalid(problem)
	if (files.isEmpty()) refuse("An archive needs at least one file")
	val paths = hashSetOf<String>()
	return files.map { file ->
		val path = file.path
		val pathBytes = path.encodeToByteArray()
		if (pathBytes.size > MAX_ARCHIVE_PATH_BYTES) {
			refuse("Archive path \"${path.take(QUOTED_LONG_PATH_CHARS)}...\" is longer than $MAX_ARCHIVE_PATH_BYTES bytes")
		}
		archivePathRefusal(path)?.let { refuse("Archive path \"$path\" $it") }
		// An unpaired surrogate encodes as ?, so two such paths could otherwise pass as different and zip as one name.
		if (pathBytes.decodeToString() != path) refuse("Archive path \"$path\" is not valid Unicode")
		if (!paths.add(path)) refuse("Archive path \"$path\" is already in the archive")
		when {
			listOfNotNull(file.text, file.content, file.image).size > 1 ->
				refuse("Archive path \"$path\" has more than one of text, content and image")
			file.text != null -> InMemoryZipEntry(path, file.text.encodeToByteArray())
			file.content != null -> {
				val bytes = try {
					Base64.decode(file.content)
				} catch (_: IllegalArgumentException) {
					refuse("Archive path \"$path\" has content that is not standard padded base64")
				}
				InMemoryZipEntry(path, bytes)
			}
			file.image != null -> {
				val image = images[file.image]
					?: refuse("Archive path \"$path\" names image ${file.image}, which was not sent")
				InMemoryZipEntry(path, image.bytes)
			}
			else -> refuse("Archive path \"$path\" has none of text, content and image")
		}
	}
}

/** Why [archiveEntries] refused a reply, as the tail of the message the user sees. */
internal sealed class ArchiveRefusal(message: String?) : Exception(message) {
	/** The reply is not an archive reply at all. */
	class Malformed(detail: String?) : ArchiveRefusal(detail)

	/** The reply breaks one of the archive rules. */
	class Invalid(problem: String) : ArchiveRefusal(problem)
}

// The rules the plugin kits check before sending an archive, worded as they word them.
private fun archivePathRefusal(path: String): String? = when {
	path.isEmpty() -> "is empty"
	path.startsWith('/') -> "starts with /"
	'\\' in path -> "has a backslash"
	else -> path.split('/').firstNotNullOfOrNull { segment ->
		when (segment) {
			"" -> "has an empty segment"
			".", ".." -> "has a $segment segment"
			else -> null
		}
	}
}

/**
 * What an `archive` exporter replies: [files] in the order to zip them, each with exactly one of
 * [File.text], written as UTF-8, [File.content], its bytes as standard padded base64, or
 * [File.image], the id of a sent image whose bytes it is.
 */
@Serializable
private class ArchiveReply(val files: List<File>) {
	@Serializable
	class File(
		val path: String,
		val text: String? = null,
		val content: String? = null,
		@Serializable(with = ImageIdSerializer::class)
		val image: Int? = null,
	)
}

// Json reads a quoted number as a number, so without this an id sent as a string would pass.
private object ImageIdSerializer : KSerializer<Int> {
	override val descriptor = PrimitiveSerialDescriptor("ArchiveImageId", PrimitiveKind.INT)

	override fun deserialize(decoder: Decoder): Int {
		val element = (decoder as JsonDecoder).decodeJsonElement()
		return (element as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
			?: throw SerializationException("An archive image is an integer id, not $element")
	}

	override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
