package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.EntryAppearance
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.WritingActivityDerived
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

interface ProjectHome : Router, HammerComponent, BackHandlerOwner, ComponentToaster {
	val state: Value<State>
	val contentRouterState: Value<ChildStack<ProjectHomeContentRouter.Config, ContentDestination>>

	suspend fun exportProject(path: String, options: ExportOptions): HPath
	fun beginProjectExport()
	fun cancelExportDialog()
	fun confirmExportDialog(options: ExportOptions)
	fun endProjectExport()

	fun beginProjectImport()
	fun cancelImportFilePicker()
	fun selectImportFile(name: String, content: String)
	fun updateImportOptions(options: ImportOptions)
	fun cancelImportDialog()
	suspend fun confirmImportDialog()

	fun startProjectSync()
	fun showGlobalSearch()
	fun supportsBackup(): Boolean
	fun createBackup(callback: (ProjectBackupDef?) -> Unit)
	fun getExportStoryFileName(): String
	fun refreshStatistics()

	fun showProjectStats()
	fun showProjectSettings()

	fun closeProject()

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
		val dailyWordTotals: Map<LocalDate, Int> = emptyMap(),
		val wordsPerDevice: Map<String, Int> = emptyMap(),
		val topAppearances: List<EntryAppearance> = emptyList(),
		val totalEntryConnections: Int = 0,
		val tagBreakdowns: List<TagBreakdown> = emptyList(),
		val tagUsesByType: Map<TaggedEntityType, Int> = emptyMap(),
		val wordCountGoal: WordCountGoal? = null,
		val writingActivity: WritingActivityDerived = WritingActivityDerived.Empty,
		val showExportDialog: Boolean = false,
		val showExportFilePicker: Boolean = false,
		val exportOptions: ExportOptions = ExportOptions(),
		val showImportFilePicker: Boolean = false,
		val showImportDialog: Boolean = false,
		val importOptions: ImportOptions = ImportOptions(),
		val importSourceName: String = "",
		val importFileContent: String = "",
		val importPreview: ImportPreview = ImportPreview(emptyList()),
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
