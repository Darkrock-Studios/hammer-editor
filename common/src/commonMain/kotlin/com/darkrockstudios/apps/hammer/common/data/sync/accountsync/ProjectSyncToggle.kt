package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectInUseException
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

/**
 * Turns sync on or off for one of the account's own projects on this device, from its menu on the
 * project list. A shared project has no switch: it syncs while shared and not left.
 */
class ProjectSyncToggle(
	private val projectsRepository: ProjectsRepository,
	private val accountSyncDatasource: AccountSyncDatasource,
) {
	enum class Result {
		Done,

		/**
		 * Nothing changed: `project.toml` could not be read, the project is shared with this account,
		 * or it is open and needs a fresh id.
		 */
		Failed,
	}

	fun turnOff(projectDef: ProjectDef): Result =
		if (projectsRepository.getOwner(projectDef) == null && projectsRepository.setSyncEnabled(projectDef, false)) {
			Result.Done
		} else {
			Result.Failed
		}

	/**
	 * An id the account file lists as deleted is re-minted first, so the create path uploads the
	 * project as new instead of being refused with `project_deleted`. Otherwise only the flag
	 * changes and the next sync resumes from the journal.
	 */
	suspend fun turnOn(projectDef: ProjectDef): Result {
		if (projectsRepository.getOwner(projectDef) != null) return Result.Failed
		if (projectDef.id in accountSyncDatasource.load().deletedProjects) {
			try {
				projectsRepository.setProjectId(projectDef, ProjectId.randomUUID())
			} catch (_: ProjectInUseException) {
				return Result.Failed
			}
		}
		return if (projectsRepository.setSyncEnabled(projectDef, true)) Result.Done else Result.Failed
	}
}
