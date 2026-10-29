package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import io.documentnode.epub4kmp.domain.*
import io.documentnode.epub4kmp.epub.EpubWriter
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import okio.BufferedSink
import com.darkrockstudios.apps.hammer.base.markdown.ProseHtml
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.ImageGeneratingProvider
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

private data class TocEntry(val title: String, val href: String)

/** An image packaged in the book; [href] is relative to the chapter files. */
private class PackagedImage(val name: String, val href: String, val resource: Resource)

fun writeStoryAsEpub(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	language: String,
	strings: ExportStrings,
	options: ExportOptions = ExportOptions(),
	images: Map<Int, ExportImage> = emptyMap(),
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val packagedImages = images.values.mapNotNull { image -> packageImage(image)?.let { image.id to it } }.toMap()

	val book = Book().apply {
		metadata.addTitle(projectName)
		authorName?.let { metadata.addAuthor(parseAuthor(it)) }
		metadata.language = language

		addStylesheet(buildStylesheet(projectData.theme, options.font))

		packagedImages.values.forEach { addResource(it.resource) }
		projectData.coverImageId?.let(packagedImages::get)?.let { coverImage = it.resource }

		val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }
		val tocEntries = effective.mapIndexed { index, chapter ->
			TocEntry(title = chapterTitle(index, chapter, options.numberChapters), href = "ch${index + 1}.xhtml")
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
			strings.contentsTitle,
			buildXhtmlResource(
				id = "toc",
				href = "toc.xhtml",
				title = strings.contentsTitle,
				bodyBuilder = { tocPageBody(strings.contentsTitle, tocEntries) },
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
					bodyBuilder = {
						chapterBody(entry.title, chapter.markdown, options.keepBlankLines, packagedImages)
					},
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
private fun buildStylesheet(theme: ProjectTheme?, font: ExportFont): Stylesheet = stylesheet {
	raw(Stylesheets.defaultReader().css)
	raw("body { font-family: ${font.cssFamily}; }")

	// Title page presentation — applies even without a theme.
	raw(
		"""
		/* Each authored line is its own paragraph, so the indent has to be the only separator */
		p { margin: 0; }
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
		.user-del { text-decoration: line-through; }
		img { display: block; max-width: 100%; height: auto; margin: 1em auto; }
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

private fun BODY.tocPageBody(contentsTitle: String, entries: List<TocEntry>) {
	classes = setOf("toc-page")
	h1(classes = "toc-title") { +contentsTitle }
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

private fun BODY.chapterBody(
	chapterTitle: String,
	markdown: String,
	keepBlankLines: Boolean,
	images: Map<Int, PackagedImage>,
) {
	h1(classes = "chapter-title") { +chapterTitle }
	val flavour = GFMFlavourDescriptor()
	val source = ProseHtml.normalizeLineEndings(markdown)
	val parsed = MarkdownParser(flavour).buildMarkdownTreeFromString(source)
	val linkMap = LinkMap.buildLinkMap(parsed, source)
	val providers = ProseHtml.providers(flavour, linkMap, keepBlankLines) +
		(MarkdownElementTypes.IMAGE to EpubImageProvider(linkMap, images))
	val htmlBody = HtmlGenerator(source, parsed, providers).generateHtml()
	val xhtmlBody = htmlBody.selfCloseHtmlVoidTags()
	unsafe { +xhtmlBody }
}

/**
 * Writes a `hammer://image` reference to a packaged image as an `img` of it, and any other image as
 * its alt text: an EPUB reader loads only resources packaged in the book. An image written without
 * alt text is described by its name.
 */
private class EpubImageProvider(
	linkMap: LinkMap,
	private val images: Map<Int, PackagedImage>,
) : ImageGeneratingProvider(linkMap, null) {
	override fun renderLink(
		visitor: HtmlGenerator.HtmlGeneratingVisitor,
		text: String,
		node: ASTNode,
		info: RenderInfo,
	) {
		val image = ImageReferenceSyntax.parseId(info.destination.toString())?.let(images::get)
		if (image == null) {
			labelProvider.processNode(visitor, text, info.label)
		} else {
			val alt = plainAltText(info.label, text).ifBlank { escapeAttribute(image.name) }
			// Written as an HTML void tag; selfCloseHtmlVoidTags closes it for XHTML.
			visitor.consumeTagOpen(node, "img", "src=\"${image.href}\"", "alt=\"$alt\"")
		}
	}
}

private val altMarkupTokens = setOf(MarkdownTokenTypes.EMPH, MarkdownTokenTypes.BACKTICK, GFMTokenTypes.TILDE)

/**
 * The label's text without its brackets or emphasis markup. [HtmlGenerator.leafText] resolves Markdown
 * escapes and entities and escapes `& < > "`, so the result is safe in an attribute.
 */
private fun plainAltText(label: ASTNode, text: String): String = buildString {
	fun visit(node: ASTNode) {
		when {
			node.type in altMarkupTokens -> Unit
			node is LeafASTNode -> append(HtmlGenerator.leafText(text, node))
			else -> node.children.forEach(::visit)
		}
	}
	val children = label.children
	children.forEachIndexed { index, child ->
		val isBracket = (index == 0 && child.type == MarkdownTokenTypes.LBRACKET) ||
			(index == children.lastIndex && child.type == MarkdownTokenTypes.RBRACKET)
		if (!isBracket) visit(child)
	}
}

private fun escapeAttribute(value: String): String = value
	.replace("&", "&amp;")
	.replace("<", "&lt;")
	.replace(">", "&gt;")
	.replace("\"", "&quot;")

private fun packageImage(image: ExportImage): PackagedImage? {
	val extension = image.fileExtension.lowercase()
	val mimeType = ImageFormat.mimeType(extension) ?: return null
	// epub4kmp tells bitmaps apart by the identity of its own constants, so reuse them where one exists.
	val mediaType = MediaTypes.getMediaTypeByName(mimeType) ?: MediaType(mimeType, ".$extension")
	val href = "images/${image.id}.$extension"
	val resource = Resource(id = "img${image.id}", data = image.bytes, href = href)
		.apply { this.mediaType = mediaType }
	return PackagedImage(image.name, href, resource)
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
