package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.project_home_export_byline
import com.darkrockstudios.apps.hammer.project_home_export_contents_title
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import org.koin.core.component.KoinComponent

data class StoryChapter(val name: String, val markdown: String)

/** Source data read from disk before rendering; [projectData] is null only for Markdown, which doesn't need it. */
private class ExportSource(
	val perNodeChapters: List<StoryChapter>,
	val projectData: ProjectData?,
	val language: String,
) {
	fun requireProjectData(): ProjectData =
		requireNotNull(projectData) { "Project data is required for this export format" }
}

fun exportFileName(projectName: String, format: ExportFormat): String {
	val safeName = projectName.sanitizedFileName().ifBlank { "story" }
	return "$safeName.${format.fileExtension}"
}

val ExportFormat.fileExtension: String
	get() = when (this) {
		ExportFormat.Markdown -> "md"
		ExportFormat.Epub -> "epub"
		ExportFormat.Pdf -> "pdf"
		ExportFormat.Docx -> "docx"
		ExportFormat.Rtf -> "rtf"
	}

/** Strips characters that have meaning in file paths or the SAF picker; covers project names that came from sync. */
private val unsafeFileNameChars = Regex("""[/\\:*?"<>|\x00-\x1F]""")
private fun String.sanitizedFileName(): String =
	replace(unsafeFileNameChars, "_").trim().trim('.')

class ExportStoryUseCase(
	private val sceneEditorRepository: SceneEditorService,
	private val projectDataDatasource: ProjectDataDatasource,
	private val fileSystem: FileSystem,
	private val localeResolver: DeviceLocaleResolver,
	private val strRes: StrRes,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val defaultDispatcher by injectDefaultDispatcher()

	suspend fun execute(exportDir: HPath, options: ExportOptions): HPath {
		val projectName = sceneEditorRepository.projectDef.name
		val targetFile = (exportDir.toOkioPath() / exportFileName(projectName, options.format)).toHPath()
		return executeToFile(targetFile, options)
	}

	suspend fun executeToFile(exportFile: HPath, options: ExportOptions): HPath {
		val projectName = sceneEditorRepository.projectDef.name
		val exportPath = exportFile.toOkioPath()

		val rendered = render(projectName, options)

		withContext(ioDispatcher) {
			try {
				fileSystem.write(exportPath) { writeAll(rendered) }
				// Any failure must clean up the partial file, then rethrow unchanged.
			} catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
				// fileSystem.write truncates exportPath before the body runs; clean up the partial file on failure.
				runCatching { fileSystem.delete(exportPath, mustExist = false) }
				throw t
			}
		}

		return exportPath.toHPath()
	}

	/** Reads source data off [ioDispatcher], then renders the document into an in-memory buffer on [defaultDispatcher]. */
	private suspend fun render(projectName: String, options: ExportOptions): Buffer {
		val source = withContext(ioDispatcher) {
			val perNodeChapters = sceneEditorRepository.getSceneTree().root.children.mapNotNull { node ->
				chapterFor(node, options.sceneIds)
			}
			val projectData =
				if (options.format == ExportFormat.Markdown) null else projectDataDatasource.load().data
			// The project's declared language wins; the device locale is only a fallback.
			val language = projectData?.language?.takeIf { it.isNotBlank() }
				?: localeResolver.getCurrentLocale().language?.takeIf { it.isNotBlank() }
				?: "en"
			ExportSource(perNodeChapters, projectData, language)
		}

		val exportStrings = resolveExportStrings(source.projectData)

		return withContext(defaultDispatcher) {
			val buffer = Buffer()
			when (options.format) {
				ExportFormat.Markdown -> writeStoryAsMarkdown(
					sink = buffer,
					projectName = projectName,
					chapters = source.perNodeChapters,
					treatTopLevelAsChapters = options.treatTopLevelAsChapters,
				)

				ExportFormat.Epub -> writeStoryAsEpub(
					sink = buffer,
					projectName = projectName,
					projectData = source.requireProjectData(),
					chapters = chaptersFor(options, projectName, source.perNodeChapters),
					language = source.language,
					strings = exportStrings,
				)

				ExportFormat.Pdf -> writeStoryAsPdf(
					sink = buffer,
					projectName = projectName,
					projectData = source.requireProjectData(),
					chapters = chaptersFor(options, projectName, source.perNodeChapters),
					strings = exportStrings,
				)

				ExportFormat.Docx -> writeStoryAsDocx(
					sink = buffer,
					projectName = projectName,
					projectData = source.requireProjectData(),
					chapters = chaptersFor(options, projectName, source.perNodeChapters),
					strings = exportStrings,
				)

				ExportFormat.Rtf -> writeStoryAsRtf(
					sink = buffer,
					projectName = projectName,
					projectData = source.requireProjectData(),
					chapters = chaptersFor(options, projectName, source.perNodeChapters),
					strings = exportStrings,
				)
			}
			buffer
		}
	}

	/** Resolves the user-facing strings that appear inside the exported document for the active locale. */
	private suspend fun resolveExportStrings(projectData: ProjectData?): ExportStrings {
		val authorName = projectData?.authorName?.takeIf { it.isNotBlank() }
		return ExportStrings(
			contentsTitle = strRes.get(Res.string.project_home_export_contents_title),
			authorByline = authorName?.let { strRes.get(Res.string.project_home_export_byline, it) },
		)
	}

	/** Per-node chapters when treating top-level scenes as chapters; otherwise a single chapter named after the project. */
	private fun chaptersFor(
		options: ExportOptions,
		projectName: String,
		perNodeChapters: List<StoryChapter>,
	): List<StoryChapter> = if (options.treatTopLevelAsChapters) {
		perNodeChapters
	} else {
		listOf(StoryChapter(projectName, perNodeChapters.joinToString("\n\n") { it.markdown }))
	}

	/**
	 * Builds the chapter for one top-level node under an optional scene filter.
	 * Returns null when a filter is active and the node contributes no selected scenes,
	 * dropping it from the chapter list so later chapters renumber automatically.
	 * A filter never widens: an empty or fully stale set yields zero chapters.
	 */
	private fun chapterFor(node: TreeValue<SceneItem>, sceneFilter: Set<Int>?): StoryChapter? {
		val sceneNodes = if (node.value.type == SceneItem.Type.Scene) {
			listOf(node)
		} else {
			node.filter { it.value.type == SceneItem.Type.Scene }
		}
		val included = if (sceneFilter == null) {
			sceneNodes
		} else {
			sceneNodes.filter { it.value.id in sceneFilter }
		}
		if (sceneFilter != null && included.isEmpty()) return null
		return StoryChapter(
			name = node.value.name,
			markdown = included.joinToString("\n\n") { sceneEditorRepository.loadSceneMarkdownRaw(it.value) },
		)
	}

}
