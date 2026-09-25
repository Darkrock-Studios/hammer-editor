package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
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

fun exportFileName(projectName: String, fileExtension: String): String {
	val safeName = projectName.sanitizedFileName().ifBlank { "story" }
	return "$safeName.$fileExtension"
}

/** Strips characters that have meaning in file paths or the SAF picker; covers project names that came from sync. */
private val unsafeFileNameChars = Regex("""[/\\:*?"<>|\x00-\x1F]""")
private fun String.sanitizedFileName(): String =
	replace(unsafeFileNameChars, "_").trim().trim('.')

class ExportStoryUseCase(
	private val sceneEditorRepository: SceneEditorService,
	private val exporters: StoryExporterRegistry,
	private val projectDataDatasource: ProjectDataDatasource,
	private val fileSystem: FileSystem,
	private val localeResolver: DeviceLocaleResolver,
	private val strRes: StrRes,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val defaultDispatcher by injectDefaultDispatcher()

	suspend fun execute(exportDir: HPath, options: ExportOptions): HPath {
		val projectName = sceneEditorRepository.projectDef.name
		val fileExtension = exporters.forFormat(options.format).fileExtension
		val targetFile = (exportDir.toOkioPath() / exportFileName(projectName, fileExtension)).toHPath()
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
		val exporter = exporters.forFormat(options.format)
		val input = withContext(ioDispatcher) {
			val chapters = sceneEditorRepository.getSceneTree().root.children.mapNotNull { node ->
				chapterFor(node, options.sceneIds)
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
				language = language,
				strings = resolveExportStrings(projectData),
			)
		}

		return withContext(defaultDispatcher) {
			Buffer().also { exporter.render(it, input) }
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
