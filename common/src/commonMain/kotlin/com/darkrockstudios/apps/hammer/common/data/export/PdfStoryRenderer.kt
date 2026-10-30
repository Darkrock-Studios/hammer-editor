package com.darkrockstudios.apps.hammer.common.data.export

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.defaultPdfDriverFactory
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.render.PdfDriverFactory
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import okio.BufferedSink

private fun chapterAnchorId(index: Int): String = "chapter-$index"

internal val STORY_PAGE_SIZE = PageSize.A4
internal val STORY_PAGE_PADDING = Padding.symmetric(horizontal = 56.dp, vertical = 64.dp)

/** The share of the page height a tall image is shrunk to fit. */
private const val MAX_IMAGE_PAGE_FRACTION = 0.6f

/**
 * Renders the story as a PDF mirroring the EPUB layout: a title page, a clickable table of contents,
 * then one auto-paginating section per chapter. Chapter bodies are rendered from markdown with book
 * typography (first-line paragraph indents) via [proseMarkdown]. Like the EPUB stylesheet, the
 * project theme's primary color accents the title page, contents, chapter headings, and links;
 * secondary accents h2 headings within chapters. A project image on a line of its own is drawn from
 * [images]; one missing there, or in a format it cannot draw, is written as its name.
 */
fun writeStoryAsPdf(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	strings: ExportStrings,
	options: ExportOptions = ExportOptions(),
	imageNames: Map<Int, String> = emptyMap(),
	images: Map<Int, ExportImage> = emptyMap(),
) {
	sink.write(buildStoryPdf(projectName, projectData, chapters, strings, options, imageNames, images).toByteArray())
}

internal fun buildStoryPdf(
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	strings: ExportStrings,
	options: ExportOptions = ExportOptions(),
	imageNames: Map<Int, String> = emptyMap(),
	images: Map<Int, ExportImage> = emptyMap(),
	factory: PdfDriverFactory = defaultPdfDriverFactory(),
): PdfDocument {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }
	val primary = projectData.theme?.primary?.let(::argbHexToPdfColor)
	val secondary = projectData.theme?.secondary?.let(::argbHexToPdfColor)
	val proseColors = ProseColors(primary = primary, secondary = secondary)

	val contentWidth = STORY_PAGE_SIZE.width.value - STORY_PAGE_PADDING.left.value - STORY_PAGE_PADDING.right.value
	// Decoded once per image, however often the story shows it.
	val pictures = mutableMapOf<Int, ProsePicture?>()
	val picture = { imageId: Int ->
		if (imageId !in pictures) pictures[imageId] = images[imageId]?.let(::prosePicture)
		pictures[imageId]
	}
	val measuringDriver = factory.create(PdfMetadata(), emptyList())
	val prose = try {
		effective.map {
			prepareProse(
				it.markdown,
				proseColors,
				measuringDriver.fontMetrics,
				contentWidth,
				options.keepBlankLines,
				imageNames::get,
				picture,
				maxImageHeight = STORY_PAGE_SIZE.height.value * MAX_IMAGE_PAGE_FRACTION,
			)
		}
	} finally {
		measuringDriver.close()
	}

	return pdf(factory) {
		// Bottom/side margins so text doesn't run to the page edge; Slice lets long bodies flow
		// onto new pages at line boundaries instead of being clipped at the bottom.
		defaultPagePadding = STORY_PAGE_PADDING
		defaultPageBreakStrategy = PageBreakStrategy.Slice

		metadata {
			title = projectName
			authorName?.let { author = it }
		}

		// Title page — accent rule under the title mirrors the EPUB's title-page hr.
		page(STORY_PAGE_SIZE) {
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
		page(STORY_PAGE_SIZE) {
			text(strings.contentsTitle) {
				fontSize = 26.sp
				bold = true
				primary?.let { color = it }
			}
			spacer(height = 10.dp)
			column(spacing = 6.dp) {
				effective.forEachIndexed { index, chapter ->
					linkToAnchor(chapterAnchorId(index)) {
						text(chapterTitle(index, chapter, options.numberChapters)) {
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
			page(STORY_PAGE_SIZE) {
				bookmark(chapter.name)
				anchor(chapterAnchorId(index))
				text(chapterTitle(index, chapter, options.numberChapters)) {
					fontSize = 22.sp
					bold = true
					primary?.let { color = it }
				}
				if (chapter.markdown.isNotBlank()) {
					spacer(height = 14.dp)
					proseMarkdown(prose[index], proseColors)
				}
			}
		}
	}
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
