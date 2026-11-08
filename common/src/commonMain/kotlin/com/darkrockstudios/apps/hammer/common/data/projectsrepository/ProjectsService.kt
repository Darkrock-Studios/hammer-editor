package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer

/** Creates, renames, and deletes projects, queuing deletions for the next account sync. */
class ProjectsService(
	private val projectsRepository: ProjectsRepository,
	private val accountSynchronizer: ClientAccountSynchronizer,
) {

	fun createProject(name: String, group: GroupDef? = null): CResult<ProjectDef> =
		projectsRepository.createProject(name, seedDefaultLanguage = true, group = group)

	fun renameProject(projectDef: ProjectDef, newName: String): CResult<ProjectDef> =
		projectsRepository.renameProject(projectDef, newName)

	/** A project with sync off on this device is deleted here only; the server keeps its copy. */
	suspend fun deleteProject(projectDef: ProjectDef): Boolean {
		val syncEnabled = projectsRepository.isSyncEnabled(projectDef)
		val deleted = projectsRepository.deleteProject(projectDef)
		if (deleted && syncEnabled) {
			// Queued even when it never synced: account sync drops ids the server never knew.
			accountSynchronizer.deleteProject(projectDef)
		}
		return deleted
	}
}
