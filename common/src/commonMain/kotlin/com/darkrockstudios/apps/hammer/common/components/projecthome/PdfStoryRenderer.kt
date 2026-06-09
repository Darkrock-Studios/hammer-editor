package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.markdown.MarkdownTheme
import com.conamobile.pdfkmp.markdown.markdown
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import okio.BufferedSink

private const val TOC_TITLE = "Contents"

private fun chapterAnchorId(index: Int): String = "chapter-$index"

/**
 * Renders the story as a PDF mirroring the EPUB layout: a title page, a clickable table of contents,
 * then one auto-paginating section per chapter. Chapter bodies are rendered from markdown so headings,
 * emphasis, and lists are laid out as formatted text rather than literal syntax.
 *
 * The contents page is hand-built from anchor/linkToAnchor rather than the library's `tableOfContents`,
 * which prints page numbers and so forces a full dry-run layout of the whole book to resolve them —
 * doubling export time. Matching EPUB, entries carry no page numbers, so a single layout pass suffices.
 */
fun writeStoryAsPdf(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }

	val document = pdf {
		// Bottom/side margins so text doesn't run to the page edge; Slice lets long bodies flow
		// onto new pages at line boundaries instead of being clipped at the bottom.
		defaultPagePadding = Padding.symmetric(horizontal = 56.dp, vertical = 64.dp)
		defaultPageBreakStrategy = PageBreakStrategy.Slice

		metadata {
			title = projectName
			authorName?.let { author = it }
		}

		// Title page.
		page {
			text(projectName) { fontSize = 36.sp; bold = true }
			authorName?.let { text("by $it") { fontSize = 16.sp } }
		}

		// Contents page — each row links to the matching chapter's anchor below.
		page {
			text(TOC_TITLE) { fontSize = 26.sp; bold = true }
			column(spacing = 6.dp) {
				effective.forEachIndexed { index, chapter ->
					linkToAnchor(chapterAnchorId(index)) {
						text("${index + 1}. ${chapter.name}")
					}
				}
			}
		}

		// One section per chapter. The title is a literal text heading (not markdown) so metacharacters
		// in a chapter name render verbatim; Slice keeps it on the same page as the body that follows.
		// bookmark drives the reader's outline panel; anchor is the jump target for the contents page.
		effective.forEachIndexed { index, chapter ->
			page {
				bookmark(chapter.name)
				anchor(chapterAnchorId(index))
				text("${index + 1}. ${chapter.name}") { fontSize = 22.sp; bold = true }
				if (chapter.markdown.isNotBlank()) {
					markdown(chapter.markdown.stripBackslashEscapes(), theme = MarkdownTheme())
				}
			}
		}
	}

	sink.write(document.toByteArray())
}

private const val ASCII_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

/** Unescaping these would create active markdown (emphasis/code/link/strikethrough), so keep them escaped. */
private const val MARKDOWN_DELIMITERS = "*_`[]~"

/**
 * Resolves CommonMark backslash escapes (`\!`, `\(`, `\-`, …) to the bare punctuation a compliant
 * parser would emit. pdfkmp-markdown leaves the backslash in, so imported content that was escaped
 * upstream would otherwise show stray backslashes throughout the prose. Markdown delimiters are left
 * escaped so `\*literal\*` is not turned back into emphasis.
 */
private fun String.stripBackslashEscapes(): String {
	if ('\\' !in this) return this
	val out = StringBuilder(length)
	var i = 0
	while (i < length) {
		val c = this[i]
		val next = if (i + 1 < length) this[i + 1] else null
		if (c == '\\' && next != null && next in ASCII_PUNCTUATION && next !in MARKDOWN_DELIMITERS) {
			out.append(next)
			i += 2
		} else {
			out.append(c)
			i++
		}
	}
	return out.toString()
}
