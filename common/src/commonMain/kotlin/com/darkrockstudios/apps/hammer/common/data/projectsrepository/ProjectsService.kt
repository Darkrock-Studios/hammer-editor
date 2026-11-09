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
		refuseShared(projectDef) ?: projectsRepository.renameProject(projectDef, newName)

	fun moveProject(projectDef: ProjectDef, target: GroupDef?): CResult<ProjectDef> =
		refuseShared(projectDef) ?: projectsRepository.moveProject(projectDef, target)

	private fun refuseShared(projectDef: ProjectDef): CResult<ProjectDef>? =
		if (ownerNamesAndPlaces(projectDef)) {
			CResult.failure("'${projectDef.name}' is shared with this account")
		} else {
			null
		}

	/**
	 * A shared project that syncs takes its name and place from its owner; account sync would undo a
	 * local change. Parked, or with no server, it is this device's copy to arrange.
	 */
	fun ownerNamesAndPlaces(projectDef: ProjectDef): Boolean =
		projectsRepository.getOwner(projectDef) != null &&
			projectsRepository.isSyncEnabled(projectDef) &&
			accountSynchronizer.isServerSynchronized()

	/**
	 * A project with sync off on this device is deleted here only, so the server keeps its copy,
	 * unless [alsoDeleteOnServer]. A shared project not yet left is refused: see [leaveProject]. A
	 * parked one is deleted here only: this account is no longer a member to send the delete for.
	 */
	suspend fun deleteProject(projectDef: ProjectDef, alsoDeleteOnServer: Boolean = false): Boolean {
		if (offersLeave(projectDef)) return false
		val deleteOnServer = projectsRepository.isSyncEnabled(projectDef) ||
			(alsoDeleteOnServer && projectsRepository.getOwner(projectDef) == null)
		val deleted = projectsRepository.deleteProject(projectDef)
		if (deleted && deleteOnServer) {
			// Queued even when it never synced: account sync drops ids the server never knew.
			accountSynchronizer.deleteProject(projectDef)
		}
		return deleted
	}

	/** True for a project shared with this account that still syncs: not left, and still shared. */
	fun offersLeave(projectDef: ProjectDef): Boolean = leaveFrom(projectDef) != null

	/** The owner to name when leaving [projectDef], or null when it is not a project to leave. */
	fun leaveFrom(projectDef: ProjectDef): ProjectOwner? =
		projectsRepository.getOwner(projectDef)?.takeIf { projectsRepository.isSyncEnabled(projectDef) }

	/**
	 * Leaves a shared project: the delete it queues is a leave on the server, which keeps the owner's
	 * copy. The folder stays with sync off, as the owner stopping sharing it would leave it; deleting
	 * it afterwards is an ordinary local delete.
	 */
	suspend fun leaveProject(projectDef: ProjectDef): Boolean {
		if (!projectsRepository.setSyncEnabled(projectDef, false)) return false
		accountSynchronizer.deleteProject(projectDef)
		return true
	}

	/** True for a project that syncs with a server, so deleting it here deletes it there. */
	fun deletesOnServer(projectDef: ProjectDef): Boolean =
		accountSynchronizer.isServerSynchronized() && projectsRepository.isSyncEnabled(projectDef)

	/** True for a project with sync off here that the server holds a copy of. */
	suspend fun offersServerDelete(projectDef: ProjectDef): Boolean =
		accountSynchronizer.isServerSynchronized() &&
			!projectsRepository.isSyncEnabled(projectDef) &&
			accountSynchronizer.hasProjectBaseline(projectDef)
}
