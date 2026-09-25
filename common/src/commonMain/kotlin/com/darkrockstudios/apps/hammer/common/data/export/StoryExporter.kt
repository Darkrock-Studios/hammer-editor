package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import okio.BufferedSink

/** One export format. Plugins contribute formats by binding more of these in Koin. */
interface StoryExporter {
	/** Stable id, e.g. `epub`. A plugin's formats are prefixed with its id, e.g. `smf.docx`. */
	val formatId: String
	val fileExtension: String
	val mimeType: String

	/** False for formats that ignore project data, which skips loading it. */
	val needsProjectData: Boolean get() = true

	fun render(sink: BufferedSink, input: ExportInput)
}

/** One top-level node of the scene tree, holding the markdown of each scene under it in order. */
data class StoryChapter(val name: String, val scenes: List<String>) {
	constructor(name: String, markdown: String) : this(name, listOf(markdown))

	val markdown: String get() = scenes.joinToString("\n\n")
}

class ExportInput(
	val projectName: String,
	/** Null only when the exporter does not need project data. */
	val projectData: ProjectData?,
	/** One per top-level node of the scene tree. */
	val chapters: List<StoryChapter>,
	val treatTopLevelAsChapters: Boolean,
	val language: String,
	val strings: ExportStrings,
) {
	fun requireProjectData(): ProjectData =
		requireNotNull(projectData) { "Project data is required for this export format" }

	/** [chapters] when top-level nodes are chapters, otherwise one chapter named after the project. */
	fun bookChapters(): List<StoryChapter> = if (treatTopLevelAsChapters) {
		chapters
	} else {
		listOf(StoryChapter(projectName, chapters.flatMap { it.scenes }))
	}
}
