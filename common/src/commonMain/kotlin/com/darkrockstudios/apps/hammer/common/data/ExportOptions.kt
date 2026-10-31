package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import kotlinx.serialization.Serializable

/** Body typefaces offered for exports; the reader's own installed copy of [faceName] is what renders. */
@Serializable
enum class ExportFont(val faceName: String, val cssFamily: String) {
	Georgia("Georgia", "Georgia, \"Times New Roman\", serif"),
	TimesNewRoman("Times New Roman", "\"Times New Roman\", Times, serif"),
	Garamond("Garamond", "Garamond, \"EB Garamond\", Georgia, serif"),
	Arial("Arial", "Arial, Helvetica, sans-serif"),
	Verdana("Verdana", "Verdana, Geneva, sans-serif"),
	CourierNew("Courier New", "\"Courier New\", Courier, monospace"),
}

@Serializable
data class ExportOptions(
	val treatTopLevelAsChapters: Boolean = true,
	/** A [com.darkrockstudios.apps.hammer.common.data.export.StoryExporter.formatId]. */
	val format: String = BuiltInExportFormat.EPUB,
	/** Scene ids the export is limited to; null exports the entire story. */
	val sceneIds: Set<Int>? = null,
	/** Prefixes each chapter title with its position ("1. Title"). */
	val numberChapters: Boolean = false,
	val font: ExportFont = ExportFont.Georgia,
	/**
	 * Keeps the blank lines the author typed between paragraphs. Off drops a lone blank line, leaving the
	 * indent as the separator, while a run of two or more still leaves one break.
	 */
	val keepBlankLines: Boolean = true,
	/** Docx only: the short story layout puts the title on page 1 instead of a separate title page. */
	val manuscriptShortStory: Boolean = false,
	/** Docx only: the classic manuscript layout sets the text in Courier instead of Times New Roman. */
	val manuscriptClassic: Boolean = false,
)
