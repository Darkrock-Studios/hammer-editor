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

	/**
	 * A project with sync off on this device is deleted here only, so the server keeps its copy,
	 * unless [alsoDeleteOnServer].
	 */
	suspend fun deleteProject(projectDef: ProjectDef, alsoDeleteOnServer: Boolean = false): Boolean {
		val deleteOnServer = alsoDeleteOnServer || projectsRepository.isSyncEnabled(projectDef)
		val deleted = projectsRepository.deleteProject(projectDef)
		if (deleted && deleteOnServer) {
			// Queued even when it never synced: account sync drops ids the server never knew.
			accountSynchronizer.deleteProject(projectDef)
		}
		return deleted
	}

	/** True for a project with sync off here that the server holds a copy of. */
	suspend fun offersServerDelete(projectDef: ProjectDef): Boolean =
		accountSynchronizer.isServerSynchronized() &&
			!projectsRepository.isSyncEnabled(projectDef) &&
			accountSynchronizer.hasProjectBaseline(projectDef)
}
