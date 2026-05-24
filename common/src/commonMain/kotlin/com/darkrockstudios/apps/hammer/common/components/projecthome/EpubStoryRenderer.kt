package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import io.documentnode.epub4kmp.domain.Author
import io.documentnode.epub4kmp.domain.Book
import io.documentnode.epub4kmp.domain.MediaTypes
import io.documentnode.epub4kmp.domain.Resource
import io.documentnode.epub4kmp.epub.EpubWriter
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import okio.BufferedSink
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

fun writeStoryAsEpub(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	language: String,
) {
	val book = Book().apply {
		metadata.addTitle(projectName)
		projectData.authorName
			?.takeIf { it.isNotBlank() }
			?.let { metadata.addAuthor(parseAuthor(it)) }
		metadata.language = language

		val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }
		effective.forEachIndexed { index, chapter ->
			val resourceId = "ch${index + 1}"
			addSection(chapter.name, buildXhtmlResource(resourceId, chapter.name, chapter.markdown))
		}
	}

	EpubWriter().write(book, sink)
}

private fun buildXhtmlResource(id: String, title: String, markdown: String): Resource {
	val xhtml = markdownToXhtml(title, markdown)
	return Resource(
		id = id,
		data = xhtml.encodeToByteArray(),
		href = "$id.xhtml",
	).apply { mediaType = MediaTypes.XHTML }
}

private fun markdownToXhtml(chapterTitle: String, markdown: String): String {
	val flavour = CommonMarkFlavourDescriptor()
	val parsed = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
	val htmlBody = HtmlGenerator(markdown, parsed, flavour).generateHtml()
	val xhtmlBody = htmlBody.selfCloseHtmlVoidTags()
	return buildString {
		append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
		appendHTML(xhtmlCompatible = true).html(namespace = "http://www.w3.org/1999/xhtml") {
			head { title { +chapterTitle } }
			body {
				h1 { +chapterTitle }
				unsafe { +xhtmlBody }
			}
		}
	}
}

private val voidTagPattern = Regex(
	"""<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)\b([^>]*?)(?<!/)>""",
	RegexOption.IGNORE_CASE,
)

/** CommonMark's HtmlGenerator emits HTML5 void tags (`<br>`, `<img ...>`); EPUB content docs are parsed as XML. */
private fun String.selfCloseHtmlVoidTags(): String =
	voidTagPattern.replace(this) { m -> "<${m.groupValues[1]}${m.groupValues[2]}/>" }

/** Split on last whitespace; fall back to whole string as given name when no space present. */
private fun parseAuthor(fullName: String): Author {
	val trimmed = fullName.trim()
	val lastSpace = trimmed.lastIndexOf(' ')
	return if (lastSpace > 0) {
		Author(trimmed.substring(0, lastSpace), trimmed.substring(lastSpace + 1))
	} else {
		Author(trimmed, "")
	}
}
