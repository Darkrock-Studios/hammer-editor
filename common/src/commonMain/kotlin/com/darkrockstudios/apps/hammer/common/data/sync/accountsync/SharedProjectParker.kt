package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

/**
 * Parks a shared project the server no longer lets this account sync: sync off, the account's
 * baseline for it dropped, the folder and the project journal left as they are. Clearing the journal
 * would let a partial copy delete the owner's entities on a re-invite.
 */
class SharedProjectParker(
	private val projectsRepository: ProjectsRepository,
	private val accountSyncDatasource: AccountSyncDatasource,
) {
	/** True when [projectDef] is shared and was not parked yet, so the caller logs it once. */
	suspend fun park(projectDef: ProjectDef): Boolean {
		if (projectsRepository.getOwner(projectDef) == null || projectsRepository.isParked(projectDef)) return false
		if (!projectsRepository.park(projectDef)) return false
		accountSyncDatasource.update { it.copy(projectBaselines = it.projectBaselines - projectDef.id) }
		return true
	}
}
