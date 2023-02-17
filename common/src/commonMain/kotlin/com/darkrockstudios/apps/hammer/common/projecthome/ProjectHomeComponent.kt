package com.darkrockstudios.apps.hammer.common.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.projectInject
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectHomeComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), ProjectHome {

	private val mainDispatcher by injectMainDispatcher()

	private val projectEditorRepository: ProjectEditorRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	private val _state = MutableValue(
		ProjectHome.State(
			projectDef = projectDef,
			numberOfScenes = 0,
			created = ""
		)
	)
	override val state: Value<ProjectHome.State> = _state

	override fun beginProjectExport() {
		_state.reduce {
			it.copy(
				showExportDialog = true
			)
		}
	}

	override fun endProjectExport() {
		_state.reduce {
			it.copy(
				showExportDialog = false
			)
		}
	}

	override suspend fun exportProject(path: String) {
		val hpath = HPath(
			path = path,
			name = "",
			isAbsolute = true
		)
		projectEditorRepository.exportStory(hpath)

		withContext(mainDispatcher) {
			endProjectExport()
		}
	}

	override fun onCreate() {
		super.onCreate()

		loadData()
	}

	private fun loadData() {
		scope.launch(dispatcherDefault) {
			val metadata = projectEditorRepository.getMetadata()
			val created = metadata.info.created.formatLocal("dd MMM `yy")

			var sceneSummary: SceneSummary? = null
			projectEditorRepository.sceneListChannel.take(1).collect { summary ->
				sceneSummary = summary
			}
			val tree = sceneSummary?.sceneTree?.root ?: throw IllegalStateException("Failed to get scene tree")
			val numScenes = tree.totalChildren

			var words = 0
			tree.forEach { node ->
				if (node.value.type == SceneItem.Type.Scene) {
					val count = projectEditorRepository.countWordsInScene(node.value)
					words += count
				}
			}

			val wordsByChapter = mutableMapOf<String, Int>()
			tree.children.forEach { node ->
				val chapterName = node.value.name
				var wordsInChapter = 0
				node.forEach { child ->
					if (child.value.type == SceneItem.Type.Scene) {
						val count = projectEditorRepository.countWordsInScene(child.value)
						wordsInChapter += count
					}
				}

				wordsByChapter[chapterName] = wordsInChapter
			}

			encyclopediaRepository.loadEntries()
			val entriesByType = mutableMapOf<EntryType, Int>()
			encyclopediaRepository.entryListFlow.take(1).collect { entries ->
				EntryType.values().forEach { type ->
					val numEntriesOfType = entries.count { it.type == type }
					entriesByType[type] = numEntriesOfType
				}
			}

			withContext(dispatcherMain) {
				_state.reduce {
					it.copy(
						created = created,
						numberOfScenes = numScenes,
						totalWords = words,
						wordsByChapter = wordsByChapter,
						encyclopediaEntriesByType = entriesByType
					)
				}
			}
		}
	}

	override fun isAtRoot() = true
}

val wordRegex = Regex("""(\s+|(\r\n|\r|\n))""")
fun ProjectEditorRepository.countWordsInScene(sceneItem: SceneItem): Int {
	val markdown = loadSceneMarkdownRaw(sceneItem)
	val count = wordRegex.findAll(markdown.trim()).count() + 1
	return count
}