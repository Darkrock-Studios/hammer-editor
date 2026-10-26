package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tree.ChapterScenes
import com.darkrockstudios.apps.hammer.common.data.tree.collectChapters
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import org.koin.core.component.KoinComponent

/** An export's file name, with the extension apart for save dialogs that add it themselves. */
data class ExportFileName(val baseName: String, val extension: String) {
	val fileName: String get() = "$baseName.$extension"
}

fun exportFileName(projectName: String, fileExtension: String): ExportFileName =
	ExportFileName(projectName.sanitizedFileName().ifBlank { "story" }, fileExtension)

/** Strips characters that have meaning in file paths or the SAF picker; covers project names that came from sync. */
private val unsafeFileNameChars = Regex("""[/\\:*?"<>|\x00-\x1F]""")
private fun String.sanitizedFileName(): String =
	replace(unsafeFileNameChars, "_").trim().trim('.')

class ExportStoryUseCase(
	private val sceneEditorRepository: SceneEditorService,
	private val exporters: StoryExporterRegistry,
	private val projectPlugins: ProjectPluginsRepository,
	private val projectDataDatasource: ProjectDataDatasource,
	private val fileSystem: FileSystem,
	private val localeResolver: DeviceLocaleResolver,
	private val strRes: StrRes,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val defaultDispatcher by injectDefaultDispatcher()

	suspend fun execute(exportDir: HPath, options: ExportOptions): HPath {
		val projectName = sceneEditorRepository.projectDef.name
		val fileExtension = exporterFor(options.format).fileExtension
		val targetFile = (exportDir.toOkioPath() / exportFileName(projectName, fileExtension).fileName).toHPath()
		return executeToFile(targetFile, options)
	}

	suspend fun executeToFile(exportFile: HPath, options: ExportOptions): HPath {
		val exportPath = exportFile.toOkioPath()

		val rendered = render(options)

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
	suspend fun render(options: ExportOptions): Buffer {
		val projectName = sceneEditorRepository.projectDef.name
		val exporter = exporterFor(options.format)
		val input = withContext(ioDispatcher) {
			val chapters = sceneEditorRepository.getSceneTree().collectChapters().mapNotNull { chapter ->
				chapterFor(chapter, options)
			}
			val projectData = if (exporter.needsProjectData) projectDataDatasource.load().data else null
			// The project's declared language wins; the device locale is only a fallback.
			val language = projectData?.language?.takeIf { it.isNotBlank() }
				?: localeResolver.getCurrentLocale().language?.takeIf { it.isNotBlank() }
				?: "en"
			ExportInput(
				projectName = projectName,
				projectData = projectData,
				chapters = chapters,
				treatTopLevelAsChapters = options.treatTopLevelAsChapters,
				options = options,
				language = language,
				strings = resolveExportStrings(projectData),
			)
		}

		return withContext(defaultDispatcher) {
			Buffer().also { exporter.render(it, input) }
		}
	}

	/** Throws [IllegalArgumentException] when the project cannot export to [format]. */
	private suspend fun exporterFor(format: String): StoryExporter {
		val disabledPlugins = projectPlugins.disabledPlugins(sceneEditorRepository.projectDef.id).first()
		return exporters.forFormat(format, disabledPlugins)
	}

	/** Resolves the user-facing strings that appear inside the exported document for the active locale. */
	private suspend fun resolveExportStrings(projectData: ProjectData?): ExportStrings {
		val authorName = projectData?.authorName?.takeIf { it.isNotBlank() }
		return ExportStrings(
			contentsTitle = strRes.get(Res.string.project_home_export_contents_title),
			authorByline = authorName?.let { strRes.get(Res.string.project_home_export_byline, it) },
		)
	}

	/**
	 * Builds the chapter for one top-level node under an optional scene filter.
	 * Returns null when a filter is active and the node contributes no selected scenes,
	 * dropping it from the chapter list so later chapters renumber automatically.
	 * A filter never widens: an empty or fully stale set yields zero chapters.
	 */
	private fun chapterFor(chapter: ChapterScenes, options: ExportOptions): StoryChapter? {
		val sceneFilter = options.sceneIds
		val included = if (sceneFilter == null) {
			chapter.scenes
		} else {
			chapter.scenes.filter { it.id in sceneFilter }
		}
		if (sceneFilter != null && included.isEmpty()) return null
		return StoryChapter(
			name = chapter.chapter.name,
			scenes = included.map { currentMarkdown(it) },
			separator = sceneSeparator(options),
		)
	}

	/** The scene as the editor shows it: an unsaved buffer wins over the file on disk. */
	private fun currentMarkdown(scene: SceneItem): String =
		sceneEditorRepository.getSceneBuffer(scene)?.content?.coerceMarkdown()
			?: sceneEditorRepository.loadSceneMarkdownRaw(scene)
}
