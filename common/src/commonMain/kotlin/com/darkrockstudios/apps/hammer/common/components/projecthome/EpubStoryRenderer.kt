package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import io.documentnode.epub4kmp.domain.*
import io.documentnode.epub4kmp.epub.EpubWriter
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import okio.BufferedSink
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

private const val TOC_TITLE = "Contents"

private data class TocEntry(val title: String, val href: String)

fun writeStoryAsEpub(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	language: String,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }

	val book = Book().apply {
		metadata.addTitle(projectName)
		authorName?.let { metadata.addAuthor(parseAuthor(it)) }
		metadata.language = language

		addStylesheet(buildStylesheet(projectData.theme))

		val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }
		val tocEntries = effective.mapIndexed { index, chapter ->
			TocEntry(title = chapter.name, href = "ch${index + 1}.xhtml")
		}

		// Title page, contents page, then the chapters.
		addSection(
			projectName,
			buildXhtmlResource(
				id = "title",
				href = "title.xhtml",
				title = projectName,
				bodyBuilder = { titlePageBody(projectName, authorName) },
			),
		)

		addSection(
			TOC_TITLE,
			buildXhtmlResource(
				id = "toc",
				href = "toc.xhtml",
				title = TOC_TITLE,
				bodyBuilder = { tocPageBody(tocEntries) },
			),
		)

		effective.forEachIndexed { index, chapter ->
			val entry = tocEntries[index]
			addSection(
				chapter.name,
				buildXhtmlResource(
					id = "ch${index + 1}",
					href = entry.href,
					title = chapter.name,
					bodyBuilder = { chapterBody(chapter.name, chapter.markdown) },
				),
			)
		}
	}

	EpubWriter().write(book, sink)
}

/**
 * Built from [Stylesheets.defaultReader] (serif type, first-line indents, h1 page break per chapter)
 * plus theme overlays — primary on h1 and links, secondary on h2, and a primary-colored title-page rule —
 * so the exported book inherits the project's chosen accent colors.
 */
private fun buildStylesheet(theme: ProjectTheme?): Stylesheet = stylesheet {
	raw(Stylesheets.defaultReader().css)

	// Title page presentation — applies even without a theme.
	raw(
		"""
		body.title-page { text-align: center; margin: 0; }
		.title-page-inner { margin-top: 30%; padding: 0 2em; }
		h1.book-title { font-size: 2.5em; margin: 0 0 0.4em 0; page-break-before: auto; }
		hr.title-rule { border: none; border-top: 2px solid currentColor; width: 40%; margin: 1em auto; }
		p.book-author { font-size: 1.2em; font-style: italic; margin: 0.5em 0; }
		body.toc-page h1.toc-title { text-align: center; margin-bottom: 1.5em; }
		h1.chapter-title { page-break-before: auto; }
		nav.toc ol.toc-list { list-style: none; padding: 0; margin: 0; }
		nav.toc li.toc-item { margin: 0.6em 0; text-indent: 0; }
		nav.toc li.toc-item a { text-decoration: none; }
		""".trimIndent(),
	)

	val primary = theme?.primary?.let(::argbHexToCssHex)
	val secondary = theme?.secondary?.let(::argbHexToCssHex)

	if (primary != null) {
		heading(1) { color(primary) }
		link { color(primary) }
		selector("hr.title-rule") { property("border-top", "2px solid $primary") }
	}
	if (secondary != null) {
		heading(2) { color(secondary) }
	}
}

/**
 * Project themes store colors as 8-digit ARGB hex (`#FFRRGGBB`); CSS wants 6-digit RGB.
 * Strip the alpha byte and uppercase. Returns null for anything we don't recognise so callers
 * can simply skip the override.
 */
internal fun argbHexToCssHex(argb: String): String? {
	val hex = argb.trim().removePrefix("#")
	return when (hex.length) {
		6 -> if (hex.all { it.isHexDigit() }) "#${hex.uppercase()}" else null
		8 -> if (hex.all { it.isHexDigit() }) "#${hex.substring(2).uppercase()}" else null
		else -> null
	}
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun buildXhtmlResource(
	id: String,
	href: String,
	title: String,
	bodyBuilder: BODY.() -> Unit,
): Resource {
	val xhtml = buildString {
		append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
		appendHTML(xhtmlCompatible = true).html(namespace = "http://www.w3.org/1999/xhtml") {
			head { title { +title } }
			body { bodyBuilder() }
		}
	}
	return Resource(id = id, data = xhtml.encodeToByteArray(), href = href)
		.apply { mediaType = MediaTypes.XHTML }
}

private fun BODY.tocPageBody(entries: List<TocEntry>) {
	classes = setOf("toc-page")
	h1(classes = "toc-title") { +TOC_TITLE }
	nav(classes = "toc") {
		ol(classes = "toc-list") {
			entries.forEach { entry ->
				li(classes = "toc-item") {
					a(href = entry.href) { +entry.title }
				}
			}
		}
	}
}

private fun BODY.titlePageBody(projectName: String, authorName: String?) {
	classes = setOf("title-page")
	div(classes = "title-page-inner") {
		h1(classes = "book-title") { +projectName }
		hr(classes = "title-rule")
		if (!authorName.isNullOrBlank()) {
			p(classes = "book-author") { +authorName }
		}
	}
}

private fun BODY.chapterBody(chapterTitle: String, markdown: String) {
	h1(classes = "chapter-title") { +chapterTitle }
	val flavour = CommonMarkFlavourDescriptor()
	val parsed = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
	val htmlBody = HtmlGenerator(markdown, parsed, flavour).generateHtml()
	val xhtmlBody = htmlBody.selfCloseHtmlVoidTags()
	unsafe { +xhtmlBody }
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
