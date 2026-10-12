package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer

/** Creates, renames, and deletes projects, queuing each change for the next account sync. */
class ProjectsService(
	private val projectsRepository: ProjectsRepository,
	private val accountSynchronizer: ClientAccountSynchronizer,
) {

	fun createProject(name: String): CResult<ProjectDef> =
		projectsRepository.createProject(name, seedDefaultLanguage = true)

	fun renameProject(projectDef: ProjectDef, newName: String): CResult<ProjectDef> {
		val result = projectsRepository.renameProject(projectDef, newName)
		if (isSuccess(result)) {
			accountSynchronizer.renameProject(projectDef.id, newName)
		}
		return result
	}

	fun deleteProject(projectDef: ProjectDef): Boolean {
		val deleted = projectsRepository.deleteProject(projectDef)
		if (deleted) {
			// Queued even when it never synced: account sync drops ids the server never knew.
			accountSynchronizer.deleteProject(projectDef)
		}
		return deleted
	}
}
