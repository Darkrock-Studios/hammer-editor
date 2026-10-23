package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogW
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_account_project_in_use
import com.darkrockstudios.apps.hammer.sync_log_account_project_move_client_from_server_failure
import com.darkrockstudios.apps.hammer.sync_log_account_project_move_client_from_server_success
import com.darkrockstudios.apps.hammer.sync_log_account_project_move_client_group_missing

/** Puts projects in their server group on this device during account sync, logging each outcome. */
internal class LocalProjectPlacer(
	private val projectsRepository: ProjectsRepository,
	private val strRes: StrRes,
) {
	/**
	 * Moves [projectDef], now in [currentGroup], into [target]: the server group [serverGroupId] or
	 * the nearest ancestor of it this device has, null being the top level. True only when the
	 * project ends up in [serverGroupId] itself.
	 */
	suspend fun place(
		projectDef: ProjectDef,
		currentGroup: GroupId?,
		target: GroupDef?,
		serverGroupId: GroupId?,
		onLog: OnSyncLog,
	): Boolean {
		val moved = target?.id == currentGroup || move(projectDef, target, onLog)
		if (target?.id != serverGroupId) {
			onLog(syncAccLogW(strRes.get(Res.string.sync_log_account_project_move_client_group_missing, projectDef.name)))
		}
		return moved && target?.id == serverGroupId
	}

	private suspend fun move(projectDef: ProjectDef, target: GroupDef?, onLog: OnSyncLog): Boolean {
		val result = projectsRepository.moveProject(projectDef, target)
		if (isSuccess(result)) {
			onLog(
				syncAccLogI(
					strRes.get(Res.string.sync_log_account_project_move_client_from_server_success, projectDef.name)
				)
			)
			return true
		}
		if (result.isInUse()) {
			logInUse(projectDef, onLog)
		} else {
			onLog(
				syncAccLogE(
					strRes.get(Res.string.sync_log_account_project_move_client_from_server_failure, projectDef.name)
				)
			)
		}
		return false
	}

	suspend fun logInUse(projectDef: ProjectDef, onLog: OnSyncLog) {
		onLog(syncAccLogW(strRes.get(Res.string.sync_log_account_project_in_use, projectDef.name)))
	}
}
