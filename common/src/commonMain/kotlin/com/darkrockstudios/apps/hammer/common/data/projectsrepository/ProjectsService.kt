package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer

/** Creates, renames, and deletes projects, queuing each change for the next account sync. */
class ProjectsService(
	private val projectsRepository: ProjectsRepository,
	private val accountSynchronizer: ClientAccountSynchronizer,
) {

	fun createProject(name: String): CResult<ProjectDef> {
		val result = projectsRepository.createProject(name, seedDefaultLanguage = true)
		if (isSuccess(result) && accountSynchronizer.isServerSynchronized()) {
			accountSynchronizer.createProject(name.trim())
		}
		return result
	}

	fun renameProject(projectDef: ProjectDef, newName: String): CResult<ProjectDef> {
		val projectId = projectsRepository.getProjectId(projectDef)
		val result = projectsRepository.renameProject(projectDef, newName)
		if (isSuccess(result)) {
			if (projectId != null) {
				accountSynchronizer.renameProject(projectId, newName)
			} else if (accountSynchronizer.isServerSynchronized()) {
				// Never synced: the queued creation is by name.
				accountSynchronizer.deleteUnsyncedProject(projectDef.name)
				accountSynchronizer.createProject(newName)
			}
		}
		return result
	}

	fun deleteProject(projectDef: ProjectDef): Boolean {
		val projectId = projectsRepository.getProjectId(projectDef)
		val deleted = projectsRepository.deleteProject(projectDef)
		if (deleted) {
			if (projectId != null) {
				accountSynchronizer.deleteProject(SyncedProjectDefinition(projectDef, projectId))
			} else if (accountSynchronizer.isServerSynchronized()) {
				accountSynchronizer.deleteUnsyncedProject(projectDef.name)
			}
		}
		return deleted
	}
}
