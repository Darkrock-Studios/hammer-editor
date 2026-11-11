package com.darkrockstudios.apps.hammer.common.data.export

import okio.BufferedSink

internal actual val pdfStoryExporter: StoryExporter? = PdfStoryExporter

private object PdfStoryExporter : StoryExporter {
	override val formatId = BuiltInExportFormat.PDF
	override val fileExtension = "pdf"
	override val mimeType = "application/pdf"
	override val embedsImages = true
	override val embedsCover = true

	override fun render(sink: BufferedSink, input: ExportInput) = writeStoryAsPdf(
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
