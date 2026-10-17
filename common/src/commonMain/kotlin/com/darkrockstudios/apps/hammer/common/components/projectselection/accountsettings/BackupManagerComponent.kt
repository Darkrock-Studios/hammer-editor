package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.backup_manager_error_delete_backup
import com.darkrockstudios.apps.hammer.backup_manager_error_load_backups
import com.darkrockstudios.apps.hammer.backup_manager_error_load_project_backups
import com.darkrockstudios.apps.hammer.backup_manager_error_restore_backup
import com.darkrockstudios.apps.hammer.backup_manager_error_restore_in_use
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.data.ClientMessage
import com.darkrockstudios.apps.hammer.common.data.projectbackup.BackupManagerService
import com.darkrockstudios.apps.hammer.common.data.projectbackup.BackupRestoreResult
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BackupManagerComponent(
	componentContext: ComponentContext,
) : BackupManager, KoinComponent, SavableComponent<BackupManager.State>(componentContext) {

	private val backupService by inject<ProjectBackupService>()
	private val strRes: StrRes by inject()
	private val backupManagerService by inject<BackupManagerService>()

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
				val projectsWithBackups = withContext(dispatcherIo) {
					backupService.getBackedUpProjects()
				}

				withContext(dispatcherMain) {
					val firstProject = projectsWithBackups.firstOrNull()
					_state.update {
						it.copy(
							availableProjects = projectsWithBackups.sorted(),
							selectedProject = firstProject,
							isLoading = false
						)
					}

					// Load backups for the first project if available
					firstProject?.let { projectName ->
						loadBackupsForProject(projectName)
					}
				}
				// Loading backups can fail many ways (IO, parse); report and surface error to UI.
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.e("Failed to load backups", e)
				val errorMsg = ClientMessage.Resource(Res.string.backup_manager_error_load_backups, e.message ?: "")
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

	private fun loadBackupsForProject(projectName: String) {
		scope.launch {
			try {
				val backups = withContext(dispatcherIo) {
					backupService.getBackups(projectName)
						.sortedByDescending { it.date }
				}

				withContext(dispatcherMain) {
					_state.update {
						it.copy(backupsForSelectedProject = backups)
					}
				}
			} catch (e: Exception) {
				val errorMsg =
					ClientMessage.Resource(Res.string.backup_manager_error_load_project_backups, e.message ?: "")
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
			withContext(dispatcherMain) {
				_state.update {
					it.copy(
						selectedProject = projectName,
						backupsForSelectedProject = emptyList()
					)
				}

				loadBackupsForProject(projectName)
			}
		}
	}

	override fun deleteBackup(backup: ProjectBackupDef) {
		scope.launch {
			try {
				withContext(dispatcherIo) {
					backupService.deleteBackup(backup)
				}

				// Reload backups for the current project
				state.value.selectedProject?.let { projectName ->
					loadBackupsForProject(projectName)
				}
			} catch (e: Exception) {
				val errorMsg = ClientMessage.Resource(Res.string.backup_manager_error_delete_backup, e.message ?: "")
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
			val result = withContext(dispatcherIo) {
				backupService.restoreBackup(backup)
			}

			val errorMsg = when (result) {
				BackupRestoreResult.Restored -> null
				BackupRestoreResult.ProjectInUse ->
					ClientMessage.Resource(Res.string.backup_manager_error_restore_in_use, backup.projectName)

				BackupRestoreResult.Failed ->
					ClientMessage.Resource(Res.string.backup_manager_error_restore_backup, backup.path.name)
			}
			if (errorMsg != null) {
				val errorText = errorMsg.text(strRes)
				withContext(dispatcherMain) {
					_state.update {
						it.copy(error = errorText)
					}
				}
			}
		}
	}

	override fun exportBackup(backup: ProjectBackupDef) {
		scope.launch {
			withContext(dispatcherIo) {
				backupManagerService.exportBackup(backup)
			}
		}
	}
}