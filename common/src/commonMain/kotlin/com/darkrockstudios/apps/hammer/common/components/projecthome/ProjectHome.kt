package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable

interface ProjectHome : Router, HammerComponent, BackHandlerOwner, ComponentToaster {
	val state: Value<State>
	val contentRouterState: Value<ChildStack<ProjectHomeContentRouter.Config, ContentDestination>>

	suspend fun exportProject(path: String, options: ExportOptions): HPath
	fun beginProjectExport()
	fun cancelExportDialog()
	fun confirmExportDialog(options: ExportOptions)
	fun endProjectExport()
	fun startProjectSync()
	fun showGlobalSearch()
	fun supportsBackup(): Boolean
	fun createBackup(callback: (ProjectBackupDef?) -> Unit)
	fun getExportStoryFileName(): String
	fun refreshStatistics()

	fun showProjectStats()
	fun showProjectSettings()

	fun onBack()

	@Serializable
	data class State(
		val projectDef: ProjectDef,
		val created: String,
		val numberOfScenes: Int = 0,
		val totalWords: Int = 0,
		val wordsByChapter: Map<String, Int> = emptyMap(),
		val encyclopediaEntriesByType: Map<EntryType, Int> = emptyMap(),
		val longestSceneName: String? = null,
		val longestSceneWords: Int = 0,
		val shortestSceneWords: Int = 0,
		val medianSceneWords: Int = 0,
		val sceneWordsStdDev: Int = 0,
		val numberOfNotes: Int = 0,
		val numberOfTimelineEvents: Int = 0,
		val showExportDialog: Boolean = false,
		val showExportFilePicker: Boolean = false,
		val exportOptions: ExportOptions = ExportOptions(),
		val hasServer: Boolean = false,
		val isLoadingStats: Boolean = false,
		val isStatsDirty: Boolean = false,
	) {
		val averageWordsPerScene: Int
			get() = if (numberOfScenes > 0) totalWords / numberOfScenes else 0
	}

	sealed class ContentDestination {
		data object Stats : ContentDestination()
		data class ProjectSettings(val component: ProjectSettingsComponent) : ContentDestination()
	}
}
