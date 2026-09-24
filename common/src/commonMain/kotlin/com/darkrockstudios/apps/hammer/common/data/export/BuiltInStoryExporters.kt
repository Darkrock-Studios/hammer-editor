package com.darkrockstudios.apps.hammer.common.data.export

import okio.BufferedSink

object BuiltInExportFormat {
	const val EPUB = "epub"
	const val DOCX = "docx"
	const val RTF = "rtf"
	const val PDF = "pdf"
	const val MARKDOWN = "markdown"
}

internal val builtInStoryExporters: List<StoryExporter> = listOf(
	EpubStoryExporter,
	DocxStoryExporter,
	RtfStoryExporter,
	PdfStoryExporter,
	MarkdownStoryExporter,
)

private object EpubStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.EPUB
	override val fileExtension = "epub"
	override val mimeType = "application/epub+zip"

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsEpub(
		sink = sink,
		projectName = input.projectName,
		projectData = input.requireProjectData(),
		chapters = input.bookChapters(),
		language = input.language,
		strings = input.strings,
	)
}

private object DocxStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.DOCX
	override val fileExtension = "docx"
	override val mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsDocx(
		sink = sink,
		projectName = input.projectName,
		projectData = input.requireProjectData(),
		chapters = input.bookChapters(),
		strings = input.strings,
	)
}

private object RtfStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.RTF
	override val fileExtension = "rtf"
	override val mimeType = "application/rtf"

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsRtf(
		sink = sink,
		projectName = input.projectName,
		projectData = input.requireProjectData(),
		chapters = input.bookChapters(),
		strings = input.strings,
	)
}

private object PdfStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.PDF
	override val fileExtension = "pdf"
	override val mimeType = "application/pdf"

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsPdf(
		sink = sink,
		projectName = input.projectName,
		projectData = input.requireProjectData(),
		chapters = input.bookChapters(),
		strings = input.strings,
	)
}

private object MarkdownStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.MARKDOWN
	override val fileExtension = "md"

	// text/markdown is missing from MimeTypeMap on many Android versions and SAF providers; the
	// ".md" extension on the suggested file name is what determines association.
	override val mimeType = "text/plain"
	override val needsProjectData = false

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsMarkdown(
		sink = sink,
		projectName = input.projectName,
		chapters = input.chapters,
		treatTopLevelAsChapters = input.treatTopLevelAsChapters,
	)
}
