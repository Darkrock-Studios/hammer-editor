package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable

interface ProjectHome : Router, HammerComponent, BackHandlerOwner, ComponentToaster {
	val state: Value<State>
	val contentRouterState: Value<ChildStack<ProjectHomeContentRouter.Config, ContentDestination>>

	suspend fun exportProject(path: String): HPath
	fun beginProjectExport()
	fun endProjectExport()
	fun startProjectSync()
	fun supportsBackup(): Boolean
	fun createBackup(callback: (ProjectBackupDef?) -> Unit)
	fun getExportStoryFileName(): String

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
		val showExportDialog: Boolean = false,
		val hasServer: Boolean = false,
		val isLoadingStats: Boolean = false,
	)

	sealed class ContentDestination {
		data object Stats : ContentDestination()
		data class ProjectSettings(val component: ProjectSettingsComponent) : ContentDestination()
	}
}
