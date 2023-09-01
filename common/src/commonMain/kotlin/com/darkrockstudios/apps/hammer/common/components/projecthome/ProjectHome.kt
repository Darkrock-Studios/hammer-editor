package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface ProjectHome : Router, HammerComponent {
	val state: Value<State>

	suspend fun exportProject(path: String): HPath
	fun beginProjectExport()
	fun endProjectExport()
	fun startProjectSync()
	fun supportsBackup(): Boolean
	fun createBackup(callback: (ProjectBackupDef?) -> Unit)
	fun getExportStoryFileName(): String

	data class State(
		val projectDef: ProjectDef,
		val created: String,
		val numberOfScenes: Int = 0,
		val totalWords: Int = 0,
		val wordsByChapter: Map<String, Int> = emptyMap(),
		val encyclopediaEntriesByType: Map<EntryType, Int> = emptyMap(),
		val showExportDialog: Boolean = false,
		val hasServer: Boolean = false,
	)
}