package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import kotlinx.coroutines.withContext
import okio.FileSystem
import org.koin.core.component.KoinComponent

data class StoryChapter(val name: String, val markdown: String)

fun exportFileName(projectName: String, format: ExportFormat): String {
	val safeName = projectName.sanitizedFileName().ifBlank { "story" }
	return when (format) {
		ExportFormat.Markdown -> "$safeName.md"
		ExportFormat.Epub -> "$safeName.epub"
	}
}

/** Strips characters that have meaning in file paths or the SAF picker; covers project names that came from sync. */
private val unsafeFileNameChars = Regex("""[/\\:*?"<>|\x00-\x1F]""")
private fun String.sanitizedFileName(): String =
	replace(unsafeFileNameChars, "_").trim().trim('.')

class ExportStoryUseCase(
	private val sceneEditorRepository: SceneEditorRepository,
	private val projectDataDatasource: ProjectDataDatasource,
	private val fileSystem: FileSystem,
	private val localeResolver: DeviceLocaleResolver,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	suspend fun execute(exportDir: HPath, options: ExportOptions): HPath = withContext(ioDispatcher) {
		val projectName = sceneEditorRepository.projectDef.name
		val perNodeChapters = sceneEditorRepository.getSceneTree().root.children.map { node ->
			StoryChapter(name = node.value.name, markdown = collectMarkdown(node))
		}
		val exportPath = exportDir.toOkioPath() / exportFileName(projectName, options.format)

		try {
			fileSystem.write(exportPath) {
				when (options.format) {
					ExportFormat.Markdown -> writeStoryAsMarkdown(
						sink = this,
						projectName = projectName,
						chapters = perNodeChapters,
						treatTopLevelAsChapters = options.treatTopLevelAsChapters,
					)

					ExportFormat.Epub -> {
						val projectData = projectDataDatasource.load().data
						val epubChapters = if (options.treatTopLevelAsChapters) {
							perNodeChapters
						} else {
							listOf(StoryChapter(projectName, perNodeChapters.joinToString("\n\n") { it.markdown }))
						}
						writeStoryAsEpub(
							sink = this,
							projectName = projectName,
							projectData = projectData,
							chapters = epubChapters,
							language = localeResolver.getCurrentLocale().language?.takeIf { it.isNotBlank() } ?: "en",
						)
					}
				}
			}
		} catch (t: Throwable) {
			// fileSystem.write truncates exportPath before the body runs; clean up the partial file on failure.
			runCatching { fileSystem.delete(exportPath, mustExist = false) }
			throw t
		}

		exportPath.toHPath()
	}

	private fun collectMarkdown(node: TreeValue<SceneItem>): String {
		return if (node.value.type == SceneItem.Type.Scene) {
			sceneEditorRepository.loadSceneMarkdownRaw(node.value)
		} else {
			node.filter { it.value.type == SceneItem.Type.Scene }
				.joinToString("\n\n") { sceneEditorRepository.loadSceneMarkdownRaw(it.value) }
		}
	}

}
