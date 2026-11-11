package com.darkrockstudios.apps.hammer.common.data.export

import okio.BufferedSink

object BuiltInExportFormat {
	const val EPUB = "epub"
	const val DOCX = "docx"
	const val RTF = "rtf"
	const val PDF = "pdf"
	const val MARKDOWN = "markdown"
}

internal val builtInStoryExporters: List<StoryExporter> = listOfNotNull(
	EpubStoryExporter,
	DocxStoryExporter,
	RtfStoryExporter,
	pdfStoryExporter,
	MarkdownStoryExporter,
)

/** The PDF exporter, or null where pdfkmp, its renderer, is not available. */
internal expect val pdfStoryExporter: StoryExporter?

private object EpubStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.EPUB
	override val fileExtension = "epub"
	override val mimeType = "application/epub+zip"
	override val embedsImages = true
	override val embedsCover = true

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsEpub(
		sink = sink,
		projectName = input.projectName,
		projectData = input.requireProjectData(),
		chapters = input.bookChapters(),
		language = input.language,
		strings = input.strings,
		options = input.options,
		images = input.images,
		imageNames = input.imageNames,
	)
}

private object DocxStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.DOCX
	override val fileExtension = "docx"
	override val mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
	override val embedsImages = true
	override val embedsCover = false

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsDocx(
		sink = sink,
		projectName = input.projectName,
		projectData = input.requireProjectData(),
		chapters = input.bookChapters(),
		strings = input.strings,
		options = input.options,
		imageNames = input.imageNames,
		images = input.images,
		contact = input.contact,
	)
}

private object RtfStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.RTF
	override val fileExtension = "rtf"
	override val mimeType = "application/rtf"
	override val embedsImages = true
	override val embedsCover = true

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsRtf(
		sink = sink,
		projectName = input.projectName,
		projectData = input.requireProjectData(),
		chapters = input.bookChapters(),
		strings = input.strings,
		options = input.options,
		imageNames = input.imageNames,
		images = input.images,
	)
}

private object MarkdownStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.MARKDOWN
	override val fileExtension = "zip"
	override val mimeType = "application/zip"
	override val embedsImages = true
	override val embedsCover = true

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsMarkdownZip(sink, input)
}
