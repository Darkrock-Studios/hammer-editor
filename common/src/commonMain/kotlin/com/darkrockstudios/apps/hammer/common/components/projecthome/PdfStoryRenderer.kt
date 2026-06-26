package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import okio.BufferedSink

private fun chapterAnchorId(index: Int): String = "chapter-$index"

/**
 * Renders the story as a PDF mirroring the EPUB layout: a title page, a clickable table of contents,
 * then one auto-paginating section per chapter. Chapter bodies are rendered from markdown with book
 * typography (first-line paragraph indents) via [proseMarkdown]. Like the EPUB stylesheet, the
 * project theme's primary color accents the title page, contents, chapter headings, and links;
 * secondary accents h2 headings within chapters.
 */
fun writeStoryAsPdf(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	strings: ExportStrings,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }
	val primary = projectData.theme?.primary?.let(::argbHexToPdfColor)
	val secondary = projectData.theme?.secondary?.let(::argbHexToPdfColor)
	val proseColors = ProseColors(primary = primary, secondary = secondary)

	val document = pdf {
		// Bottom/side margins so text doesn't run to the page edge; Slice lets long bodies flow
		// onto new pages at line boundaries instead of being clipped at the bottom.
		defaultPagePadding = Padding.symmetric(horizontal = 56.dp, vertical = 64.dp)
		defaultPageBreakStrategy = PageBreakStrategy.Slice

		metadata {
			title = projectName
			authorName?.let { author = it }
		}

		// Title page — accent rule under the title mirrors the EPUB's title-page hr.
		page {
			text(projectName) {
				fontSize = 36.sp
				bold = true
				primary?.let { color = it }
			}
			spacer(height = 10.dp)
			box(width = 200.dp) {
				divider(thickness = 2.dp, color = primary ?: PdfColor.Gray)
			}
			strings.authorByline?.let {
				spacer(height = 10.dp)
				text(it) { fontSize = 16.sp; italic = true }
			}
		}

		// Contents page — each row links to the matching chapter's anchor below.
		page {
			text(strings.contentsTitle) {
				fontSize = 26.sp
				bold = true
				primary?.let { color = it }
			}
			spacer(height = 10.dp)
			column(spacing = 6.dp) {
				effective.forEachIndexed { index, chapter ->
					linkToAnchor(chapterAnchorId(index)) {
						text("${index + 1}. ${chapter.name}") {
							primary?.let { color = it }
						}
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
				text("${index + 1}. ${chapter.name}") {
					fontSize = 22.sp
					bold = true
					primary?.let { color = it }
				}
				if (chapter.markdown.isNotBlank()) {
					spacer(height = 14.dp)
					proseMarkdown(chapter.markdown, proseColors)
				}
			}
		}
	}

	sink.write(document.toByteArray())
}

/** Project themes store colors as ARGB hex (`#FFRRGGBB`, sometimes `#RRGGBB`); null for anything else. */
internal fun argbHexToPdfColor(argb: String): PdfColor? {
	val hex = argb.trim().removePrefix("#")
	val rgb = when (hex.length) {
		6 -> hex
		8 -> hex.substring(2)
		else -> return null
	}
	val value = rgb.toIntOrNull(16) ?: return null
	return PdfColor(
		((value shr 16) and 0xFF) / 255f,
		((value shr 8) and 0xFF) / 255f,
		(value and 0xFF) / 255f,
	)
}
