package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.util.StrRes

class BackupOperation(
	projectDef: ProjectDef,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val backupRepository: ProjectBackupRepository,
	private val strRes: StrRes,
) : SyncOperation(projectDef) {
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {
		if (
			globalSettingsRepository.globalSettings.automaticBackups &&
			backupRepository.supportsBackup()
		) {
			val backupDef = backupRepository.createBackup(projectDef)

			if (backupDef != null) {
				onProgress(
					0.25f,
					syncLogI(
						strRes.get(MR.strings.sync_log_backup_made, backupDef.path.name),
						projectDef
					)
				)
			} else {
				throw IllegalStateException("Failed to make local backup")
			}
		}

		return CResult.success(state)
	}
}