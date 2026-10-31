package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import com.darkrockstudios.apps.hammer.common.data.search.escapeMarkdown
import com.darkrockstudios.apps.hammer.common.data.search.escapeOrderedListMarkers
import com.darkrockstudios.apps.hammer.common.util.zip.InMemoryZipEntry
import com.darkrockstudios.apps.hammer.common.util.zip.writeZip
import okio.BufferedSink

private const val MARKDOWN_FILE_EXTENSION = "md"

/** The folder beside the `.md` that holds the images it shows. */
private const val MARKDOWN_IMAGES_DIRECTORY = "images"

// Enough only for a path from ImagesDatasource.getFilename, whose FilenameEncoding has already
// mapped ? " < > \ to lookalikes; ASCII controls are checked separately.
private const val DESTINATION_ENCODED = " ()%#`"

/**
 * Renders the story as a zip of `<project>.md` and, under [MARKDOWN_IMAGES_DIRECTORY], the file of
 * each project image it shows, named as in the project ([ImagesDatasource.getFilename]) and with its
 * bytes unchanged. Each project image in the story points at its file, with the image's name as alt
 * text, and the cover leads the document. An image missing from [ExportInput.images] is written as
 * its name, and one with no name is dropped.
 */
fun writeStoryAsMarkdownZip(sink: BufferedSink, input: ExportInput) {
	// Keyed by image id, in the order the document first shows them.
	val packaged = LinkedHashMap<Int, InMemoryZipEntry>()
	fun imageMarkdown(image: ExportImage): String {
		val entry = packaged.getOrPut(image.id) {
			val def = ImageDef(id = image.id, name = image.name, fileExtension = image.fileExtension)
			InMemoryZipEntry("$MARKDOWN_IMAGES_DIRECTORY/${ImagesDatasource.getFilename(def)}", image.bytes)
		}
		return "![${escapeMarkdown(image.name)}](${markdownDestination(entry.name)})"
	}

	val cover = exportCover(input.requireProjectData(), input.images, ::imageMarkdown)
	// Rewritten as a whole, so a reference definition in one scene still serves a use in another.
	val body = ImageReferenceSyntax.replaceImages(storyMarkdown(input)) { imageId, _ ->
		input.images[imageId]?.let(::imageMarkdown)
			?: input.imageNames[imageId]?.let { escapeOrderedListMarkers(escapeMarkdown(it)) }
	}
	val markdown = (if (cover != null) "$cover\n\n$body" else body).encodeToByteArray()

	val documentName = exportFileName(input.projectName, MARKDOWN_FILE_EXTENSION).fileName
	writeZip(sink, listOf(InMemoryZipEntry(documentName, markdown)) + packaged.values)
}

/**
 * [path] as a link destination, with each space, parenthesis, `%`, `#`, backtick and ASCII control
 * character percent-encoded: many viewers lack CommonMark's angle-bracket form, and a URL reads `%`
 * and `#` as an escape and a fragment.
 */
private fun markdownDestination(path: String): String = buildString {
	for (ch in path) {
		if (ch in DESTINATION_ENCODED || ch < ' ' || ch == '\u007F') {
			append('%').append(ch.code.toByte().toHexString(HexFormat.UpperCase))
		} else {
			append(ch)
		}
	}
}
