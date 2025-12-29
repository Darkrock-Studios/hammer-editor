package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.backup_manager_error_delete_backup
import com.darkrockstudios.apps.hammer.backup_manager_error_load_backups
import com.darkrockstudios.apps.hammer.backup_manager_error_load_project_backups
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BackupManagerComponent(
	componentContext: ComponentContext,
) : BackupManager, KoinComponent, SavableComponent<BackupManager.State>(componentContext) {

	private val backupRepository by inject<ProjectBackupRepository>()
	private val projectsRepository by inject<ProjectsRepository>()
	private val strRes: StrRes by inject()

	private val _state = MutableValue(BackupManager.State(isLoading = true))
	override val state: Value<BackupManager.State> = _state
	override fun getStateSerializer() = BackupManager.State.serializer()

	init {
		loadProjects()
	}

	private fun loadProjects() {
		scope.launch {
			_state.update { it.copy(isLoading = true, error = null) }

			try {
				val projects = withContext(dispatcherIo) {
					projectsRepository.getProjects()
				}

				// Get projects that have backups
				val projectsWithBackups = withContext(dispatcherIo) {
					projects.filter { projectDef ->
						backupRepository.getBackups(projectDef).isNotEmpty()
					}
				}

				val projectNames = projectsWithBackups
					.map { it.name }
					.sorted()

				withContext(dispatcherMain) {
					val firstProject = projectsWithBackups.firstOrNull()
					_state.update {
						it.copy(
							availableProjects = projectNames,
							selectedProject = firstProject?.name,
							selectedProjectDef = firstProject,
							isLoading = false
						)
					}

					// Load backups for the first project if available
					firstProject?.let { projectDef ->
						loadBackupsForProject(projectDef)
					}
				}
			} catch (e: Exception) {
				val errorMsg = Msg(Res.string.backup_manager_error_load_backups, e.message ?: "")
				val errorText = errorMsg.text(strRes)
				withContext(dispatcherMain) {
					_state.update {
						it.copy(
							isLoading = false,
							error = errorText
						)
					}
				}
			}
		}
	}

	private fun loadBackupsForProject(projectDef: ProjectDef) {
		scope.launch {
			try {
				val backups = withContext(dispatcherIo) {
					backupRepository.getBackups(projectDef)
						.sortedByDescending { it.date }
				}

				withContext(dispatcherMain) {
					_state.update {
						it.copy(backupsForSelectedProject = backups)
					}
				}
			} catch (e: Exception) {
				val errorMsg = Msg(Res.string.backup_manager_error_load_project_backups, e.message ?: "")
				val errorText = errorMsg.text(strRes)
				withContext(dispatcherMain) {
					_state.update {
						it.copy(error = errorText)
					}
				}
			}
		}
	}

	override fun selectProject(projectName: String) {
		scope.launch {
			val projectDef = withContext(dispatcherIo) {
				projectsRepository.getProjects().find { it.name == projectName }
			}

			withContext(dispatcherMain) {
				_state.update {
					it.copy(
						selectedProject = projectName,
						selectedProjectDef = projectDef,
						backupsForSelectedProject = emptyList()
					)
				}

				projectDef?.let {
					loadBackupsForProject(it)
				}
			}
		}
	}

	override fun deleteBackup(backup: ProjectBackupDef) {
		scope.launch {
			try {
				withContext(dispatcherIo) {
					backupRepository.deleteBackup(backup)
				}

				// Reload backups for the current project
				state.value.selectedProjectDef?.let { projectDef ->
					loadBackupsForProject(projectDef)
				}
			} catch (e: Exception) {
				val errorMsg = Msg(Res.string.backup_manager_error_delete_backup, e.message ?: "")
				val errorText = errorMsg.text(strRes)
				withContext(dispatcherMain) {
					_state.update {
						it.copy(error = errorText)
					}
				}
			}
		}
	}

	override fun restoreBackup(backup: ProjectBackupDef) {
		scope.launch {
			backupRepository.restoreBackup(backup, backup.projectDef.path)
		}
	}
}