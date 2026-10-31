package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import com.darkrockstudios.apps.hammer.common.data.search.escapeMarkdown
import com.darkrockstudios.apps.hammer.common.data.search.escapeOrderedListMarkers
import com.darkrockstudios.apps.hammer.common.util.zip.InMemoryZipEntry
import com.darkrockstudios.apps.hammer.common.util.zip.writeZip
import okio.Buffer
import okio.BufferedSink

internal const val MARKDOWN_FILE_EXTENSION = "md"

/** The folder beside the `.md` that holds the images it shows. */
internal const val MARKDOWN_IMAGES_DIRECTORY = "images"

private const val DESTINATION_ENCODED = " ()<>%#?\"`\\"
private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * Renders the story as a zip of `<project>.md` and, under [MARKDOWN_IMAGES_DIRECTORY], the file of
 * each project image it shows, named as in the project ([ImagesDatasource.getFilename]) and with its
 * bytes unchanged. Each project image in the story points at its file, with the image's name as alt
 * text, and the cover leads the document. An image missing from [ExportInput.images] is written as
 * its name, and one with no name is dropped.
 */
fun writeStoryAsMarkdownZip(sink: BufferedSink, input: ExportInput) {
	// Image id to its path in the archive, in the order the document first shows them.
	val packaged = LinkedHashMap<Int, String>()
	fun imageMarkdown(image: ExportImage): String {
		val path = packaged.getOrPut(image.id) {
			val def = ImageDef(id = image.id, name = image.name, fileExtension = image.fileExtension)
			"$MARKDOWN_IMAGES_DIRECTORY/${ImagesDatasource.getFilename(def)}"
		}
		return "![${escapeMarkdown(image.name)}](${markdownDestination(path)})"
	}

	val cover = exportCover(input.requireProjectData(), input.images, ::imageMarkdown)
	// Rewritten as a whole, so a reference definition in one scene still serves a use in another.
	val story = Buffer().also {
		writeStoryAsMarkdown(
			sink = it,
			projectName = input.projectName,
			chapters = input.chapters,
			treatTopLevelAsChapters = input.treatTopLevelAsChapters,
			numberChapters = input.options.numberChapters,
		)
	}.readUtf8()
	val body = ImageReferenceSyntax.replaceImages(story) { imageId, _ ->
		input.images[imageId]?.let(::imageMarkdown)
			?: input.imageNames[imageId]?.let { escapeOrderedListMarkers(escapeMarkdown(it)) }
	}
	val markdown = (if (cover != null) "$cover\n\n$body" else body).encodeToByteArray()

	val documentName = exportFileName(input.projectName, MARKDOWN_FILE_EXTENSION).fileName
	val imageEntries = packaged.map { (imageId, path) ->
		InMemoryZipEntry(path, input.images.getValue(imageId).bytes, compress = false)
	}
	writeZip(sink, listOf(InMemoryZipEntry(documentName, markdown)) + imageEntries)
}

/**
 * [path] as a link destination, with each UTF-8 byte of a space, a control character, a backtick,
 * or one of `()<>%#?"\` percent-encoded. Many viewers lack CommonMark's angle-bracket form, but
 * they resolve percent-encoding as in any URL. `%`, `#` and `?` are encoded because a URL reads
 * them as an escape, a fragment and a query. Other characters, non-ASCII included, stay raw so the
 * text reads well.
 */
private fun markdownDestination(path: String): String = buildString {
	for (ch in path) {
		if (ch in DESTINATION_ENCODED || ch.isISOControl()) {
			for (byte in ch.toString().encodeToByteArray()) {
				append('%')
				append(HEX_DIGITS[(byte.toInt() shr 4) and 0xF])
				append(HEX_DIGITS[byte.toInt() and 0xF])
			}
		} else {
			append(ch)
		}
	}
}
