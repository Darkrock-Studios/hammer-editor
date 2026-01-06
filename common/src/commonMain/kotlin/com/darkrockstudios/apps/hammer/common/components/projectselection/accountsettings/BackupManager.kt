package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import kotlinx.serialization.Serializable

interface BackupManager {
	val state: Value<State>

	fun selectProject(projectName: String)
	fun deleteBackup(backup: ProjectBackupDef)
	fun restoreBackup(backup: ProjectBackupDef)
	fun exportBackup(backup: ProjectBackupDef)

	@Serializable
	data class State(
		val availableProjects: List<String> = emptyList(),
		val selectedProject: String? = null,
		val selectedProjectDef: ProjectDef? = null,
		val backupsForSelectedProject: List<ProjectBackupDef> = emptyList(),
		val isLoading: Boolean = false,
		val error: String? = null
	)
}