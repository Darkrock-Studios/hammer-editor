package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import okio.BufferedSink

/** One export format. Plugins contribute formats by binding more of these in Koin. */
interface StoryExporter {
	/** Stable id, e.g. `epub`. A plugin's formats are prefixed with its id, e.g. `smf.docx`. */
	val formatId: String
	val fileExtension: String
	val mimeType: String

	/** A display name that needs no localization, such as a runtime plugin's; otherwise the UI supplies one. */
	val label: String? get() = null

	/** False for formats that ignore project data, which skips loading it. */
	val needsProjectData: Boolean get() = true

	/** True for formats that package project images, which is the only time their files are read. */
	val embedsImages: Boolean get() = false

	fun render(sink: BufferedSink, input: ExportInput)
}

/** One top-level node of the scene tree, holding the markdown of each scene under it in order. */
data class StoryChapter(val name: String, val scenes: List<String>, val separator: String = "\n\n") {
	constructor(name: String, markdown: String) : this(name, listOf(markdown))

	val markdown: String get() = scenes.joinToString(separator)
}

/**
 * What goes between two scenes. A renderer that drops lone blank lines still keeps a run of two,
 * so the wider gap is what carries the scene break through.
 */
fun sceneSeparator(options: ExportOptions): String =
	if (options.keepBlankLines || options.format == BuiltInExportFormat.MARKDOWN) "\n\n" else "\n\n\n"

/** A project image as read from disk for an export. */
class ExportImage(
	val id: Int,
	val name: String,
	val fileExtension: String,
	val bytes: ByteArray,
)

class ExportInput(
	val projectName: String,
	/** Null only when the exporter does not need project data. */
	val projectData: ProjectData?,
	/** One per top-level node of the scene tree. */
	val chapters: List<StoryChapter>,
	val treatTopLevelAsChapters: Boolean,
	/** The user's choices from the export dialog, for formats that honor them. */
	val options: ExportOptions,
	val language: String,
	val strings: ExportStrings,
	/** The project images the story refers to, by id; empty unless the exporter [StoryExporter.embedsImages]. */
	val images: Map<Int, ExportImage> = emptyMap(),
	/** The current name of each project image the story refers to, by id. */
	val imageNames: Map<Int, String> = emptyMap(),
) {
	fun requireProjectData(): ProjectData =
		requireNotNull(projectData) { "Project data is required for this export format" }

	/** [chapters] when top-level nodes are chapters, otherwise one chapter named after the project. */
	fun bookChapters(): List<StoryChapter> = if (treatTopLevelAsChapters) {
		chapters
	} else {
		listOf(StoryChapter(projectName, chapters.flatMap { it.scenes }, sceneSeparator(options)))
	}
}
