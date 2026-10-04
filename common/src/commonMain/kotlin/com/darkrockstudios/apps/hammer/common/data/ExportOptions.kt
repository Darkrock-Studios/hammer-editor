package com.darkrockstudios.apps.hammer.common.data

import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat { Markdown, Epub, Pdf, Docx, Rtf }

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
	val format: ExportFormat = ExportFormat.Epub,
	/** Scene ids the export is limited to; null exports the entire story. */
	val sceneIds: Set<Int>? = null,
	/** Prefixes each chapter title with its position ("1. Title"). */
	val numberChapters: Boolean = true,
	val font: ExportFont = ExportFont.Georgia,
	/** Keeps the blank lines the author typed between paragraphs; off leaves the indent as the only separator. */
	val keepBlankLines: Boolean = true,
)
