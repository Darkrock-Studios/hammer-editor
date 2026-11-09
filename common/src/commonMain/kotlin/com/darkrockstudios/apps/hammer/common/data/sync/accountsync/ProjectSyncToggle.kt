package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectInUseException
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

/** Turns sync on or off for one project on this device, the switch in its settings. */
class ProjectSyncToggle(
	private val projectsRepository: ProjectsRepository,
	private val accountSyncDatasource: AccountSyncDatasource,
) {
	enum class Result {
		Done,

		/** `project.toml` could not be read; nothing changed. */
		Failed,

		/**
		 * The server deleted the project while it was excluded and it is open, so it cannot take the
		 * fresh id it needs. Account sync re-mints it once it is closed; sync stays off until then.
		 */
		DeletedOnServer,

		/** A parked shared project uploads as the user's own only once they confirm it. */
		NeedsConfirmation,

		/**
		 * Confirmed and now the user's own, but open, so it cannot take the fresh id it needs. Its
		 * old id is recorded as this device's to retire: account sync re-mints it once it is closed,
		 * and sync stays off until it is turned on again.
		 */
		OwnNeedsClose,
	}

	fun turnOff(projectDef: ProjectDef): Result =
		if (projectsRepository.setSyncEnabled(projectDef, false)) Result.Done else Result.Failed

	/**
	 * An id the account file lists as deleted is re-minted first, so the create path uploads the
	 * project as new instead of being refused with `project_deleted`. Otherwise only the flag
	 * changes and the next sync resumes from the journal.
	 *
	 * A parked shared project is no longer the account's to sync: with [uploadAsOwn] it is detached
	 * from its owner under a fresh id (the old one is the owner's), so the create path uploads it as
	 * a new project. A shared project turned off by hand only resumes.
	 */
	suspend fun turnOn(projectDef: ProjectDef, uploadAsOwn: Boolean = false): Result {
		if (projectsRepository.getOwner(projectDef) != null) {
			return when {
				!projectsRepository.isParked(projectDef) -> setEnabled(projectDef)
				uploadAsOwn -> detach(projectDef)
				else -> Result.NeedsConfirmation
			}
		}
		if (projectDef.id in accountSyncDatasource.load().deletedProjects) {
			try {
				projectsRepository.setProjectId(projectDef, ProjectId.randomUUID())
			} catch (_: ProjectInUseException) {
				return Result.DeletedOnServer
			}
		}
		return setEnabled(projectDef)
	}

	private fun setEnabled(projectDef: ProjectDef): Result =
		if (projectsRepository.setSyncEnabled(projectDef, true)) Result.Done else Result.Failed

	private suspend fun detach(projectDef: ProjectDef): Result {
		val reminted = try {
			projectsRepository.setProjectId(projectDef, ProjectId.randomUUID())
		} catch (_: ProjectInUseException) {
			// Recorded before the owner is cleared, so the project is never left ownerless and unrecorded.
			accountSyncDatasource.update { it.copy(deletedProjects = it.deletedProjects + projectDef.id) }
			return if (projectsRepository.detachFromOwner(projectDef, syncEnabled = false)) {
				Result.OwnNeedsClose
			} else {
				Result.Failed
			}
		}
		return if (projectsRepository.detachFromOwner(reminted, syncEnabled = true)) Result.Done else Result.Failed
	}
}
